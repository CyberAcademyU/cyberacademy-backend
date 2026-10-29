package com.cyberacademy.auth.service;

import com.cyberacademy.auth.dto.*;
import com.cyberacademy.auth.entity.*;
import com.cyberacademy.auth.repository.EmailOtpRepository;
import com.cyberacademy.auth.repository.RevokedTokenRepository;
import com.cyberacademy.auth.repository.UserRepository;
import com.cyberacademy.security.JwtService;
import com.cyberacademy.security.PasswordService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final int LOCK_TIME_MINUTES = 15;

    private final UserRepository userRepository;
    private final EmailOtpRepository otpRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final RateLimitService rateLimitService;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final EmailAddressVerifier emailAddressVerifier;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       EmailOtpRepository otpRepository,
                       RevokedTokenRepository revokedTokenRepository,
                       RateLimitService rateLimitService,
                       PasswordService passwordService,
                       JwtService jwtService,
                       EmailService emailService,
                       EmailAddressVerifier emailAddressVerifier) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.revokedTokenRepository = revokedTokenRepository;
        this.rateLimitService = rateLimitService;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.emailAddressVerifier = emailAddressVerifier;
    }

    @Transactional
    public ApiResponse<String> register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // Rate limiting por correo para evitar abusos en el registro
        if (!rateLimitService.isAllowed("register:" + email)) {
            return ApiResponse.error("Demasiados intentos de registro. Intenta nuevamente en 15 minutos.");
        }

        // 0. Validar que el dominio del correo exista y pueda recibir mensajes
        //    (evita enviar el OTP a dominios mal escritos, que provocan rebotes)
        EmailAddressVerifier.Result emailCheck = emailAddressVerifier.verify(email);
        if (!emailCheck.valid()) {
            return ApiResponse.error(emailCheck.message());
        }

        // 1. Validar si ya existe el correo
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return ApiResponse.error("Este correo electrónico ya está registrado");
        }

        // 2. Hashear la contraseña con Argon2id (OWASP)
        String hashedPassword = passwordService.hashPassword(request.getPassword());

        // 3. Crear usuario con rol STUDENT obligatorio y pendiente de verificación
        User user = new User();
        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName().trim());
        user.setEmail(email);
        user.setPasswordHash(hashedPassword);
        user.setRole(UserRole.STUDENT); // La base de datos asegura mínimo privilegio
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setEmailVerified(false);

        User savedUser = userRepository.save(user);

        // 4. Generar código numérico criptográfico de 6 dígitos
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        EmailOtp otp = new EmailOtp();
        otp.setUserId(savedUser.getId());
        otp.setEmail(email);
        otp.setOtpCode(code);
        otp.setType(OtpType.EMAIL_VERIFICATION);
        otp.setExpiresAt(OffsetDateTime.now().plusMinutes(15));
        otp.setAttempts(0);
        otp.setUsed(false);

        otpRepository.save(otp);

        // 5. Enviar el código por correo. Si falla, la transacción hace rollback
        //    para no dejar un usuario registrado que no puede verificar su cuenta.
        //    Nota de seguridad: el código en claro NO debe registrarse en los logs.
        emailService.sendVerificationCode(email, user.getFirstName(), code);

        log.info("Registro de estudiante creado y código de verificación enviado a [{}]", email);

        return ApiResponse.ok("Usuario registrado. Revisa tu correo para obtener el código de verificación.", email);
    }

    @Transactional
    public ApiResponse<String> verifyOtp(VerifyOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // Rate limiting por correo para evitar fuerza bruta en el código OTP
        if (!rateLimitService.isAllowed("verify-otp:" + email)) {
            return ApiResponse.error("Demasiados intentos de verificación. Intenta nuevamente en 15 minutos.");
        }

        // El bloqueo del usuario serializa la verificación y el reenvío, incluso
        // cuando aún no existe una fila OTP que pueda bloquearse.
        var userOptional = userRepository.findLockedByEmailIgnoreCase(email);
        if (userOptional.isEmpty()) {
            return ApiResponse.error("No se encontró ningún código de verificación activo.");
        }
        var otpOptional = otpRepository.findTopByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                userOptional.get().getId(), OtpType.EMAIL_VERIFICATION
        );

        if (otpOptional.isEmpty() || otpOptional.get().isUsed()) {
            return ApiResponse.error("No se encontró ningún código de verificación activo.");
        }

        EmailOtp otp = otpOptional.get();

        // Validar expiración
        if (OffsetDateTime.now().isAfter(otp.getExpiresAt())) {
            return ApiResponse.error("El código de verificación ha expirado. Solicita uno nuevo.");
        }

        // Validar intentos (comparación en tiempo constante para evitar timing attacks)
        if (!MessageDigest.isEqual(
                otp.getOtpCode().getBytes(StandardCharsets.UTF_8),
                request.getOtpCode().trim().getBytes(StandardCharsets.UTF_8))) {
            otp.setAttempts(otp.getAttempts() + 1);
            if (otp.getAttempts() >= otp.getMaxAttempts()) {
                otp.setUsed(true); // Invalidar por exceso de intentos
                otpRepository.save(otp);
                return ApiResponse.error("Has superado el número máximo de intentos. Solicita otro código.");
            }
            otpRepository.save(otp);
            return ApiResponse.error("Código incorrecto. Intentos restantes: " + (otp.getMaxAttempts() - otp.getAttempts()));
        }

        // Código correcto: marcar como usado
        otp.setUsed(true);
        otpRepository.save(otp);

        // Activar al usuario en la base de datos
        User user = userOptional.get();
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(OffsetDateTime.now());
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        return ApiResponse.ok("Correo verificado con éxito.", "ACTIVATED");
    }

    @Transactional
    public ApiResponse<String> resendOtp(ResendOtpRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // Rate limiting por correo para evitar abuso en el reenvío de OTP
        if (!rateLimitService.isAllowed("resend-otp:" + email)) {
            return ApiResponse.error("Demasiados solicitudes de reenvío. Intenta nuevamente en 15 minutos.");
        }

        EmailAddressVerifier.Result emailCheck = emailAddressVerifier.verify(email);
        if (!emailCheck.valid()) {
            return ApiResponse.error(emailCheck.message());
        }

        var userOptional = userRepository.findLockedByEmailIgnoreCase(email);
        if (userOptional.isEmpty() || userOptional.get().isEmailVerified()) {
            log.info("Reenvío OTP omitido para [{}]: {}", email,
                    userOptional.isEmpty() ? "usuario inexistente" : "usuario verificado");
            return ApiResponse.ok("Si la cuenta requiere verificación, se enviará un código.", null);
        }

        User user = userOptional.get();
        otpRepository.invalidatePrevious(user.getId(), OtpType.EMAIL_VERIFICATION);
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        EmailOtp otp = new EmailOtp();
        otp.setUserId(user.getId());
        otp.setEmail(email);
        otp.setOtpCode(code);
        otp.setType(OtpType.EMAIL_VERIFICATION);
        otp.setExpiresAt(OffsetDateTime.now().plusMinutes(15));
        otpRepository.save(otp);

        emailService.sendVerificationCode(email, user.getFirstName(), code);

        log.info("Nuevo código de verificación enviado a [{}]", email);

        return ApiResponse.ok("Si la cuenta requiere verificación, se enviará un código.", null);
    }

    @Transactional
    public ApiResponse<AuthResponse> login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        // Rate limiting por correo para evitar fuerza bruta en el inicio de sesión
        if (!rateLimitService.isAllowed("login:" + email)) {
            return ApiResponse.error("Demasiados intentos de inicio de sesión. Intenta nuevamente en 15 minutos.");
        }

        var userOptional = userRepository.findByEmailIgnoreCase(email);
        if (userOptional.isEmpty()) {
            return ApiResponse.error("Correo o contraseña incorrectos");
        }

        User user = userOptional.get();

        // Bloqueo temporal por intentos fallidos
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            return ApiResponse.error("Cuenta bloqueada temporalmente por intentos fallidos. Intenta más tarde.");
        }

        // Validar contraseña con Argon2id
        if (!passwordService.verifyPassword(request.getPassword(), user.getPasswordHash())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= MAX_FAILED_LOGIN_ATTEMPTS) {
                user.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCK_TIME_MINUTES));
            }
            userRepository.save(user);
            return ApiResponse.error("Correo o contraseña incorrectos");
        }

        // Validar si verificó su correo
        if (!user.isEmailVerified() || user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            return ApiResponse.error("Debes verificar tu correo antes de iniciar sesión.");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            return ApiResponse.error("Tu cuenta está suspendida por políticas de seguridad.");
        }

        // Restablecer intentos fallidos y actualizar último login
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        // Generar JWT con el rol que dictamina la base de datos (STUDENT o ADMIN)
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        AuthResponse.UserSummary summary = new AuthResponse.UserSummary(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole().name()
        );

        AuthResponse response = new AuthResponse(token, jwtService.getExpirationSeconds(), summary);
        return ApiResponse.ok("Inicio de sesión exitoso", response);
    }

    /**
     * Revoca un JWT añadiéndolo a la lista negra. El token seguirá siendo válido
     * criptográficamente hasta su expiración, pero el filtro lo rechazará.
     */
    @Transactional
    public void revokeToken(String token) {
        try {
            Claims claims = jwtService.getClaimsFromToken(token);
            String jti = claims.getId();
            UUID userId = UUID.fromString(claims.getSubject());
            Date expiration = claims.getExpiration();
            OffsetDateTime expiresAt = expiration.toInstant()
                    .atZone(java.time.ZoneOffset.UTC)
                    .toOffsetDateTime();

            RevokedToken revoked = new RevokedToken(jti, userId, expiresAt);
            revokedTokenRepository.save(revoked);
        } catch (Exception e) {
            // Token inválido o ya expirado: no hay nada que revocar
        }
    }
}