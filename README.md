# CyberAcademy ÔÇö Backend

API REST de la plataforma de ciberseguridad **CyberAcademy**. Gestiona registro de estudiantes con verificaci├│n por c├│digo OTP enviado al correo, autenticaci├│n con JWT y control de acceso por roles.

## Stack

| Tecnolog├¡a | Versi├│n |
|---|---|
| Java | 21+ |
| Spring Boot | 3.3.4 |
| Spring Security | 6.x (stateless, JWT) |
| Spring Data JPA / Hibernate | 6.5 |
| PostgreSQL (Supabase) | 17 |
| Flyway | migraciones versionadas |
| Argon2id (BouncyCastle) | hash de contrase├▒as |
| JavaMail (SMTP) | env├¡o de OTP |

## Requisitos

- JDK 21 o superior
- Maven 3.9+
- Proyecto en Supabase (PostgreSQL)
- Cuenta SMTP para el env├¡o de correos (Gmail con contrase├▒a de aplicaci├│n, Resend, etc.)

## Configuraci├│n

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

Ese archivo est├í en `.gitignore`. En producci├│n, usa variables de entorno:

| Variable | Descripci├│n |
|---|---|
| `SUPABASE_DB_URL` | URL JDBC completa |
| `SUPABASE_DB_USERNAME` | `postgres.<project-ref>` |
| `SUPABASE_DB_PASSWORD` | contrase├▒a de la BD |
| `JWT_SECRET` | secreto de firma de los tokens |
| `MAIL_HOST` / `MAIL_PORT` | servidor SMTP |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | credenciales SMTP |
| `MAIL_FROM` | remitente visible |
| `CORS_ALLOWED_ORIGINS` | or├¡genes permitidos, separados por comas |
| `SPRING_PROFILES_ACTIVE` | `prod` en producci├│n |
| `MAIL_TRANSPORT` | `gmail-api` para enviar por HTTPS; `smtp` por defecto |
| `GMAIL_CLIENT_ID` | ID del cliente OAuth de Google para Gmail API |
| `GMAIL_CLIENT_SECRET` | secreto del cliente OAuth de Google |
| `GMAIL_REFRESH_TOKEN` | token de actualizaci├│n autorizado con el permiso `gmail.send` |

### Secretos y archivos locales

- No subas contrase├▒as, claves, tokens JWT ni archivos `.env` al repositorio. `.env` y sus variantes (como `.env.local` y `.env.production`) est├ín ignorados por Git; tambi├®n lo est├í `application-local.yml`.
- Se permite `.env.example` ├║nicamente como plantilla, con nombres de variables y valores de ejemplo sin credenciales reales.
- Antes de cada commit, revisa `git status` y `git diff --cached`. No uses `git add -f` con archivos secretos. Si un secreto ya se subi├│, ignorarlo despu├®s no lo elimina del historial: avisa al equipo y rev├│calo o r├│talo.

## Flujo de trabajo con Git

Aplica estos pasos por separado en el repositorio donde trabajes:

- `master`: versi├│n estable; se actualiza mediante Pull Request desde `develop`.
- `develop`: integraci├│n y pruebas; recibe Pull Requests desde ramas de tareas.
- `feature/descripcion` y `fix/descripcion`: ramas temporales para funcionalidades y correcciones. Cada persona crea una por tarea a partir de `develop`.

Actualiza `develop` y crea tu rama (usa un nombre descriptivo y diferente para cada tarea):

```bash
git fetch origin
git switch develop
git pull --ff-only origin develop
git switch -c feature/nombre-de-la-tarea
# Para una correcci├│n: git switch -c fix/nombre-del-error
```

Despu├®s de implementar y probar el cambio:

```bash
git status
git add ruta/del/archivo
git diff --cached
git commit -m "feat: describir el cambio"
git push -u origin feature/nombre-de-la-tarea
```

En GitHub abre un Pull Request con **base `develop`** y como origen tu rama. Espera a que pase CI y solicita revisi├│n a un compa├▒ero antes de fusionar. Cuando el equipo haya probado `develop`, abre otro Pull Request con **base `master`** y origen `develop`. No subas directamente a las ramas protegidas. Las ramas `feature/*` y `fix/*` se crean cuando comienza cada tarea, no de antemano.

## Ejecutar

Para habilitar im├ígenes y video en las teor├¡as, aplica una sola vez `src/main/resources/db/migration/V9__theory_media.sql` en la base de datos del entorno correspondiente antes de arrancar esta versi├│n. Flyway est├í desactivado en la configuraci├│n actual, por lo que no se ejecuta autom├íticamente. La migraci├│n agrega tres columnas opcionales; no incluye enlaces ni archivos multimedia.

### Despliegue de prueba en Render

Para desplegar la protecci├│n de OTP, aplica primero `V10__protect_email_verification_codes.sql`
en la base de datos del entorno y despliega inmediatamente el backend nuevo. Los c├│digos
de verificaci├│n antiguos se invalidan: sus usuarios deber├ín solicitar un nuevo c├│digo.
La protecci├│n usa `JWT_SECRET` con separaci├│n de prop├│sito; cambiar ese secreto tambi├®n
invalida los c├│digos pendientes. Coordina una ventana de mantenimiento para evitar que
la versi├│n anterior genere c├│digos en claro entre la migraci├│n y el despliegue.

El registro requiere un frontend actualizado: primero se confirma el correo y se env├¡a
un c├│digo; la contrase├▒a definitiva se establece al verificar ese c├│digo. El backend
rechaza la verificaci├│n sin contrase├▒a. Las cuentas pendientes no verificadas caducan
a las 24 horas y se eliminan con sus OTP mediante una tarea que se ejecuta cada hora;
las cuentas pendientes anteriores tambi├®n est├ín sujetas a esta limpieza. La verificaci├│n
y el reenv├¡o se rechazan una vez vencido el plazo. Tras la eliminaci├│n se puede volver
a registrar el mismo correo. Despliega frontend y backend coordinadamente: el cliente
anterior no puede completar la verificaci├│n con el contrato nuevo.

El `Dockerfile` compila el backend con Java 21 y crea una imagen sin Maven. Crea un **Web Service** en Render conectado a este repositorio y selecciona **Docker**. El servicio escucha en el puerto 8080.

Configura en Render `SPRING_PROFILES_ACTIVE=prod`, `SUPABASE_DB_URL` (URL JDBC), `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD`, `JWT_SECRET` y `CORS_ALLOWED_ORIGINS`. Usa una base de datos de prueba y configura los secretos en Render, nunca en el repositorio ni en el Dockerfile. La base debe tener las migraciones necesarias aplicadas manualmente: Flyway est├í desactivado y Hibernate valida el esquema al arrancar.

Cuando el servicio est├® activo, comprueba `https://<servicio>.onrender.com/api/v1/public/modules`. En Render Free el servicio puede suspenderse tras inactividad y los puertos SMTP 465 y 587 est├ín bloqueados: el env├¡o por SMTP no funcionar├í con esos puertos. El plan gratuito es para pruebas, no garantiza capacidad para 100 usuarios.

Para enviar c├│digos desde Render Free, configura `MAIL_TRANSPORT=gmail-api`, las tres
variables OAuth `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET` y `GMAIL_REFRESH_TOKEN`, y
`MAIL_FROM` con la direcci├│n Gmail autorizada. El backend solicita un token de acceso
para cada env├¡o y utiliza la API de Gmail por HTTPS. Tambi├®n se utiliza para recuperaci├│n
de contrase├▒a. No registres ni compartas las credenciales; introd├║celas directamente
en las variables de entorno de Render. La entrega real depende de la autorizaci├│n y de
los l├¡mites de Google. En aplicaciones OAuth externas en modo de prueba, los refresh
tokens con el permiso Gmail pueden vencer a los siete d├¡as: esta configuraci├│n requiere
renovaci├│n y no ofrece env├¡o continuo sin resolver la publicaci├│n de OAuth.

### M├®tricas en Grafana Cloud

La exportaci├│n de m├®tricas t├®cnicas mediante Micrometer y OTLP/HTTP est├í **desactivada
por defecto**. No hace p├║blicos endpoints de Actuator. Para una prueba en Render Free:

1. En el portal de Grafana Cloud, abre tu stack y selecciona **OpenTelemetry ÔåÆ Configure**.
   Genera una credencial con permiso de escritura de m├®tricas y obt├®n el endpoint OTLP.
2. En las variables de entorno del servicio backend de Render configura:
   - `GRAFANA_METRICS_ENABLED=true`
   - `GRAFANA_OTLP_METRICS_URL`: URL OTLP de tu stack **incluyendo `/v1/metrics`**.
     Si Grafana muestra una URL base terminada en `/otlp`, a├▒ade `/v1/metrics`.
   - `GRAFANA_OTLP_AUTHORIZATION`: valor completo de la cabecera HTTP de autorizaci├│n,
     por ejemplo `Basic <credencial-codificada>`. Si el asistente muestra
     `Authorization=Basic%20...`, copia el valor posterior a `Authorization=` y
     reemplaza `%20` por un espacio. No copies el prefijo `Authorization=`.
3. Guarda y despliega el servicio. No incluyas estos valores en Git ni los compartas.
4. En Grafana, importa `observability/grafana-cyberacademy.json` desde **Dashboards ÔåÆ
   New ÔåÆ Import** y selecciona la fuente de datos Prometheus/Mimir de Grafana Cloud.
5. Genera tr├ífico normal y espera al menos dos intervalos de exportaci├│n (60 segundos
   cada uno) para ver las tasas. Las series 429/5xx aparecer├ín cuando ocurran esos estados.

La aplicaci├│n env├¡a m├®tricas agregadas (por ejemplo, m├®todos, rutas de API, estados,
latencia y memoria JVM), no el cuerpo de peticiones, contrase├▒as, c├│digos OTP ni tokens.
Esta configuraci├│n no env├¡a logs ni trazas. En Render Free, al suspenderse la instancia
no se exportan datos; un hueco en el panel no identifica por s├¡ solo la causa y las
m├®tricas en memoria se reinician. Consulta los l├¡mites vigentes de tu plan Grafana
Cloud. El panel es una plantilla inicial y sus consultas deben comprobarse con las
series que reciba tu stack.

```bash
mvn spring-boot:run
```

- API: `http://localhost:8080/api/v1`
- El perfil `local` se activa por defecto (lee `application-local.yml`)
- Flyway aplica las migraciones autom├íticamente al arrancar (`ddl-auto: validate`)

## Endpoints

Base: `/api/v1`

| M├®todo | Ruta | Auth | Descripci├│n |
|---|---|---|---|
| POST | `/auth/register` | ÔÇö | Registro de estudiante (rol STUDENT) y env├¡o de OTP |
| POST | `/auth/verify-otp` | ÔÇö | Verifica el c├│digo y activa la cuenta |
| POST | `/auth/resend-otp` | ÔÇö | Reenv├¡a un nuevo c├│digo |
| POST | `/auth/login` | ÔÇö | Login; devuelve JWT |
| GET | `/modules` | JWT | Cat├ílogo de m├│dulos activos con sus 4 secciones |
| GET | `/modules/{id}` | JWT | Detalle de un m├│dulo |
| POST | `/modules` | ADMIN | Crea un m├│dulo (la BD genera 3 teor├¡a + 1 pr├íctica) |
| PUT | `/modules/{id}` | ADMIN | Actualiza t├¡tulo, descripci├│n, orden, nota m├¡nima o estado |
| PUT | `/modules/{moduleId}/sections/{sectionId}` | ADMIN | Actualiza t├¡tulo y/o peso de una secci├│n |
| PUT | `/modules/{moduleId}/weights` | ADMIN | Actualiza los 4 pesos en una sola petici├│n (deben sumar 100) |
| GET | `/modules/{moduleId}/progress` | JWT | Mi progreso en ese m├│dulo, secci├│n por secci├│n |
| GET | `/modules/{moduleId}/sections/{sectionId}` | JWT | Sesi├│n desbloqueada: contenido y preguntas (sin respuestas) |
| POST | `/modules/{moduleId}/sections/{sectionId}/check` | JWT | Comprueba la pregunta de comprensi├│n de una teor├¡a |
| POST | `/modules/{moduleId}/practice/submit` | JWT | Corrige la pr├íctica (├║nico intento) y guarda la nota |
| GET | `/modules/{moduleId}/content` | ADMIN | Contenido y preguntas de las 4 secciones (con respuestas) |
| PUT | `/modules/{moduleId}/sections/{sectionId}/content` | ADMIN | Guarda t├¡tulo, contenido y preguntas de una sesi├│n |
| GET | `/progress` | JWT | Resumen de mi progreso en todos los m├│dulos |
| GET | `/progress/student/{userId}` | ADMIN | Resumen del progreso de un estudiante |
| GET | `/admin/overview` | ADMIN | M├®tricas globales: estudiantes, nota media, avance y finalizaci├│n por m├│dulo |
| GET | `/admin/students` | ADMIN | Estudiantes reales con m├│dulos completados, total acumulado, nota media y ├║ltima actividad (el detalle por m├│dulo sale de `/progress/student/{userId}`) |

## M├│dulos y progreso

El cat├ílogo p├║blico de `/public/modules` se guarda en una cach├® local acotada durante
un m├íximo de 60 segundos para evitar repetir las consultas a m├│dulos y secciones. Al
confirmar la creaci├│n o edici├│n de m├│dulos o sus secciones, la instancia invalida la
cach├®; una transacci├│n revertida no la invalida. El navegador debe revalidar la
respuesta para no conservar durante cinco minutos un cat├ílogo editado. Los cambios
directos en la base de datos o en otras instancias se reflejan al vencer el plazo.
La cach├® se pierde al reiniciar Render y no incluye cuentas, sesiones ni progreso de
estudiantes. Esta optimizaci├│n reduce consultas repetidas, pero no evita el tiempo de
arranque de Render Free ni acelera todas las rutas de la API.

Cada m├│dulo tiene **3 sesiones de teor├¡a y 1 de pr├íctica**, generadas autom├íticamente por un trigger al crear el m├│dulo.

Flujo del estudiante:

1. Lee la **Teor├¡a 1** y responde su pregunta de comprensi├│n: con una respuesta correcta o incorrecta la sesi├│n queda completada y se desbloquea la siguiente (si falla, se muestra la opci├│n correcta); despu├®s la **3**.
2. Las sesiones completadas quedan bloqueadas: no se puede volver atr├ís ni saltar el orden.
3. Al completar las 3 teor├¡as se desbloquea la **Pr├íctica**, un cuestionario con las preguntas que defini├│ el administrador.
4. La pr├íctica tiene **un solo intento**. El backend la corrige (`aciertos / total ├ù 100`), guarda la nota y bloquea todas las sesiones.
5. La nota del m├│dulo es exactamente la nota de la pr├íctica (las teor├¡as no llevan nota).
6. El estado lo calcula la base de datos: `NOT_STARTED` ÔåÆ `IN_PROGRESS` ÔåÆ `COMPLETED` (nota ÔëÑ `passing_score`, 70 por defecto) o `FAILED`.

El porcentaje de avance se calcula por sesiones completadas: cada una de las 4 sesiones vale lo mismo, as├¡ que al terminar las 3 teor├¡as el m├│dulo muestra **75 %** y al enviar la pr├íctica llega al **100 %** (migraci├│n `V6`). Los pesos de las secciones (`20 / 20 / 20 / 40` por defecto) se conservan para la f├│rmula hist├│rica de la nota, pero ya no afectan al avance. La nota final del m├│dulo sale ├║nicamente de la pr├íctica.

El panel de administraci├│n muestra, por cada estudiante, la nota final de cada m├│dulo y el **total acumulado** (suma de las notas de los m├│dulos calificados sobre `m├│dulos activos ├ù 100`), adem├ís de la nota media y el avance.

Ejemplo: guardar una teor├¡a (solo ADMIN):

```http
PUT /api/v1/modules/{moduleId}/sections/{sectionId}/content
Authorization: Bearer <token-admin>
Content-Type: application/json

{
  "title": "Correos maliciosos",
  "content": "Un correo malicioso suele incluir urgencia, enlaces acortados y remitentes desconocidosÔÇª",
  "questions": [
    {
      "prompt": "┬┐Cu├íl de estas se├▒ales es sospechosa?",
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

Las teor├¡as deben tener exactamente **1** pregunta; la pr├íctica, entre **1 y 50**. Cada pregunta debe tener entre **2 y 6** opciones y exactamente una correcta.


## Validaciones de correo

Antes de enviar un OTP se valida que el dominio tenga registros DNS (MX/A) y se detectan errores de escritura en proveedores comunes (`gmial.com` ÔåÆ sugiere `gmail.com`). Aun as├¡, la existencia de un buz├│n concreto no puede comprobarse sin enviar el correo.

## Estructura

```
src/main/java/com/cyberacademy
Ôö£ÔöÇÔöÇ auth
Ôöé   Ôö£ÔöÇÔöÇ controller   # endpoints
Ôöé   Ôö£ÔöÇÔöÇ dto          # request/response + ApiResponse
Ôöé   Ôö£ÔöÇÔöÇ entity       # User, EmailOtp y enums
Ôöé   Ôö£ÔöÇÔöÇ exception    # EmailDeliveryException
Ôöé   Ôö£ÔöÇÔöÇ repository   # Spring Data JPA
Ôöé   ÔööÔöÇÔöÇ service      # AuthService, EmailService, EmailAddressVerifier
Ôö£ÔöÇÔöÇ module
Ôöé   Ôö£ÔöÇÔöÇ controller   # ModuleController, LearningController, ProgressController
Ôöé   Ôö£ÔöÇÔöÇ dto          # requests, respuestas, res├║menes y cuestionarios
Ôöé   Ôö£ÔöÇÔöÇ entity       # CourseModule, ModuleSection, SectionQuestion, QuestionOption, Student*Progress
Ôöé   Ôö£ÔöÇÔöÇ repository   # Spring Data JPA
Ôöé   ÔööÔöÇÔöÇ service      # ModuleService (cat├ílogo y progreso) y QuizService (flujo guiado)
Ôö£ÔöÇÔöÇ admin
Ôöé   Ôö£ÔöÇÔöÇ controller   # AdminDashboardController (/admin/overview, /admin/students)
Ôöé   Ôö£ÔöÇÔöÇ dto          # estad├¡sticas y listado de estudiantes
Ôöé   ÔööÔöÇÔöÇ service      # AdminDashboardService (agregaciones SQL)
Ôö£ÔöÇÔöÇ common/exception # ResourceNotFound, Conflict, BadRequest
Ôö£ÔöÇÔöÇ config           # GlobalExceptionHandler
ÔööÔöÇÔöÇ security         # JWT, filtro, Argon2id, CORS, CurrentUserProvider
src/main/resources
Ôö£ÔöÇÔöÇ application.yml
ÔööÔöÇÔöÇ db/migration
    Ôö£ÔöÇÔöÇ V1__init_auth_schema.sql
    Ôö£ÔöÇÔöÇ V2__modules_and_progress.sql
    Ôö£ÔöÇÔöÇ V3__secure_modules_objects.sql
    ÔööÔöÇÔöÇ V4__recalculate_on_weight_change.sql
```

## Seguridad

- Contrase├▒as con **Argon2id** (par├ímetros OWASP: 64 MB, 3 iteraciones).
- JWT firmado con HMAC-SHA, expiraci├│n 60 min.
- Bloqueo temporal de cuenta tras 5 intentos fallidos.
- El limitador de autenticaci├│n responde con HTTP 429 y `Retry-After` en segundos al superar el l├¡mite por operaci├│n y correo/usuario o direcci├│n de conexi├│n. Tambi├®n se aplica a recuperaci├│n y cambio de contrase├▒a. Es local a cada instancia: se reinicia al reiniciar el servicio; detr├ís de un proxy, `getRemoteAddr()` puede corresponder al proxy y agrupar usuarios. Comprueba la configuraci├│n de red antes de depender del l├¡mite por IP.
- OTP de 6 d├¡gitos, 15 min de validez, m├íximo 5 intentos, un solo uso.
- Nunca se registran c├│digos OTP ni contrase├▒as en los logs.

### Rocu: conversación con Gemini

El frontend llama a `POST /api/v1/rocu/chat`. Configura `GEMINI_API_KEY` en el entorno del backend; `GEMINI_MODEL` es opcional (por defecto `gemini-3.1-flash-lite`). Sin clave, el resto de la API sigue funcionando y el chat devuelve 503. No se requiere migración.

Se envían el mensaje y hasta cuatro intercambios recientes a Google. La aplicación no persiste la conversación. Límites por instancia: 50 consultas por dirección de conexión y 200 en total cada 15 minutos; máximo seis consultas simultáneas. Una red compartida puede consumir el mismo límite. Despliega el backend antes del frontend y comprueba el flujo con una consulta ficticia, sin datos personales.
