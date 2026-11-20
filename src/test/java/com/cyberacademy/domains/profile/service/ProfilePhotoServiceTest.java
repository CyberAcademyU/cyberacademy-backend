package com.cyberacademy.domains.profile.service;

import com.cyberacademy.domains.auth.model.*;
import com.cyberacademy.domains.auth.repository.UserRepository;
import com.cyberacademy.domains.auth.service.RateLimitService;
import com.cyberacademy.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import dev.matrixlab.webp4j.WebPCodec;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfilePhotoServiceTest {
    private final ProfilePhotoRepository photos = mock(ProfilePhotoRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CurrentUserProvider current = mock(CurrentUserProvider.class);
    private final ProfileService profiles = mock(ProfileService.class);
    private final ProfilePhotoService service = new ProfilePhotoService(photos, users, current, profiles, mock(RateLimitService.class));

    private User user(UserRole role, boolean visible) {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(role);
        user.setShowInLeaderboard(visible);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }

    private byte[] image(String format, int width, int height) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    @Test void convertsPngToSmallWebp() throws Exception {
        byte[] sanitized = ProfilePhotoService.normalize(image("png", 600, 400));
        var result = WebPCodec.decodeImage(sanitized);
        assertEquals(256, result.getWidth());
        assertEquals(256, result.getHeight());
        assertTrue(ProfilePhotoService.isWebp(sanitized));
        assertTrue(sanitized.length <= 262144);
    }

    @Test void convertsLegacyJpegOnceAndKeepsItsVersion() throws Exception {
        var owner = user(UserRole.STUDENT, true);
        UUID version = UUID.randomUUID();
        byte[] jpeg = image("jpeg", 256, 256);
        when(current.requireCurrentUser()).thenReturn(owner);
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(photos.find(version)).thenReturn(Optional.of(new ProfilePhotoRepository.Photo(owner.getId(), jpeg)));
        byte[] converted = service.read(version);
        assertTrue(ProfilePhotoService.isWebp(converted));
        verify(photos).convertLegacy(eq(version), eq(converted));
        when(photos.find(version)).thenReturn(Optional.of(new ProfilePhotoRepository.Photo(owner.getId(), converted)));
        assertArrayEquals(converted, service.read(version));
        verify(photos, times(1)).convertLegacy(any(), any());
    }

    @Test void webpIsSmallerThanJpegForRepresentativeGradient() throws Exception {
        var source = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++)
            source.setRGB(x, y, (x << 16) | (y << 8) | ((x + y) / 2));
        var out = new ByteArrayOutputStream();
        ImageIO.write(source, "jpeg", out);
        byte[] webp = ProfilePhotoService.normalize(out.toByteArray());
        assertTrue(webp.length < out.size());
        System.out.println("Miniatura de prueba: JPEG=" + out.size() + " bytes; WebP=" + webp.length + " bytes");
    }

    @Test void rejectsSvgGifAndOversize() throws Exception {
        assertThrows(ResponseStatusException.class, () -> ProfilePhotoService.normalize("<svg/>".getBytes()));
        assertThrows(ResponseStatusException.class, () -> ProfilePhotoService.normalize(image("gif", 10, 10)));
        assertThrows(ResponseStatusException.class, () -> ProfilePhotoService.normalize(new byte[2097153]));
        // Dimensions are checked before decoding a large pixel buffer.
        assertThrows(ResponseStatusException.class, () -> ProfilePhotoService.normalize(image("png", 6001, 1)));
    }

    @Test void uploadUsesDecodedContentAndClearsAvatar() throws Exception {
        var me = user(UserRole.STUDENT, true);
        me.setAvatar("op-01");
        when(current.requireCurrentUser()).thenReturn(me);
        service.upload(new MockMultipartFile("file", "../../unsafe.svg", "image/svg+xml", image("png", 10, 10)));
        verify(photos).save(eq(me.getId()), any(byte[].class));
        assertNull(me.getAvatar());
        verify(users).saveAndFlush(me);
        verify(profiles).getMyProfile();
    }

    @Test void adminCannotSelectPresetAvatar() {
        when(current.requireCurrentUser()).thenReturn(user(UserRole.ADMIN, false));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.chooseAvatar("op-01")).getStatusCode().value());
        verifyNoInteractions(photos);
    }

    @Test void selectingAvatarDeletesPhoto() {
        var me = user(UserRole.STUDENT, true);
        when(current.requireCurrentUser()).thenReturn(me);
        service.chooseAvatar("op-02");
        verify(photos).delete(me.getId());
        assertEquals("op-02", me.getAvatar());
    }

    private UUID stored(User owner) {
        UUID version = UUID.randomUUID();
        when(photos.find(version)).thenReturn(Optional.of(new ProfilePhotoRepository.Photo(owner.getId(), new byte[]{1})));
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        return version;
    }

    @Test void studentsCannotReadHiddenOrAdminPhotos() {
        when(current.requireCurrentUser()).thenReturn(user(UserRole.STUDENT, true));
        UUID hidden = stored(user(UserRole.STUDENT, false));
        UUID admin = stored(user(UserRole.ADMIN, true));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.read(hidden)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.read(admin)).getStatusCode().value());
    }

    @Test void studentsCanReadVisibleStudentPhotos() {
        when(current.requireCurrentUser()).thenReturn(user(UserRole.STUDENT, true));
        assertArrayEquals(new byte[]{1}, service.read(stored(user(UserRole.STUDENT, true))));
    }

    @Test void adminCanReadHiddenStudentPhotoAndOwnerCanReadOwnPhoto() {
        var owner = user(UserRole.STUDENT, false);
        UUID version = stored(owner);
        when(current.requireCurrentUser()).thenReturn(user(UserRole.ADMIN, false));
        assertArrayEquals(new byte[]{1}, service.read(version));
        when(current.requireCurrentUser()).thenReturn(owner);
        assertArrayEquals(new byte[]{1}, service.read(version));
    }
}
