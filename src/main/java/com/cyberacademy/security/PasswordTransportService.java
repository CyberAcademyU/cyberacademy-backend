package com.cyberacademy.security;

import com.cyberacademy.domains.auth.dto.SealedSecret;
import com.cyberacademy.exception.BadRequestException;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Descifra las contraseñas que el navegador envía con cifrado híbrido.
 *
 * <p>La clave privada del servidor se deriva de forma determinista de un secreto
 * (por defecto {@code JWT_SECRET}), de modo que no cambia al reiniciar ni entre
 * instancias. El par es ECDH P-256; la clave de sesión sale de HKDF-SHA256 y el
 * cifrado es AES-256-GCM (autenticado: un texto manipulado se rechaza).
 *
 * <p>No sustituye a TLS y no protege frente a XSS: quien ejecuta código en la
 * página puede leer la contraseña antes de cifrarla. Solo evita que el secreto
 * quede visible en el inspector de red.
 */
@Service
public class PasswordTransportService {

    private static final String CURVE = "secp256r1";
    static final byte[] SALT = "cyberacademy-password-salt-v1".getBytes(StandardCharsets.UTF_8);
    static final byte[] INFO = "cyberacademy-password-v1".getBytes(StandardCharsets.UTF_8);
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;

    private final BigInteger privateScalar;
    private final byte[] publicPoint;
    private final String keyId;
    private final ECParameterSpec params;
    private final boolean requireEncryption;

    public record PublicInfo(String keyId, String algorithm, String publicKey) {}

    public PasswordTransportService(
            @Value("${security.password-transport.secret:${JWT_SECRET}}") String secret,
            @Value("${security.password-transport.require-encryption:false}") boolean requireEncryption) {
        this.requireEncryption = requireEncryption;
        try {
            AlgorithmParameters algorithmParameters = AlgorithmParameters.getInstance("EC");
            algorithmParameters.init(new ECGenParameterSpec(CURVE));
            this.params = algorithmParameters.getParameterSpec(ECParameterSpec.class);
            BigInteger n = params.getOrder();
            byte[] okm = hkdf(secret.getBytes(StandardCharsets.UTF_8), SALT, INFO, KEY_BYTES);
            // Escalar en [1, n-1]: nunca cero, que produciría una clave degenerada.
            this.privateScalar = new BigInteger(1, okm).mod(n.subtract(BigInteger.ONE)).add(BigInteger.ONE);
            this.publicPoint = ECNamedCurveTable.getParameterSpec(CURVE).getG()
                    .multiply(privateScalar).normalize().getEncoded(false);
            this.keyId = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(publicPoint)).substring(0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo inicializar el transporte de contraseñas", e);
        }
    }

    public PublicInfo publicInfo() {
        return new PublicInfo(keyId, "ECDH-P256+HKDF-SHA256+AES-256-GCM",
                Base64.getEncoder().encodeToString(publicPoint));
    }

    /**
     * Devuelve la contraseña en claro después de descifrarla. Si el cliente no la
     * cifró, solo se acepta cuando require-encryption está desactivado (clientes antiguos).
     */
    public String resolve(String plaintext, SealedSecret sealed) {
        boolean present = sealed != null && sealed.data() != null && !sealed.data().isBlank();
        if (!present) {
            if (requireEncryption) {
                throw new BadRequestException("La contraseña debe enviarse cifrada. Recarga la página.");
            }
            return plaintext;
        }
        if (sealed.keyId() == null || !keyId.equals(sealed.keyId())) {
            throw new BadRequestException("La clave de cifrado cambió. Recarga la página e inténtalo de nuevo.");
        }
        if (sealed.clientPublicKey() == null || sealed.clientPublicKey().isBlank()) {
            throw new BadRequestException("No se pudo procesar la contraseña. Recarga la página e inténtalo de nuevo.");
        }
        try {
            return decrypt(sealed);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BadRequestException("No se pudo procesar la contraseña. Recarga la página e inténtalo de nuevo.");
        }
    }

    /**
     * Resuelve una contraseña nueva y exige la longitud admitida. Se usa en los
     * flujos donde la contraseña se establece (verificación, recuperación, cambio).
     */
    public String resolveNewPassword(String plaintext, SealedSecret sealed) {
        String value = resolve(plaintext, sealed);
        if (value == null || value.length() < MIN_PASSWORD_LENGTH || value.length() > MAX_PASSWORD_LENGTH) {
            throw new BadRequestException(
                    "La contraseña debe tener entre " + MIN_PASSWORD_LENGTH + " y " + MAX_PASSWORD_LENGTH + " caracteres");
        }
        return value;
    }

    private String decrypt(SealedSecret sealed) throws GeneralSecurityException {
        byte[] rawClientKey = Base64.getDecoder().decode(sealed.clientPublicKey());
        var bcPoint = ECNamedCurveTable.getParameterSpec(CURVE).getCurve().decodePoint(rawClientKey);
        if (!bcPoint.isValid()) {
            throw new GeneralSecurityException("Invalid client public key");
        }
        ECPoint point = new ECPoint(bcPoint.getAffineXCoord().toBigInteger(), bcPoint.getAffineYCoord().toBigInteger());
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PublicKey clientKey = keyFactory.generatePublic(new ECPublicKeySpec(point, params));
        PrivateKey serverKey = keyFactory.generatePrivate(new ECPrivateKeySpec(privateScalar, params));

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(serverKey);
        agreement.doPhase(clientKey, true);
        byte[] aesKey = hkdf(agreement.generateSecret(), SALT, INFO, KEY_BYTES);

        byte[] sealedBytes = Base64.getDecoder().decode(sealed.data());
        if (sealedBytes.length <= IV_BYTES + TAG_BITS / 8) {
            throw new GeneralSecurityException("Sealed payload too short");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                new GCMParameterSpec(TAG_BITS, Arrays.copyOf(sealedBytes, IV_BYTES)));
        byte[] plain = cipher.doFinal(sealedBytes, IV_BYTES, sealedBytes.length - IV_BYTES);
        return new String(plain, StandardCharsets.UTF_8);
    }

    /** HKDF-SHA256 (RFC 5869) de un solo bloque: suficiente para una clave AES-256. */
    static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int length) throws GeneralSecurityException {
        if (length > 32) {
            throw new GeneralSecurityException("HKDF length too large for one block");
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt.length == 0 ? new byte[32] : salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return Arrays.copyOf(mac.doFinal(), length);
    }

    /** Utilidad para pruebas y herramientas: cifra como lo haría el navegador. */
    static SealedSecret sealForTesting(String password, PublicInfo server) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec(CURVE));
        var clientPair = generator.generateKeyPair();
        var curve = ECNamedCurveTable.getParameterSpec(CURVE);
        var serverPoint = curve.getCurve().decodePoint(Base64.getDecoder().decode(server.publicKey()));
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PublicKey serverKey = keyFactory.generatePublic(new ECPublicKeySpec(
                new ECPoint(serverPoint.getAffineXCoord().toBigInteger(), serverPoint.getAffineYCoord().toBigInteger()),
                paramsOf()));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(clientPair.getPrivate());
        agreement.doPhase(serverKey, true);
        byte[] aesKey = hkdf(agreement.generateSecret(), SALT, INFO, KEY_BYTES);

        byte[] iv = new byte[IV_BYTES];
        new java.security.SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
        byte[] payload = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(ct, 0, payload, iv.length, ct.length);
        var encoder = Base64.getEncoder();
        return new SealedSecret(server.keyId(),
                encoder.encodeToString(rawPoint(clientPair.getPublic())),
                encoder.encodeToString(payload));
    }

    private static ECParameterSpec paramsOf() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec(CURVE));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    /** X.509 de P-256 → punto sin comprimir de 65 bytes. */
    private static byte[] rawPoint(PublicKey key) {
        java.security.interfaces.ECPublicKey ec = (java.security.interfaces.ECPublicKey) key;
        byte[] x = toFixed(ec.getW().getAffineX(), 32);
        byte[] y = toFixed(ec.getW().getAffineY(), 32);
        byte[] raw = new byte[65];
        raw[0] = 0x04;
        System.arraycopy(x, 0, raw, 1, 32);
        System.arraycopy(y, 0, raw, 33, 32);
        return raw;
    }

    private static byte[] toFixed(BigInteger value, int size) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[size];
        int copy = Math.min(bytes.length, size);
        System.arraycopy(bytes, bytes.length - copy, out, size - copy, copy);
        return out;
    }
}
