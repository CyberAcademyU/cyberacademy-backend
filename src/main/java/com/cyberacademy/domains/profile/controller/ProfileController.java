package com.cyberacademy.domains.profile.controller;

import com.cyberacademy.common.ApiResponse;
import com.cyberacademy.domains.profile.dto.ChangePasswordRequest;
import com.cyberacademy.domains.profile.dto.ProfileResponse;
import com.cyberacademy.domains.profile.dto.UpdateProfileRequest;
import com.cyberacademy.domains.profile.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /** Mi perfil con XP, liga, posición, insignias y actividad. */
    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> me() {
        return ResponseEntity.ok(ApiResponse.ok("Perfil obtenido correctamente", profileService.getMyProfile()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> update(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Perfil actualizado", profileService.updateMyProfile(request)));
    }

    /** Tras cambiarla, todas las sesiones (incluida esta) dejan de ser válidas. */
    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                            Authentication authentication) {
        profileService.changeMyPassword(request, (String) authentication.getCredentials());
        return ResponseEntity.ok(ApiResponse.ok("Contraseña actualizada. Inicia sesión de nuevo.", null));
    }
}
