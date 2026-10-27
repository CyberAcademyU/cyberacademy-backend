# CyberAcademy — Backend

API REST de la plataforma de ciberseguridad **CyberAcademy**. Gestiona registro de estudiantes con verificación por código OTP enviado al correo, autenticación con JWT y control de acceso por roles.

## Stack

| Tecnología | Versión |
|---|---|
| Java | 21+ |
| Spring Boot | 3.3.4 |
| Spring Security | 6.x (stateless, JWT) |
| Spring Data JPA / Hibernate | 6.5 |
| PostgreSQL (Supabase) | 17 |
| Flyway | migraciones versionadas |
| Argon2id (BouncyCastle) | hash de contraseñas |
| JavaMail (SMTP) | envío de OTP |

## Requisitos

- JDK 21 o superior
- Maven 3.9+
- Proyecto en Supabase (PostgreSQL)
- Cuenta SMTP para el envío de correos (Gmail con contraseña de aplicación, Resend, etc.)

## Configuración

Las credenciales **no se versionan**. Crea el archivo `src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://<host>:5432/postgres?sslmode=require
    username: postgres.<project-ref>
    password: <password-de-supabase>

  mail:
    host: smtp.gmail.com
    port: 587
    username: <tu-correo@gmail.com>
    password: <app-password-de-16-caracteres>

security:
  jwt:
    secret: <cadena-aleatoria-de-al-menos-32-bytes>
  cors:
    allowed-origins: http://localhost:4200
  mail:
    from: CyberAcademy <tu-correo@gmail.com>
```

Ese archivo está en `.gitignore`. En producción, usa variables de entorno:

| Variable | Descripción |
|---|---|
| `SUPABASE_DB_URL` | URL JDBC completa |
| `SUPABASE_DB_USERNAME` | `postgres.<project-ref>` |
| `SUPABASE_DB_PASSWORD` | contraseña de la BD |
| `JWT_SECRET` | secreto de firma de los tokens |
| `MAIL_HOST` / `MAIL_PORT` | servidor SMTP |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | credenciales SMTP |
| `MAIL_FROM` | remitente visible |
| `CORS_ALLOWED_ORIGINS` | orígenes permitidos, separados por comas |
| `SPRING_PROFILES_ACTIVE` | `prod` en producción |

## Ejecutar

```bash
mvn spring-boot:run
```

- API: `http://localhost:8080/api/v1`
- El perfil `local` se activa por defecto (lee `application-local.yml`)
- Flyway aplica las migraciones automáticamente al arrancar (`ddl-auto: validate`)

## Endpoints

Base: `/api/v1`

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | `/auth/register` | — | Registro de estudiante (rol STUDENT) y envío de OTP |
| POST | `/auth/verify-otp` | — | Verifica el código y activa la cuenta |
| POST | `/auth/resend-otp` | — | Reenvía un nuevo código |
| POST | `/auth/login` | — | Login; devuelve JWT |

## Validaciones de correo

Antes de enviar un OTP se valida que el dominio tenga registros DNS (MX/A) y se detectan errores de escritura en proveedores comunes (`gmial.com` → sugiere `gmail.com`). Aun así, la existencia de un buzón concreto no puede comprobarse sin enviar el correo.

## Estructura

```
src/main/java/com/cyberacademy
├── auth
│   ├── controller   # endpoints
│   ├── dto          # request/response + ApiResponse
│   ├── entity       # User, EmailOtp y enums
│   ├── exception    # EmailDeliveryException
│   ├── repository   # Spring Data JPA
│   └── service      # AuthService, EmailService, EmailAddressVerifier
├── config           # GlobalExceptionHandler
└── security         # JWT, filtro, Argon2id, CORS
src/main/resources
├── application.yml
└── db/migration     # V1__init_auth_schema.sql
```

## Seguridad

- Contraseñas con **Argon2id** (parámetros OWASP: 64 MB, 3 iteraciones).
- JWT firmado con HMAC-SHA, expiración 60 min.
- Bloqueo temporal de cuenta tras 5 intentos fallidos.
- OTP de 6 dígitos, 15 min de validez, máximo 5 intentos, un solo uso.
- Nunca se registran códigos OTP ni contraseñas en los logs.
