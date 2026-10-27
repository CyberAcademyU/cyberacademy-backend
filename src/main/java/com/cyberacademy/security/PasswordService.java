package com.cyberacademy.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    // Parámetros recomendados por OWASP para Argon2id:
    // salt: 16 bytes, hash: 32 bytes, paralelismo: 1 hilo, memoria: 64MB (65536 KB), iteraciones: 3
    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);

    public String hashPassword(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}