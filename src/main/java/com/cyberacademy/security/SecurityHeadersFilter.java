package com.cyberacademy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Añade cabeceras de seguridad HTTP a todas las respuestas:
 *  - X-Frame-Options / frame-ancestors: evita clickjacking
 *  - X-Content-Type-Options: nosniff (evita MIME sniffing)
 *  - Referrer-Policy: strict-origin-when-cross-origin
 *  - COOP/CORP y Permissions-Policy: aíslan la ventana y desactivan APIs del navegador
 *  - Strict-Transport-Security: solo si la conexión es HTTPS
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP =
            "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
    private static final String PERMISSIONS =
            "camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Content-Security-Policy", CSP);
        // El filtro XSS antiguo puede introducir vulnerabilidades; CSP reemplaza su función.
        response.setHeader("X-XSS-Protection", "0");
        response.setHeader("Cache-Control", "no-store");
        // Aísla la ventana frente a ventanas de otros orígenes y limita qué puede
        // incrustar esta respuesta. `same-site` deja pasar a la SPA del mismo dominio.
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Resource-Policy", "same-site");
        response.setHeader("X-Permitted-Cross-Domain-Policies", "none");
        response.setHeader("Permissions-Policy", PERMISSIONS);

        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }

        filterChain.doFilter(request, response);
    }
}
