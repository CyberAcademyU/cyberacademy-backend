package com.cyberacademy.domains.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
        // Null cuando la contraseña llega cifrada en `sealedNewPassword`.
        @Size(min = 8, max = 128) String newPassword,
        SealedSecret sealedNewPassword) {}
