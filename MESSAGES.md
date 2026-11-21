# Mensajes administrativos

## Publicación

1. Aplicar manualmente `src/main/resources/db/migration/V14__student_messages.sql`
   a la base correcta. Flyway sigue desactivado. No aplicar el SQL en otra base.
2. Publicar backend y después frontend. No activar el frontend antes de crear las tablas.
3. Prueba manual con un administrador y al menos dos estudiantes, incluidos uno no
   seleccionado y un administrador adicional. No enviar mensajes reales de prueba
   a todos los usuarios sin confirmar antes los destinatarios.

## Contrato y seguridad

- `POST /admin/messages`: solo ADMIN. Texto plano no vacío, máximo 2000 caracteres;
  destinatarios `ALL` (lista vacía) o `SELECTED` (1-500 UUID únicos). Solo son elegibles
  estudiantes ACTIVE con correo verificado. Una selección inválida se rechaza completa.
  Remitente y nombre/apellido se obtienen del usuario autenticado, no del cuerpo.
- Todos significa los estudiantes elegibles **en el momento del envío**, no futuros registros.
  El mensaje se almacena una vez, con una copia del nombre del remitente; cada estudiante
  tiene su propio recibo. Administradores nunca reciben recibos.
- Identificador UUID de petición y hash de contenido/destinatarios hacen que reintentos
  idénticos no dupliquen el mensaje; reutilizar el UUID con contenido diferente devuelve 409.
  Inserción, entrega y contador son transaccionales. El limitador permite 5 envíos por
  administrador cada 15 minutos y aplica también el límite IP existente.
- `GET /messages?before=ID`: solo STUDENT, paginación por cursor de 20 mensajes.
- `POST /messages/notifications/claim`: lote máximo 10; claim atómico en PostgreSQL
  con SKIP LOCKED. Dos pestañas/instancias no reclaman el mismo aviso. No marca leído.
- `PUT /messages/{id}/read`, `DELETE /messages/{id}`, `DELETE /messages`:
  usan siempre el ID del estudiante de la sesión. No aceptan un studentId manipulable.
  Mensajes inexistentes o ajenos responden 404; vaciar solo borra recibos propios.
  El contenido compartido y registro de envío permanecen para los demás destinatarios
  e idempotencia. El autor no puede retirar mensajes ya enviados.
- Cookies HttpOnly, Origin exacto y X-Requested-With siguen obligatorios.
  Hay control de rol en rutas, anotaciones y servicio. SQL parametrizado y texto sin HTML.

## Avisos y bandeja

Una única instancia por pestaña consulta cada 15 segundos, solo en sesión STUDENT
y pestaña visible. Cola acotada; avisos de 3 segundos, pausados al ocultar la pestaña.
Se limpia memoria y temporizadores al salir/cambiar de usuario. Son avisos dentro
de la web, no notificaciones push del sistema ni correos.
El aviso se considera entregado al reclamarlo: si la pestaña se cierra justo después,
no se repite, pero el mensaje continúa sin leer en la bandeja. Los errores de consulta
no generan bucles ni toasts de error. El contador se actualiza al leer/eliminar/vaciar.

## Verificación

Pruebas de servicio y HTTP cubren permisos, CSRF, manipulación de IDs y validación.
`MessagePostgresTest` usa PostgreSQL real en CI: migración, entrega elegible,
aislamiento de borrado, rollback, idempotencia y claims concurrentes.
En local esas 4 pruebas se omiten si no existe `MESSAGING_TEST_DATABASE_URL`.
Solo admiten una base desechable llamada `messaging_test` en localhost, jamás producción;
crean y borran sus tablas de prueba. CI usa PostgreSQL efímero sin secretos de producción.
