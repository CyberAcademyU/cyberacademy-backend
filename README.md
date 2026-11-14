# CyberAcademy - Backend

## Correo OTP con Resend

Registro, reenvío de OTP y recuperación de contraseña usan el mismo transporte.
Para usar Resend en Render configura:

- `MAIL_TRANSPORT=resend`
- `RESEND_API_KEY`: API key con permiso de envío del dominio seleccionado.
- `MAIL_FROM=CyberAcademy <no-reply@tu-dominio-verificado.com>`

Verifica primero el dominio y sus registros DNS en Resend. El remitente de
prueba `onboarding@resend.dev` solo permite enviar a la dirección de tu cuenta
de Resend; para enviar a estudiantes necesitas un dominio verificado.
Guarda las variables y redespliega el backend. No hace falta recompilar la APK.
No guardes la API key en Git ni en el frontend. Gmail OAuth ya no se utiliza
cuando `MAIL_TRANSPORT=resend`.

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
  session:
    secure: false # cookies sin Secure solo en HTTP local
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
| `SESSION_COOKIE_SECURE` | `true` por defecto; `false` solo en local sin HTTPS |
| `PASSWORD_TRANSPORT_SECRET` | secreto de la clave ECDH; si falta usa `JWT_SECRET` |
| `PASSWORD_TRANSPORT_REQUIRE_ENCRYPTION` | `true` rechaza contraseñas sin cifrar; `false` por defecto |
| `SPRING_PROFILES_ACTIVE` | `prod` en producción |
| `MAIL_TRANSPORT` | `gmail-api` para enviar por HTTPS; `smtp` por defecto |
| `GMAIL_CLIENT_ID` | ID del cliente OAuth de Google para Gmail API |
| `GMAIL_CLIENT_SECRET` | secreto del cliente OAuth de Google |
| `GMAIL_REFRESH_TOKEN` | token de actualización autorizado con el permiso `gmail.send` |

### Secretos y archivos locales

- No subas contraseñas, claves, tokens JWT ni archivos `.env` al repositorio. `.env` y sus variantes (como `.env.local` y `.env.production`) están ignorados por Git; también lo está `application-local.yml`.
- Se permite `.env.example` únicamente como plantilla, con nombres de variables y valores de ejemplo sin credenciales reales.
- Antes de cada commit, revisa `git status` y `git diff --cached`. No uses `git add -f` con archivos secretos. Si un secreto ya se subió, ignorarlo después no lo elimina del historial: avisa al equipo y revócalo o rótalo.

## Flujo de trabajo con Git

Aplica estos pasos por separado en el repositorio donde trabajes:

- `master`: versión estable; se actualiza mediante Pull Request desde `develop`.
- `develop`: integración y pruebas; recibe Pull Requests desde ramas de tareas.
- `feature/descripcion` y `fix/descripcion`: ramas temporales para funcionalidades y correcciones. Cada persona crea una por tarea a partir de `develop`.

Actualiza `develop` y crea tu rama (usa un nombre descriptivo y diferente para cada tarea):

```bash
git fetch origin
git switch develop
git pull --ff-only origin develop
git switch -c feature/nombre-de-la-tarea
# Para una corrección: git switch -c fix/nombre-del-error
```

Después de implementar y probar el cambio:

```bash
git status
git add ruta/del/archivo
git diff --cached
git commit -m "feat: describir el cambio"
git push -u origin feature/nombre-de-la-tarea
```

En GitHub abre un Pull Request con **base `develop`** y como origen tu rama. Espera a que pase CI y solicita revisión a un compañero antes de fusionar. Cuando el equipo haya probado `develop`, abre otro Pull Request con **base `master`** y origen `develop`. No subas directamente a las ramas protegidas. Las ramas `feature/*` y `fix/*` se crean cuando comienza cada tarea, no de antemano.

## Ejecutar

Para habilitar imágenes y video en las teorías, aplica una sola vez `src/main/resources/db/migration/V9__theory_media.sql` en la base de datos del entorno correspondiente antes de arrancar esta versión. Flyway está desactivado en la configuración actual, por lo que no se ejecuta automáticamente. La migración agrega tres columnas opcionales; no incluye enlaces ni archivos multimedia.

### Despliegue de prueba en Render

Para desplegar la protección de OTP, aplica primero `V10__protect_email_verification_codes.sql`
en la base de datos del entorno y despliega inmediatamente el backend nuevo. Los códigos
de verificación antiguos se invalidan: sus usuarios deberán solicitar un nuevo código.
La protección usa `JWT_SECRET` con separación de propósito; cambiar ese secreto también
invalida los códigos pendientes. Coordina una ventana de mantenimiento para evitar que
la versión anterior genere códigos en claro entre la migración y el despliegue.

El registro requiere un frontend actualizado: primero se confirma el correo y se envía
un código; la contraseña definitiva se establece al verificar ese código. El backend
rechaza la verificación sin contraseña. Las cuentas pendientes no verificadas caducan
a las 24 horas y se eliminan con sus OTP mediante una tarea que se ejecuta cada hora;
las cuentas pendientes anteriores también están sujetas a esta limpieza. La verificación
y el reenvío se rechazan una vez vencido el plazo. Tras la eliminación se puede volver
a registrar el mismo correo. Despliega frontend y backend coordinadamente: el cliente
anterior no puede completar la verificación con el contrato nuevo.

El `Dockerfile` compila el backend con Java 21 y crea una imagen sin Maven. Crea un **Web Service** en Render conectado a este repositorio y selecciona **Docker**. El servicio escucha en el puerto 8080.

Configura en Render `SPRING_PROFILES_ACTIVE=prod`, `SUPABASE_DB_URL` (URL JDBC), `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD`, `JWT_SECRET` y `CORS_ALLOWED_ORIGINS`. Las cookies de sesión usan `Secure` por defecto (`SESSION_COOKIE_SECURE=true`). Antes de arrancar esta versión aplica `V11__opaque_cookie_sessions.sql`; el resto de la lista de comprobación está en `SEGURIDAD.md`. Usa una base de datos de prueba y configura los secretos en Render, nunca en el repositorio ni en el Dockerfile. La base debe tener las migraciones necesarias aplicadas manualmente: Flyway está desactivado y Hibernate valida el esquema al arrancar.

Cuando el servicio esté activo, comprueba `https://<servicio>.onrender.com/api/v1/public/modules`. En Render Free el servicio puede suspenderse tras inactividad y los puertos SMTP 465 y 587 están bloqueados: el envío por SMTP no funcionará con esos puertos. El plan gratuito es para pruebas, no garantiza capacidad para 100 usuarios.

Para enviar códigos desde Render Free, configura `MAIL_TRANSPORT=gmail-api`, las tres
variables OAuth `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET` y `GMAIL_REFRESH_TOKEN`, y
`MAIL_FROM` con la dirección Gmail autorizada. El backend reutiliza en memoria el token de acceso
hasta 60 segundos antes de su caducidad y utiliza la API de Gmail por HTTPS. Si Google
rechaza el token con HTTP 401, se invalida para el siguiente envío, sin reenviar
automáticamente el correo. También se utiliza para recuperación
de contraseña. No registres ni compartas las credenciales; introdúcelas directamente
en las variables de entorno de Render. La entrega real depende de la autorización y de
los límites de Google. En aplicaciones OAuth externas en modo de prueba, los refresh
tokens con el permiso Gmail pueden vencer a los siete días: esta configuración requiere
renovación y no ofrece envío continuo sin resolver la publicación de OAuth.

### Mantener despierto el servicio en Render Free

Render suspende un servicio Free tras **15 minutos sin tráfico entrante**. El workflow
`.github/workflows/keep-alive.yml` hace `GET` a `/api/v1/actuator/health` cada 10 minutos,
lo que renueva ese temporizador y evita el arranque en frío. El health check es público,
no muestra detalles y no consulta la base de datos, así que el ping no despierta Supabase.

Ten en cuenta estas limitaciones antes de confiar en el ping:

- Cada workspace tiene **750 horas de instancia gratis al mes**. Mantener el servicio
  despierto 24/7 consume unas 730 horas, casi todo el cupo. Un segundo servicio Free
  activo superaría el límite y Render suspendería los servicios hasta el mes siguiente.
- Los horarios de GitHub Actions **solo se ejecutan desde la rama por defecto** (`master`).
  Si el workflow solo existe en `develop`, el ping no se ejecuta.
- GitHub puede retrasar las ejecuciones programadas varios minutos bajo carga; el margen
  de 5 minutos cubre los retrasos habituales, no una caída prolongada de Actions.
- GitHub desactiva los workflows programados tras **60 días sin actividad** en el
  repositorio. Tras un periodo largo sin commits, revisa la pestaña **Actions** y
  reactívalo con **Enable workflow**.
- El ping no evita la espera de ~3 minutos después de cada despliegue ni tras un fallo
  de la propia instancia: en esos casos la primera petición sigue pagando el arranque.

Si el workflow no puede ejecutarse (por ejemplo, si no se fusiona a `master`), una
alternativa equivalente es un monitor externo como cron-job.org o UptimeRobot apuntando
a la misma URL cada 10–14 minutos. El endpoint `/robots.txt` no sirve: Render lo responde
sin despertar el servicio.

### Métricas en Grafana Cloud

La exportación de métricas técnicas mediante Micrometer y OTLP/HTTP está **desactivada
por defecto**. No hace públicos endpoints de Actuator. Para una prueba en Render Free:

1. En el portal de Grafana Cloud, abre tu stack y selecciona **OpenTelemetry → Configure**.
   Genera una credencial con permiso de escritura de métricas y obtén el endpoint OTLP.
2. En las variables de entorno del servicio backend de Render configura:
   - `GRAFANA_METRICS_ENABLED=true`
   - `GRAFANA_OTLP_METRICS_URL`: URL OTLP de tu stack **incluyendo `/v1/metrics`**.
     Si Grafana muestra una URL base terminada en `/otlp`, añade `/v1/metrics`.
   - `GRAFANA_OTLP_AUTHORIZATION`: valor completo de la cabecera HTTP de autorización,
     por ejemplo `Basic <credencial-codificada>`. Si el asistente muestra
     `Authorization=Basic%20...`, copia el valor posterior a `Authorization=` y
     reemplaza `%20` por un espacio. No copies el prefijo `Authorization=`.
3. Guarda y despliega el servicio. No incluyas estos valores en Git ni los compartas.
4. En Grafana, importa `observability/grafana-cyberacademy.json` desde **Dashboards →
   New → Import** y selecciona la fuente de datos Prometheus/Mimir de Grafana Cloud.
5. Genera tráfico normal y espera al menos dos intervalos de exportación (60 segundos
   cada uno) para ver las tasas. Las series 429/5xx aparecerán cuando ocurran esos estados.

La aplicación envía métricas agregadas (por ejemplo, métodos, rutas de API, estados,
latencia y memoria JVM), no el cuerpo de peticiones, contraseñas, códigos OTP ni tokens.
Esta configuración no envía logs ni trazas. En Render Free, al suspenderse la instancia
no se exportan datos; un hueco en el panel no identifica por sí solo la causa y las
métricas en memoria se reinician. Consulta los límites vigentes de tu plan Grafana
Cloud. El panel es una plantilla inicial y sus consultas deben comprobarse con las
series que reciba tu stack.

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
| POST | `/auth/login` | — | Login; deja las cookies de sesión (sin token en el JSON) |
| GET | `/auth/encryption-key` | — | Clave pública ECDH para cifrar la contraseña en el navegador |
| GET | `/auth/me` | cookie | Restaura la sesión de la SPA |
| POST | `/auth/refresh` | cookie refresh | Rota el par de cookies |
| POST | `/auth/refresh/logout` | cookie refresh | Revoca la sesión y borra las cookies |
| GET | `/modules` | JWT | Catálogo de módulos activos con sus 4 secciones |
| GET | `/modules/{id}` | JWT | Detalle de un módulo |
| POST | `/modules` | ADMIN | Crea un módulo (la BD genera 3 teoría + 1 práctica) |
| PUT | `/modules/{id}` | ADMIN | Actualiza título, descripción, orden, nota mínima o estado |
| PUT | `/modules/{moduleId}/sections/{sectionId}` | ADMIN | Actualiza título y/o peso de una sección |
| PUT | `/modules/{moduleId}/weights` | ADMIN | Actualiza los 4 pesos en una sola petición (deben sumar 100) |
| GET | `/modules/{moduleId}/progress` | JWT | Mi progreso en ese módulo, sección por sección |
| GET | `/modules/{moduleId}/sections/{sectionId}` | JWT | Sesión desbloqueada: contenido y preguntas (sin respuestas) |
| POST | `/modules/{moduleId}/sections/{sectionId}/check` | JWT | Comprueba la pregunta de comprensión de una teoría |
| POST | `/modules/{moduleId}/practice/submit` | JWT | Corrige la práctica (único intento) y guarda la nota |
| GET | `/modules/{moduleId}/content` | ADMIN | Contenido y preguntas de las 4 secciones (con respuestas) |
| PUT | `/modules/{moduleId}/sections/{sectionId}/content` | ADMIN | Guarda título, contenido y preguntas de una sesión |
| GET | `/progress` | JWT | Resumen de mi progreso en todos los módulos |
| GET | `/progress/student/{userId}` | ADMIN | Resumen del progreso de un estudiante |
| GET | `/admin/overview` | ADMIN | Métricas globales: estudiantes, nota media, avance y finalización por módulo |
| GET | `/admin/students` | ADMIN | Estudiantes reales con módulos completados, total acumulado, nota media y última actividad (el detalle por módulo sale de `/progress/student/{userId}`) |

## Módulos y progreso

El catálogo público de `/public/modules` se guarda en una caché local acotada durante
un máximo de 60 segundos para evitar repetir las consultas a módulos y secciones. Al
confirmar la creación o edición de módulos o sus secciones, la instancia invalida la
caché; una transacción revertida no la invalida. El navegador debe revalidar la
respuesta para no conservar durante cinco minutos un catálogo editado. Los cambios
directos en la base de datos o en otras instancias se reflejan al vencer el plazo.
La caché se pierde al reiniciar Render y no incluye cuentas, sesiones ni progreso de
estudiantes. Esta optimización reduce consultas repetidas, pero no evita el tiempo de
arranque de Render Free ni acelera todas las rutas de la API.

Cada módulo tiene **3 sesiones de teoría y 1 de práctica**, generadas automáticamente por un trigger al crear el módulo.

Flujo del estudiante:

1. Lee la **Teoría 1** y responde su pregunta de comprensión: con una respuesta correcta o incorrecta la sesión queda completada y se desbloquea la siguiente (si falla, se muestra la opción correcta); después la **3**.
2. Las sesiones completadas quedan bloqueadas: no se puede volver atrás ni saltar el orden.
3. Al completar las 3 teorías se desbloquea la **Práctica**, un cuestionario con las preguntas que definió el administrador.
4. La práctica tiene **un solo intento**. El backend la corrige (`aciertos / total × 100`), guarda la nota y bloquea todas las sesiones.
5. La nota del módulo es exactamente la nota de la práctica (las teorías no llevan nota).
6. El estado lo calcula la base de datos: `NOT_STARTED` → `IN_PROGRESS` → `COMPLETED` (nota ≥ `passing_score`, 70 por defecto) o `FAILED`.

El porcentaje de avance se calcula por sesiones completadas: cada una de las 4 sesiones vale lo mismo, así que al terminar las 3 teorías el módulo muestra **75 %** y al enviar la práctica llega al **100 %** (migración `V6`). Los pesos de las secciones (`20 / 20 / 20 / 40` por defecto) se conservan para la fórmula histórica de la nota, pero ya no afectan al avance. La nota final del módulo sale únicamente de la práctica.

El panel de administración muestra, por cada estudiante, la nota final de cada módulo y el **total acumulado** (suma de las notas de los módulos calificados sobre `módulos activos × 100`), además de la nota media y el avance.

Ejemplo: guardar una teoría (solo ADMIN):

```http
PUT /api/v1/modules/{moduleId}/sections/{sectionId}/content
Authorization: Bearer <token-admin>
Content-Type: application/json

{
  "title": "Correos maliciosos",
  "content": "Un correo malicioso suele incluir urgencia, enlaces acortados y remitentes desconocidos…",
  "questions": [
    {
      "prompt": "¿Cuál de estas señales es sospechosa?",
      "options": [
        { "text": "Un enlace acortado de un remitente desconocido", "correct": true },
        { "text": "El logotipo de la empresa", "correct": false },
        { "text": "Un saludo cordial", "correct": false },
        { "text": "La firma del remitente", "correct": false }
      ]
    }
  ]
}
```

Las teorías deben tener exactamente **1** pregunta; la práctica, entre **1 y 50**. Cada pregunta debe tener entre **2 y 6** opciones y exactamente una correcta.


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
│   ├── controller   # ModuleController, LearningController, ProgressController
│   ├── dto          # requests, respuestas, resúmenes y cuestionarios
│   ├── entity       # CourseModule, ModuleSection, SectionQuestion, QuestionOption, Student*Progress
│   ├── repository   # Spring Data JPA
│   └── service      # ModuleService (catálogo y progreso) y QuizService (flujo guiado)
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
- El limitador de autenticación responde con HTTP 429 y `Retry-After` en segundos al superar el límite por operación y correo/usuario o dirección de conexión. También se aplica a recuperación y cambio de contraseña. Es local a cada instancia: se reinicia al reiniciar el servicio; detrás de un proxy, `getRemoteAddr()` puede corresponder al proxy y agrupar usuarios. Comprueba la configuración de red antes de depender del límite por IP.
- OTP de 6 dígitos, 15 min de validez, máximo 5 intentos, un solo uso.
- Nunca se registran códigos OTP ni contraseñas en los logs.
