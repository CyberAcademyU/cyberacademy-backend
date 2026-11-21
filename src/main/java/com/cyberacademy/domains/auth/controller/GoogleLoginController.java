package com.cyberacademy.domains.auth.controller;

import com.cyberacademy.common.ApiResponse;
import com.cyberacademy.domains.auth.dto.AuthResponse;
import com.cyberacademy.domains.auth.service.GoogleIdentityService;
import com.cyberacademy.domains.auth.service.RateLimitService;
import com.cyberacademy.security.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/auth/google")
public class GoogleLoginController {
    public record Credential(@NotBlank @Size(max = 8192) String credential,
                             @Size(min = 2, max = 100) String firstName,
                             @Size(min = 2, max = 100) String lastName) {}
    public record Challenge(String clientId, String nonce) {}
    private final GoogleIdentityService google;
    private final SessionCookies cookies;
    private final SessionService sessions;
    private final CurrentUserProvider current;
    private final RateLimitService limits;
    public GoogleLoginController(GoogleIdentityService google, SessionCookies cookies, SessionService sessions,
                                 CurrentUserProvider current, RateLimitService limits) {
        this.google = google; this.cookies = cookies; this.sessions = sessions; this.current = current; this.limits = limits;
    }
    @PostMapping("/challenge")
    public Challenge challenge(HttpServletResponse response) {
        String nonce = UUID.randomUUID().toString();
        cookies.googleNonce(response, nonce);
        return new Challenge(google.clientId(), nonce);
    }
    @PostMapping({"", "/link"})
    public ApiResponse<AuthResponse> login(@Valid @RequestBody Credential body, HttpServletRequest req, HttpServletResponse res) {
        limits.check("google:" + SessionService.hash(body.credential()));
        var token = google.verify(body.credential(), SessionCookies.read(req, "ca_google_nonce"));
        boolean link = req.getRequestURI().endsWith("/link");
        var user = google.resolve(token, link ? current.requireCurrentUser() : null, body.firstName(), body.lastName());
        if (!link) cookies.write(res, sessions.create(user.getId()));
        cookies.googleNonce(res, "");
        return ApiResponse.ok(link ? "Cuenta vinculada" : "Sesión iniciada", new AuthResponse(new AuthResponse.UserSummary(
                user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getRole().name())));
    }
}
