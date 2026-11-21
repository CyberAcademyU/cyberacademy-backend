package com.cyberacademy.domains.auth.service;

import com.cyberacademy.domains.auth.model.*;
import com.cyberacademy.domains.auth.repository.UserRepository;
import com.cyberacademy.security.PasswordService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class GoogleIdentityService {
    private final UserRepository users;
    private final PasswordService passwords;
    private final String clientId;
    // Claves públicas de Google, almacenadas temporalmente por Nimbus. Solo RS256.
    private final JwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();

    public GoogleIdentityService(UserRepository users, PasswordService passwords, @Value("${GOOGLE_WEB_CLIENT_ID:411607888266-jsvasko53dbq9jtoanud8ir8nmlqtulc.apps.googleusercontent.com}") String clientId) {
        this.users = users;
        this.passwords = passwords;
        this.clientId = clientId;
    }

    public String clientId() { return clientId; }

    public Jwt verify(String credential, String nonce) {
        if (nonce == null || nonce.isBlank()) throw invalid();
        try {
            Jwt jwt = decoder.decode(credential);
            if (!"https://accounts.google.com".equals(jwt.getClaimAsString("iss"))
                    || jwt.getAudience() == null || !jwt.getAudience().contains(clientId)
                    || (jwt.hasClaim("azp") && !clientId.equals(jwt.getClaimAsString("azp")))
                    || jwt.getExpiresAt() == null || jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || !nonce.equals(jwt.getClaimAsString("nonce"))
                    || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
                    || jwt.getClaimAsString("email") == null) throw invalid();
            return jwt;
        } catch (JwtException e) {
            throw invalid();
        }
    }

    @Transactional
    public User resolve(Jwt jwt, User linking, String firstName, String lastName) {
        String email = jwt.getClaimAsString("email").strip().toLowerCase(Locale.ROOT);
        var known = users.findByGoogleSubject(jwt.getSubject());
        User user;
        if (linking != null) {
            user = users.findLockedByEmailIgnoreCase(linking.getEmail()).orElseThrow(GoogleIdentityService::invalid);
            if (!email.equalsIgnoreCase(user.getEmail()) || known.filter(u -> !u.getId().equals(user.getId())).isPresent()
                    || (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(jwt.getSubject())))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Selecciona la cuenta de Google correspondiente a tu correo.");
        } else if (known.isPresent()) {
            user = known.get();
        } else {
            if (users.existsByEmailIgnoreCase(email))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Inicia sesión con tu contraseña y vincula Google desde tu perfil.");
            if (!email.endsWith("@gmail.com") && !jwt.hasClaim("hd"))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Verifica este correo mediante el registro normal y vincula Google desde tu perfil.");
            // No crear usuario ni sesión hasta que confirme sus propios datos.
            if (firstName == null && lastName == null)
                throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Completa tu nombre y apellido para crear tu cuenta con Google.");
            String confirmedFirst = confirmedName(firstName);
            String confirmedLast = confirmedName(lastName);
            user = new User();
            user.setRole(UserRole.STUDENT);
            user.setStatus(UserStatus.ACTIVE);
            user.setEmail(email);
            user.setFirstName(confirmedFirst);
            user.setLastName(confirmedLast);
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(OffsetDateTime.now());
            user.setPasswordHash(passwords.hashPassword(UUID.randomUUID().toString()));
        }
        if (!user.isEmailVerified() || user.getStatus() != UserStatus.ACTIVE
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now()))) throw invalid();
        user.setGoogleSubject(jwt.getSubject());
        user.setLastLoginAt(OffsetDateTime.now());
        return users.saveAndFlush(user);
    }

    private static String confirmedName(String value) {
        if (value == null || value.strip().length() < 2 || value.strip().length() > 100
                || value.chars().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escribe nombre y apellido de entre 2 y 100 caracteres.");
        return value.strip();
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No se pudo validar el acceso con Google. Vuelve a intentarlo.");
    }
}
