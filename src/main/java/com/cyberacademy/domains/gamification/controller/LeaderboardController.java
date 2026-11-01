package com.cyberacademy.domains.gamification.controller;

import com.cyberacademy.common.ApiResponse;
import com.cyberacademy.exception.BadRequestException;
import com.cyberacademy.domains.gamification.dto.LeaderboardResponse;
import com.cyberacademy.domains.gamification.service.GamificationService;
import com.cyberacademy.security.CurrentUserProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/leaderboard")
public class LeaderboardController {

    private final GamificationService gamificationService;
    private final CurrentUserProvider currentUserProvider;

    public LeaderboardController(GamificationService gamificationService, CurrentUserProvider currentUserProvider) {
        this.gamificationService = gamificationService;
        this.currentUserProvider = currentUserProvider;
    }

    /** Clasificación por XP de la semana (desde el lunes) o de siempre. */
    @GetMapping
    public ResponseEntity<ApiResponse<LeaderboardResponse>> leaderboard(
            @RequestParam(defaultValue = "week") String period) {
        GamificationService.Period parsed;
        try {
            parsed = GamificationService.Period.valueOf(period.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("El periodo debe ser 'week' o 'all'");
        }
        return ResponseEntity.ok(ApiResponse.ok(
                "Clasificación obtenida correctamente",
                gamificationService.leaderboard(currentUserProvider.requireCurrentUser(), parsed)));
    }
}
