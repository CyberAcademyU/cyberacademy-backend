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
| GET | `/modules` | JWT | Catálogo de módulos activos con sus 4 secciones |
| GET | `/modules/{id}` | JWT | Detalle de un módulo |
| POST | `/modules` | ADMIN | Crea un módulo (la BD genera 3 teoría + 1 práctica) |
| PUT | `/modules/{id}` | ADMIN | Actualiza título, descripción, orden, nota mínima o estado |
| PUT | `/modules/{moduleId}/sections/{sectionId}` | ADMIN | Actualiza título y/o peso de una sección |
| PUT | `/modules/{moduleId}/weights` | ADMIN | Actualiza los 4 pesos en una sola petición (deben sumar 100) |
| GET | `/modules/{moduleId}/progress` | JWT | Mi progreso en ese módulo, sección por sección |
| PUT | `/modules/{moduleId}/sections/{sectionId}/score` | JWT | Registra mi nota en una sección |
| GET | `/progress` | JWT | Resumen de mi progreso en todos los módulos |
| GET | `/progress/student/{userId}` | ADMIN | Resumen del progreso de un estudiante |
| GET | `/admin/overview` | ADMIN | Métricas globales: estudiantes, nota media, avance y finalización por módulo |
| GET | `/admin/students` | ADMIN | Estudiantes reales con módulos completados, nota media y última actividad |

## Módulos y progreso

Cada módulo tiene **3 sesiones de teoría y 1 de práctica**, generadas automáticamente por un trigger al crear el módulo, con pesos por defecto `20 / 20 / 20 / 40`.

- La nota del módulo se calcula en la base de datos:
  `nota = Σ(nota_sección × peso) / Σ(peso de secciones calificadas)`.
- El estado pasa solo: `NOT_STARTED` → `IN_PROGRESS` → `COMPLETED` (nota ≥ `passing_score`, 70 por defecto) o `FAILED`.
- Si un administrador cambia los pesos, los progresos ya calculados se recalculan (migración `V4`).
- Registrar una nota es idempotente: vuelve a enviarla para corregirla; el contador `attempts` aumenta.

Ejemplo de registro de nota:

```http
PUT /api/v1/modules/{moduleId}/sections/{sectionId}/score
Authorization: Bearer <token>
Content-Type: application/json

{ "score": 85, "completed": true }
```


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
├── module
│   ├── controller   # ModuleController, ProgressController
│   ├── dto          # requests, respuestas y resúmenes
│   ├── entity       # CourseModule, ModuleSection, Student*Progress
│   ├── repository   # Spring Data JPA
│   └── service      # ModuleService (catálogo y progreso)
├── admin
│   ├── controller   # AdminDashboardController (/admin/overview, /admin/students)
│   ├── dto          # estadísticas y listado de estudiantes
│   └── service      # AdminDashboardService (agregaciones SQL)
├── common/exception # ResourceNotFound, Conflict, BadRequest
├── config           # GlobalExceptionHandler
└── security         # JWT, filtro, Argon2id, CORS, CurrentUserProvider
src/main/resources
├── application.yml
└── db/migration
    ├── V1__init_auth_schema.sql
    ├── V2__modules_and_progress.sql
    ├── V3__secure_modules_objects.sql
    └── V4__recalculate_on_weight_change.sql
```

## Seguridad

- Contraseñas con **Argon2id** (parámetros OWASP: 64 MB, 3 iteraciones).
- JWT firmado con HMAC-SHA, expiración 60 min.
- Bloqueo temporal de cuenta tras 5 intentos fallidos.
- OTP de 6 dígitos, 15 min de validez, máximo 5 intentos, un solo uso.
- Nunca se registran códigos OTP ni contraseñas en los logs.
