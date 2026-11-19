package com.cyberacademy.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Permite @PreAuthorize en los controladores (p. ej. solo ADMIN)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          @Value("${security.cors.allowed-origins:http://localhost:4200}") String allowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        // Se admiten varios or├¡genes separados por comas en la propiedad del application.yml
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // OriginCsrfFilter: Origin exacto + cabecera obligatoria.
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // El dispatch as├¡ncrono solo reanuda una petici├│n ya autenticada.
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                // Rutas p├║blicas de autenticaci├│n
                .requestMatchers("/auth/me").authenticated()
                .requestMatchers("/auth/**").permitAll()
                // Cat├ílogo p├║blico (p├ígina "Explorar"): solo GET
                .requestMatchers(HttpMethod.GET, "/public/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/rocu/chat").permitAll()
                // /error debe ser p├║blico: si no, los errores se enmascaran como 401
                .requestMatchers("/error").permitAll()
                // Health check de Render. P├║blico pero sin detalles
                // (management.endpoint.health.show-details=never).
                .requestMatchers("/actuator/health").permitAll()
                // Panel de administraci├│n: solo ADMIN (adem├ís del @PreAuthorize del controlador)
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Cualquier otra requiere autenticaci├│n
                .anyRequest().authenticated()
            )
            .addFilterBefore(new SecurityHeadersFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new OriginCsrfFilter(allowedOrigins), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins); // Angular (configurable por CORS_ALLOWED_ORIGINS)
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setAllowCredentials(true);
        // Cachea el preflight (OPTIONS) 1 hora en el navegador. Sin esto, cada petici├│n
        // con Authorization dispara un OPTIONS extra contra un backend lento.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthenticationFilter> sessionFilterRegistration() {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false); // Ejecutar solo dentro de Spring Security, despu├®s de CORS.
        return registration;
    }
}
