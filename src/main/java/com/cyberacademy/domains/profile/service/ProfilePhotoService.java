package com.cyberacademy.domains.profile.service;

import com.cyberacademy.domains.auth.model.*;
import com.cyberacademy.domains.auth.repository.UserRepository;
import com.cyberacademy.domains.auth.service.RateLimitService;
import com.cyberacademy.security.CurrentUserProvider;
import com.cyberacademy.domains.profile.dto.ProfileResponse;
import dev.matrixlab.webp4j.WebPCodec;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.Semaphore;

@Service
public class ProfilePhotoService {
    private final ProfilePhotoRepository photos;
    private final UserRepository users;
    private final CurrentUserProvider current;
    private final ProfileService profiles;
    private final RateLimitService limits;
    private final Semaphore capacity = new Semaphore(2);

    public ProfilePhotoService(ProfilePhotoRepository photos, UserRepository users,
            CurrentUserProvider current, ProfileService profiles, RateLimitService limits) {
        this.photos = photos; this.users = users; this.current = current; this.profiles = profiles; this.limits = limits;
    }

    @PostConstruct
    void verifyWebpSupport() {
        if (!WebPCodec.isAvailable())
            throw new IllegalStateException("El conversor WebP no está disponible en esta plataforma.");
    }

    @Transactional
    public ProfileResponse upload(MultipartFile file) {
        User user = current.requireCurrentUser();
        limits.check("profile-picture:" + user.getId());
        if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024) throw invalid();
        if (!capacity.tryAcquire())
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Intenta subir la foto en unos segundos.");
        try {
            photos.save(user.getId(), normalize(file.getBytes()));
        } catch (IOException | IllegalArgumentException e) { throw invalid(); }
        finally { capacity.release(); }
        user.setAvatar(null);
        users.saveAndFlush(user);
        return profiles.getMyProfile();
    }

    @Transactional
    public ProfileResponse remove() {
        User user = current.requireCurrentUser();
        limits.check("profile-picture:" + user.getId());
        photos.delete(user.getId());
        return profiles.getMyProfile();
    }

    @Transactional
    public ProfileResponse chooseAvatar(String avatar) {
        User user = current.requireCurrentUser();
        if (user.getRole() != UserRole.STUDENT) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (avatar == null || !avatar.matches("op-0[1-8]")) throw invalid();
        limits.check("profile-picture:" + user.getId());
        photos.delete(user.getId());
        user.setAvatar(avatar);
        users.saveAndFlush(user);
        return profiles.getMyProfile();
    }

    @Transactional
    public byte[] read(UUID version) {
        User viewer = current.requireCurrentUser();
        var photo = photos.find(version).orElseThrow(ProfilePhotoService::notFound);
        User owner = users.findById(photo.userId()).orElseThrow(ProfilePhotoService::notFound);
        boolean self = viewer.getId().equals(owner.getId());
        boolean admin = viewer.getRole() == UserRole.ADMIN && owner.getRole() == UserRole.STUDENT;
        boolean leaderboard = viewer.getRole() == UserRole.STUDENT && owner.getRole() == UserRole.STUDENT
                && owner.isShowInLeaderboard() && owner.getStatus() == UserStatus.ACTIVE && owner.isEmailVerified();
        if (!self && !admin && !leaderboard) throw notFound();
        byte[] image = photo.image();
        // Convertir miniaturas antiguas una sola vez, después de validar permisos.
        // Si el conversor está ocupado, servir el JPEG válido y reintentar después.
        if (!isWebp(image) && capacity.tryAcquire()) {
            try {
                byte[] converted = normalize(image);
                photos.convertLegacy(version, converted);
                return converted;
            } catch (IOException | IllegalArgumentException | ResponseStatusException e) {
                // Las fotos existentes siguen disponibles aunque falle una conversión.
                return image;
            } finally { capacity.release(); }
        }
        return image;
    }

    /** Bound pixels before decoding; re-encode PNG/JPEG to WebP without metadata. */
    static byte[] normalize(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > 2 * 1024 * 1024) throw invalid();
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid();
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg").contains(format)) throw invalid();
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 6000 || height > 6000 || (long) width * height > 16000000) throw invalid();
                var source = reader.read(0);
                int edge = Math.min(width, height), x = (width - edge) / 2, y = (height - edge) / 2;
                var target = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
                var graphics = target.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, 256, 256);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(source, 0, 0, 256, 256, x, y, x + edge, y + edge, null);
                } finally { graphics.dispose(); source.flush(); }
                try {
                    // Calidad visual alta; no lanzar hilos nativos adicionales por foto.
                    byte[] encoded = WebPCodec.encodeImage(target, 82.0f, false, false);
                    if (encoded.length > 262144) throw invalid();
                    return encoded;
                } finally { target.flush(); }
            } finally { reader.dispose(); }
        }
    }

    public static boolean isWebp(byte[] image) {
        return image.length >= 12 && image[0] == 'R' && image[1] == 'I'
                && image[2] == 'F' && image[3] == 'F' && image[8] == 'W'
                && image[9] == 'E' && image[10] == 'B' && image[11] == 'P';
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usa una imagen JPG o PNG de hasta 2 MB y 16 megapíxeles.");
    }
    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto no disponible.");
    }
}
