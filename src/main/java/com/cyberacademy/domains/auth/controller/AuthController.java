package com.cyberacademy.domains.auth.controller;

import com.cyberacademy.common.ApiResponse;

import com.cyberacademy.domains.auth.dto.*;
import com.cyberacademy.domains.auth.service.AuthService;
import com.cyberacademy.domains.auth.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.request(request.email());
        return ResponseEntity.ok(ApiResponse.ok("Si la cuenta existe, recibirás un código de recuperación.", null));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        if (!passwordResetService.confirm(request.email(), request.code(), request.newPassword())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Código inválido o vencido. Solicita uno nuevo."));
        }
        return ResponseEntity.ok(ApiResponse.ok("Contraseña actualizada correctamente.", null));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegisterRequest request) {
        ApiResponse<String> response = authService.register(request);
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<String>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        ApiResponse<String> response = authService.verifyOtp(request);
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<String>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        ApiResponse<String> response = authService.resendOtp(request);
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        ApiResponse<AuthResponse> response = authService.login(request);
        if (!response.isSuccess()) {
            if (response.getMessage().contains("verificar tu correo")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    /** Cierra la sesión revocando el JWT actual en el backend. */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.revokeToken(token);
        }
        return ResponseEntity.ok(ApiResponse.ok("Sesión cerrada correctamente", null));
    }
}
