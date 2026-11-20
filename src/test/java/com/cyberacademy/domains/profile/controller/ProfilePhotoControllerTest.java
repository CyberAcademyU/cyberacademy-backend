package com.cyberacademy.domains.profile.controller;

import com.cyberacademy.domains.profile.service.ProfilePhotoService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProfilePhotoControllerTest {
    private final ProfilePhotoService service = mock(ProfilePhotoService.class);

    @Test void sendsWebpWithCorrectMimeAndPrivacyHeaders() throws Exception {
        UUID version = UUID.randomUUID();
        byte[] image = "RIFF0000WEBPtest".getBytes(StandardCharsets.US_ASCII);
        when(service.read(version)).thenReturn(image);
        MockMvcBuilders.standaloneSetup(new ProfilePhotoController(service)).build()
                .perform(get("/profile/photos/" + version))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/webp"))
                .andExpect(content().bytes(image))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test void legacyJpegKeepsCorrectMimeWhileWaitingForConversion() throws Exception {
        UUID version = UUID.randomUUID();
        byte[] image = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
        when(service.read(version)).thenReturn(image);
        MockMvcBuilders.standaloneSetup(new ProfilePhotoController(service)).build()
                .perform(get("/profile/photos/" + version))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(image));
    }
}
