package com.cyberacademy.domains.auth.dto;

/**
 * Contraseña cifrada por el navegador: ECDH P-256 efímero + HKDF-SHA256 + AES-256-GCM.
 * `data` es Base64(IV || ciphertext || tag). No aporta seguridad frente a XSS:
 * su objetivo es que la contraseña no aparezca en claro en DevTools ni en capturas.
 */
public record SealedSecret(String keyId, String clientPublicKey, String data) {}
