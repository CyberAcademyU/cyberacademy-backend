# Sesión única e inactividad

## Publicación obligatoria

Flyway está **desactivado**. Aplicar manualmente
`src/main/resources/db/migration/V15__single_session_and_idle_timeout.sql`.

Coordinar una ventana breve de mantenimiento: aplicar V15 y publicar el backend
nuevo antes de reabrir los inicios de sesión, y después publicar/recargar el frontend.
El backend anterior no implementa la revocación previa y puede chocar con el nuevo
índice único. Un frontend antiguo no comunica interacción y terminará expirando:
los usuarios con una pestaña antigua deben recargar.

V15 añade la última actividad, mantiene solo la familia no revocada más reciente
de cada cuenta y crea un índice único parcial. No borra usuarios, respuestas,
calificaciones ni progreso. Las sesiones conservadas reciben un plazo inicial de
15 minutos al aplicar la migración, porque no existía historial de interacción.
El cambio afecta a estudiantes y administradores, Google y contraseña.

## Reglas

- Solo una familia no revocada por cuenta. El login bloquea la fila del usuario,
  revoca las familias anteriores y crea una nueva dentro de la misma transacción.
  Esto funciona entre instancias y con logins simultáneos; no depende de memoria.
- La sesión anterior pierde acceso en la próxima comprobación del backend.
  La interfaz verifica cada 30 segundos mientras está visible y al regresar del
  segundo plano. No se muestra aviso de reemplazo ni cuenta regresiva.
- Varias pestañas del mismo navegador comparten cookies y la misma sesión.
  Otro navegador, perfil, incógnito o dispositivo crea una sesión distinta.
- Límite de **15 minutos desde la última interacción comunicada**. Autenticación,
  refresh, consultas de mensajes y heartbeats SSE no actualizan la actividad.
  Una familia vencida nunca se reactiva mediante refresh ni actividad.
- Se mantienen los tokens de acceso de 10 minutos y el límite absoluto de 7 días.
  No son 7 días nuevos después de cada renovación.
- Cambiar contraseña, suspender la cuenta, logout y detección de refresh reutilizado
  mantienen las invalidaciones existentes.

## API

- `GET /session`: requiere cookie de acceso válida; devuelve `sessionId`, `userId`
  e `idleRemainingMillis`, sin registrar actividad.
- `POST /session/activity`: requiere autenticación y las mismas protecciones CSRF
  (Origin y X-Requested-With). Cuerpo `{ "ageMillis": 0 }` con edad de la interacción
  entre 0 y 60000 ms; usa el token autenticado, nunca IDs de usuario del cuerpo.
  La edad evita contar como actividad nueva un evento que se envió con retraso.
  Se aplica tiempo del servidor, fecha monotónica y condición de no vencimiento.
- `POST /auth/refresh/logout` admite `{ "sessionId": "UUID" }` para no revocar ni
  borrar cookies de una nueva sesión debido a un cierre antiguo. El UUID no es una
  credencial: solo se revoca si coincide con la familia de la cookie presentada.
  Logout sin cuerpo sigue siendo compatible con los clientes existentes.

La SPA solo comunica eventos de interacción confiables del navegador; esto no
demuestra presencia humana frente a un cliente manipulado que ya posea cookies.
Las peticiones recibidas antes de una revocación no se pueden deshacer. El control
se aplica a nuevas comprobaciones; el SSE existente revalida antes de emitir datos.

## Pruebas

Pruebas H2 y HTTP: reemplazo de sesión, concurrencia, plazo de inactividad,
renovación que no alarga el plazo, no reactivación, CSRF y logout tardío.
`SessionPolicyPostgresTest` ejecuta V11 y V15 reales en la base desechable local
de CI y valida índice único, consolidación, concurrencia y expiración.
Nunca apuntar estas pruebas a una base con datos reales; contienen borrados de
tablas y solo admiten `messaging_test` en localhost.

Probar tras publicar con dos dispositivos: iniciar sesión en ambos, verificar
rechazo del primero; comprobar actividad entre pestañas, 15 minutos sin tocar
la interfaz, suspensión del equipo y práctica con respuestas sin enviar.
Las respuestas sin enviar no se guardan ni se envían automáticamente al cerrar.
