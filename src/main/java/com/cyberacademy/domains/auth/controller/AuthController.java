package com.cyberacademy.domains.auth.controller;

import com.cyberacademy.common.ApiResponse;

import com.cyberacademy.domains.auth.dto.*;
import com.cyberacademy.domains.auth.service.AuthService;
import com.cyberacademy.domains.auth.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.cyberacademy.security.SessionService;
import com.cyberacademy.security.SessionCookies;
import com.cyberacademy.security.CurrentUserProvider;
import com.cyberacademy.security.PasswordTransportService;
import com.cyberacademy.domains.auth.service.RateLimitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final SessionService sessions;
    private final SessionCookies cookies;
    private final CurrentUserProvider currentUser;
    private final RateLimitService rateLimit;
    private final PasswordTransportService passwordTransport;

    public AuthController(AuthService authService, PasswordResetService passwordResetService,
                          SessionService sessions, SessionCookies cookies, CurrentUserProvider currentUser,
                          RateLimitService rateLimit, PasswordTransportService passwordTransport) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.sessions = sessions;
        this.cookies = cookies;
        this.currentUser = currentUser;
        this.rateLimit = rateLimit;
        this.passwordTransport = passwordTransport;
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.request(request.email());
        return ResponseEntity.ok(ApiResponse.ok("Si la cuenta existe, recibirás un código de recuperación.", null));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        if (!passwordResetService.confirm(request.email(), request.code(), request.newPassword(), request.sealedNewPassword())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Código inválido o vencido. Solicita uno nuevo."));
        }
        return ResponseEntity.ok(ApiResponse.ok("Contraseña actualizada correctamente.", null));
    }

    /**
     * Clave pública con la que el navegador cifra las contraseñas. Es pública y
     * determinista: no cambia entre reinicios ni instancias.
     */
    @GetMapping("/encryption-key")
    public ResponseEntity<ApiResponse<PasswordTransportService.PublicInfo>> encryptionKey() {
        return ResponseEntity.ok(ApiResponse.ok("Clave de transporte disponible", passwordTransport.publicInfo()));
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
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                          HttpServletResponse httpResponse) {
        ApiResponse<AuthResponse> response = authService.login(request);
        if (!response.isSuccess()) {
            if (response.getMessage().contains("verificar tu correo")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        cookies.write(httpResponse, sessions.create(response.getData().getUser().getId()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> me() {
        var user = currentUser.requireCurrentUser();
        var summary = new AuthResponse.UserSummary(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getRole().name());
        return ResponseEntity.ok(ApiResponse.ok("Sesión activa", new AuthResponse(summary)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(HttpServletRequest request, HttpServletResponse response) {
        String token = SessionCookies.read(request, SessionCookies.REFRESH);
        rateLimit.check("refresh:" + (token == null ? "missing" : SessionService.hash(token)));
        var tokens = sessions.rotate(token);
        if (tokens == null) {
            cookies.clear(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Sesión no válida"));
        }
        cookies.write(response, tokens);
        return ResponseEntity.ok(ApiResponse.ok("Sesión renovada", null));
    }

    // La ruta anidada recibe el refresh incluso si el access ya expiró.
    @PostMapping({"/logout", "/refresh/logout"})
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.revoke(SessionCookies.read(request, SessionCookies.ACCESS));
        sessions.revoke(SessionCookies.read(request, SessionCookies.REFRESH));
        cookies.clear(response);
        return ResponseEntity.ok(ApiResponse.ok("Sesión cerrada correctamente", null));
    }
}
