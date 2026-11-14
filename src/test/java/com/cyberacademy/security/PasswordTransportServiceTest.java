package com.cyberacademy.security;

import com.cyberacademy.domains.auth.dto.SealedSecret;
import com.cyberacademy.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PasswordTransportServiceTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long";
    private final PasswordTransportService service = new PasswordTransportService(SECRET, false);

    @Test
    void roundTripRecoversPassword() throws Exception {
        SealedSecret sealed = PasswordTransportService.sealForTesting("contraseña-segura", service.publicInfo());
        assertEquals("contraseña-segura", service.resolve(null, sealed));
    }

    @Test
    void sameSecretProducesStableKeyAcrossRestarts() {
        var restarted = new PasswordTransportService(SECRET, false);
        assertEquals(service.publicInfo().keyId(), restarted.publicInfo().keyId());
        assertEquals(service.publicInfo().publicKey(), restarted.publicInfo().publicKey());

        var otherSecret = new PasswordTransportService("another-secret-at-least-32-bytes", false);
        assertNotEquals(service.publicInfo().keyId(), otherSecret.publicInfo().keyId());
    }

    @Test
    void rejectsTamperedCiphertext() throws Exception {
        SealedSecret sealed = PasswordTransportService.sealForTesting("contraseña-segura", service.publicInfo());
        byte[] packed = Base64.getDecoder().decode(sealed.data());
        packed[packed.length - 1] ^= 0x01;
        var tampered = new SealedSecret(sealed.keyId(), sealed.clientPublicKey(),
                Base64.getEncoder().encodeToString(packed));

        assertThrows(BadRequestException.class, () -> service.resolve(null, tampered));
    }

    @Test
    void rejectsWrongKeyIdAndMalformedEnvelope() throws Exception {
        SealedSecret sealed = PasswordTransportService.sealForTesting("contraseña-segura", service.publicInfo());

        assertThrows(BadRequestException.class,
                () -> service.resolve(null, new SealedSecret("otra-clave", sealed.clientPublicKey(), sealed.data())));
        assertThrows(BadRequestException.class,
                () -> service.resolve(null, new SealedSecret(sealed.keyId(), null, sealed.data())));
        assertThrows(BadRequestException.class,
                () -> service.resolve(null, new SealedSecret(sealed.keyId(), sealed.clientPublicKey(), "no-es-base64")));
    }

    @Test
    void plaintextOnlyAcceptedWhenEncryptionIsNotRequired() {
        assertEquals("texto-plano", new PasswordTransportService(SECRET, false).resolve("texto-plano", null));
        assertThrows(BadRequestException.class,
                () -> new PasswordTransportService(SECRET, true).resolve("texto-plano", null));
    }

    @Test
    void enforcesPasswordLengthAfterDecryption() throws Exception {
        SealedSecret shortPassword = PasswordTransportService.sealForTesting("corta", service.publicInfo());
        assertThrows(BadRequestException.class, () -> service.resolveNewPassword(null, shortPassword));

        SealedSecret longPassword = PasswordTransportService.sealForTesting("x".repeat(129), service.publicInfo());
        assertThrows(BadRequestException.class, () -> service.resolveNewPassword(null, longPassword));

        SealedSecret valid = PasswordTransportService.sealForTesting("ocho-och", service.publicInfo());
        assertEquals("ocho-och", service.resolveNewPassword(null, valid));
    }
}
