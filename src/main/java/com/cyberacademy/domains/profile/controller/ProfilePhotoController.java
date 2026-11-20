package com.cyberacademy.domains.profile.controller;

import com.cyberacademy.common.ApiResponse;
import com.cyberacademy.domains.profile.dto.ProfileResponse;
import com.cyberacademy.domains.profile.service.ProfilePhotoService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController
@RequestMapping("/profile")
public class ProfilePhotoController {
    private final ProfilePhotoService service;
    public ProfilePhotoController(ProfilePhotoService service) { this.service = service; }
    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProfileResponse> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok("Foto actualizada", service.upload(file));
    }
    @DeleteMapping("/photo")
    public ApiResponse<ProfileResponse> remove() {
        return ApiResponse.ok("Foto eliminada", service.remove());
    }
    public record Avatar(String avatar) {}
    @PutMapping("/avatar")
    public ApiResponse<ProfileResponse> avatar(@RequestBody Avatar body) {
        return ApiResponse.ok("Avatar actualizado", service.chooseAvatar(body.avatar()));
    }
    @GetMapping("/photos/{version}")
    public ResponseEntity<byte[]> photo(@PathVariable UUID version) {
        byte[] image = service.read(version);
        MediaType type = ProfilePhotoService.isWebp(image) ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type)
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .body(image);
    }
}
