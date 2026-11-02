package com.cyberacademy.security;

import com.cyberacademy.domains.auth.repository.RevokedTokenRepository;
import com.cyberacademy.domains.auth.repository.UserRepository;
import com.cyberacademy.domains.auth.model.UserStatus;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthenticationFilter extends OncePerRequestFilter implements Ordered {

    private final JwtService jwtService;
    private final RevokedTokenRepository revokedTokenRepository;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   RevokedTokenRepository revokedTokenRepository,
                                   UserRepository userRepository) {
        this.jwtService = jwtService;
        this.revokedTokenRepository = revokedTokenRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtService.validateToken(token)) {
                // Rechazar si el token ha sido revocado (logout, suspensión, cambio de rol, etc.)
                String jti = jwtService.getJtiFromToken(token);
                if (revokedTokenRepository.existsByJti(jti)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Token revocado. Inicie sesión nuevamente.\"}");
                    response.setContentType("application/json");
                    return;
                }

                Claims claims = jwtService.getClaimsFromToken(token);
                com.cyberacademy.domains.auth.model.User currentUser;
                try {
                    currentUser = userRepository.findById(UUID.fromString(claims.getSubject())).orElse(null);
                } catch (IllegalArgumentException | NullPointerException ex) {
                    currentUser = null;
                }
                if (currentUser == null || currentUser.getStatus() != UserStatus.ACTIVE
                        || !currentUser.isEmailVerified()
                        || issuedBeforePasswordChange(claims, currentUser)) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }

                User userDetails = new User(
                        currentUser.getEmail(),
                        "",
                        true,
                        true,
                        true,
                        true,
                        List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.getRole().name())));

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, token, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    /** Un cambio de contraseña invalida todas las sesiones abiertas antes de él (iat tiene precisión de segundos). */
    private boolean issuedBeforePasswordChange(Claims claims, com.cyberacademy.domains.auth.model.User user) {
        if (user.getPasswordChangedAt() == null) return false;
        if (claims.getIssuedAt() == null) return true;
        return claims.getIssuedAt().toInstant().getEpochSecond()
                <= user.getPasswordChangedAt().toInstant().getEpochSecond();
    }

    @Override
    public int getOrder() {
        return 100; // Before UsernamePasswordAuthenticationFilter (default order 1000)
    }
}
