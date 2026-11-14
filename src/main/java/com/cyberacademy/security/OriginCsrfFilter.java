package com.cyberacademy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

/** Origen exacto + cabecera no simple: cubre también login y refresh sin cookies legibles en JS. */
public class OriginCsrfFilter extends OncePerRequestFilter {
    private final List<String> origins;
    public OriginCsrfFilter(List<String> origins) { this.origins = List.copyOf(origins); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())
                && (request.getHeader("Origin") == null || !origins.contains(request.getHeader("Origin"))
                || !"CyberAcademy".equals(request.getHeader("X-Requested-With")))) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Solicitud no permitida\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
