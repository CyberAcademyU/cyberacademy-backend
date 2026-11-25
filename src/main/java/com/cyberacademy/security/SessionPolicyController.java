package com.cyberacademy.security;

import com.cyberacademy.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/session")
public class SessionPolicyController {
    public record Activity(@Min(0) @Max(60000) long ageMillis) {}
    private final SessionService sessions;
    public SessionPolicyController(SessionService sessions) { this.sessions = sessions; }

    @GetMapping
    public ApiResponse<SessionService.Status> status(HttpServletRequest request) {
        return response(sessions.status(SessionCookies.read(request, SessionCookies.ACCESS)));
    }

    @PostMapping("/activity")
    public ApiResponse<SessionService.Status> activity(@Valid @RequestBody Activity body, HttpServletRequest request) {
        return response(sessions.activity(SessionCookies.read(request, SessionCookies.ACCESS), body.ageMillis()));
    }

    private ApiResponse<SessionService.Status> response(SessionService.Status status) {
        if (status == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida");
        return ApiResponse.ok("Sesión activa", status);
    }
}
