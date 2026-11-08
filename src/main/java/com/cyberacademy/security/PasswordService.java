package com.cyberacademy.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PasswordService {

    // Parámetros recomendados por OWASP para Argon2id:
    // salt: 16 bytes, hash: 32 bytes, paralelismo: 1 hilo, memoria: 64MB (65536 KB), iteraciones: 3
    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);

    /**
     * Hash dummy generado al arrancar con una contraseña aleatoria que nadie conoce.
     * Se usa para verificar contraseñas de correos inexistentes: así el tiempo de
     * respuesta es el mismo que el de un usuario real y no se puede enumerar
     * cuentas midiendo latencias.
     */
    private final String dummyHash = encoder.encode(UUID.randomUUID().toString());

    public String hashPassword(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }

    /** Verifica la contraseña contra el hash dummy (constante de tiempo comparable a un usuario real). */
    public boolean verifyAgainstDummy(String rawPassword) {
        return encoder.matches(rawPassword, dummyHash);
    }
}