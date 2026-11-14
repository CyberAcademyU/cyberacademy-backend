package com.cyberacademy.domains.profile.service;

import com.cyberacademy.domains.auth.model.User;
import com.cyberacademy.domains.auth.repository.UserRepository;
import com.cyberacademy.security.SessionService;
import com.cyberacademy.domains.auth.service.RateLimitService;
import com.cyberacademy.exception.BadRequestException;
import com.cyberacademy.exception.ConflictException;
import com.cyberacademy.domains.gamification.service.GamificationService;
import com.cyberacademy.domains.profile.dto.ChangePasswordRequest;
import com.cyberacademy.domains.profile.dto.ProfileResponse;
import com.cyberacademy.domains.profile.dto.UpdateProfileRequest;
import com.cyberacademy.security.CurrentUserProvider;
import com.cyberacademy.security.PasswordService;
import com.cyberacademy.security.PasswordTransportService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final GamificationService gamificationService;
    private final PasswordService passwordService;
    private final RateLimitService rateLimitService;
    private final SessionService authService;
    private final PasswordTransportService passwordTransport;

    public ProfileService(UserRepository userRepository,
                          CurrentUserProvider currentUserProvider,
                          GamificationService gamificationService,
                          PasswordService passwordService,
                          RateLimitService rateLimitService,
                          SessionService authService,
                          PasswordTransportService passwordTransport) {
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.gamificationService = gamificationService;
        this.passwordService = passwordService;
        this.rateLimitService = rateLimitService;
        this.authService = authService;
        this.passwordTransport = passwordTransport;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getMyProfile() {
        return toResponse(currentUserProvider.requireCurrentUser());
    }

    @Transactional
    public ProfileResponse updateMyProfile(UpdateProfileRequest request) {
        User user = currentUserProvider.requireCurrentUser();

        String displayName = optional(request.getDisplayName());
        if (displayName != null && userRepository.existsByDisplayNameIgnoreCaseAndIdNot(displayName, user.getId())) {
            throw new ConflictException("Ese alias ya está en uso. Prueba con otro.");
        }

        user.setFirstName(clean(request.getFirstName()));
        user.setLastName(clean(request.getLastName()));
        user.setDisplayName(displayName);
        user.setBio(optional(request.getBio()));
        user.setCountry(optional(request.getCountry()));
        user.setAvatar(optional(request.getAvatar()));
        user.setShowInLeaderboard(request.getShowInLeaderboard());
        user.setUpdatedAt(OffsetDateTime.now());

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Dos peticiones simultáneas con el mismo alias: gana el índice único
            throw new ConflictException("Ese alias ya está en uso. Prueba con otro.");
        }
        return toResponse(user);
    }

    /**
     * Cambia la contraseña e invalida todas las sesiones abiertas (password_changed_at),
     * incluida la actual, que además se revoca explícitamente.
     */
    @Transactional
    public void changeMyPassword(ChangePasswordRequest request, String currentToken) {
        User user = currentUserProvider.requireCurrentUser();

        rateLimitService.check("password-change:" + user.getId());

        String current = passwordTransport.resolve(request.getCurrentPassword(), request.getSealedCurrentPassword());
        String next = passwordTransport.resolveNewPassword(request.getNewPassword(), request.getSealedNewPassword());

        if (current == null || current.isEmpty() || current.length() > PasswordTransportService.MAX_PASSWORD_LENGTH) {
            throw new BadRequestException("La contraseña actual no es correcta");
        }
        if (!passwordService.verifyPassword(current, user.getPasswordHash())) {
            throw new BadRequestException("La contraseña actual no es correcta");
        }
        if (next.equals(current)) {
            throw new BadRequestException("La nueva contraseña debe ser distinta de la actual");
        }

        OffsetDateTime now = OffsetDateTime.now();
        user.setPasswordHash(passwordService.hashPassword(next));
        user.setPasswordChangedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        if (currentToken != null) {
            authService.revoke(currentToken);
        }
    }

    private ProfileResponse toResponse(User user) {
        GamificationService.PlayerStats stats = gamificationService.statsFor(user);
        return new ProfileResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole().name(),
                user.getDisplayName(),
                GamificationService.publicName(user.getDisplayName(), user.getFirstName(), user.getLastName()),
                user.getBio(),
                user.getCountry(),
                user.getAvatar(),
                user.isShowInLeaderboard(),
                user.getCreatedAt(),
                stats.xp(),
                stats.league(),
                stats.rank(),
                stats.totalRanked(),
                stats.modulesCompleted(),
                stats.modulesTotal(),
                stats.averageScore(),
                stats.currentStreak(),
                stats.longestStreak(),
                stats.badges(),
                stats.activity());
    }

    /** Quita caracteres de control (salvo saltos de línea) y espacios sobrantes. */
    private String clean(String value) {
        return value == null ? null : value.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").strip();
    }

    private String optional(String value) {
        String cleaned = clean(value);
        return cleaned == null || cleaned.isEmpty() ? null : cleaned;
    }
}
