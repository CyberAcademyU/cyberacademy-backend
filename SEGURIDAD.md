# Seguridad de la sesión — CyberAcademy

Este documento describe el esquema de autenticación implementado, lo que hay que
configurar antes de desplegar y cómo comprobarlo en producción.

## 1. Cómo funciona la sesión

- El navegador **nunca recibe un token en el cuerpo JSON** ni lo guarda en
  `localStorage`/`sessionStorage`.
- `POST /api/v1/auth/login` responde con los datos del usuario y dos cookies
  `HttpOnly`:
  - `ca_access` — 10 minutos, `Path=/`.
  - `ca_refresh` — 7 días, `Path=/api/v1/auth/refresh` (solo se envía a esa ruta).
- Ambas cookies son `HttpOnly`, `Secure`, `SameSite=Strict`, sin `Domain`, y las
  respuestas de sesión llevan `Cache-Control: no-store`.
- Los tokens son **opacos**: 32 bytes aleatorios en Base64URL. En PostgreSQL solo
  se guarda su SHA-256 (`auth_session_tokens`), nunca el valor utilizable.
- **Rotación con detección de reuso**: cada `POST /api/v1/auth/refresh` marca el
  refresh anterior como usado y emite un par nuevo dentro de la misma familia.
  Si se presenta un refresh ya usado, se revoca la familia completa: el access
  vigente y cualquier refresh posterior dejan de servir.
- El servidor comprueba en cada petición que el usuario siga `ACTIVE`,
  verificado y que la sesión sea posterior a `password_changed_at`.
- `POST /api/v1/auth/refresh/logout` revoca la familia usando el refresh (aunque
  el access ya haya caducado) y borra ambas cookies con `Max-Age=0`.
- El filtro de autenticación acepta el token **solo** desde `request.getCookies()`.
  Un `Authorization: Bearer` o `?token=` se ignoran por completo.

Endpoints nuevos:

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/v1/auth/me` | Restaura la sesión al cargar la SPA |
| POST | `/api/v1/auth/refresh` | Renueva el par de cookies (la cookie viaja sola) |
| POST | `/api/v1/auth/refresh/logout` | Cierra sesión usando el refresh |
| GET | `/api/v1/auth/encryption-key` | Clave pública ECDH para cifrar la contraseña |

## 2. Defensas adicionales

- **CSRF:** además de `SameSite=Strict`, `OriginCsrfFilter` exige, en todo método
  que no sea `GET/HEAD/OPTIONS`, que `Origin` coincida exactamente con
  `CORS_ALLOWED_ORIGINS` y que exista `X-Requested-With: CyberAcademy`. Un
  formulario cross-site no puede añadir esa cabecera.
- **CORS:** orígenes exactos, `allowCredentials(true)`, métodos y cabeceras
  restringidos; nunca `*`.
- **Cabeceras del backend:** `X-Frame-Options: DENY`, `X-Content-Type-Options:
  nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`,
  `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`,
  `X-XSS-Protection: 0` (obsoleto e inseguro; lo sustituye la CSP) y HSTS en HTTPS.
- **Rate limiting:** sigue activo en `login`, `register`, `resend-otp`,
  `password-reset` y ahora también en `refresh`.
- **Errores:** el manejador global devuelve mensajes genéricos sin stack traces.

## 3. Antes de desplegar

1. **Aplicar la migración** `src/main/resources/db/migration/V11__opaque_cookie_sessions.sql`
   en Supabase (Flyway está desactivado). Es aditiva y segura de ejecutar primero.
2. En el servicio **backend** de Render, revisar:
   ```env
   CORS_ALLOWED_ORIGINS=https://migueltalavera.site,https://www.migueltalavera.site,https://cyberacademy-frontend.onrender.com,http://localhost:4200
   # SESSION_COOKIE_SECURE=true es el valor por defecto; solo se pone false en local.
   # Opcional: exige que la contraseña llegue cifrada. Actívalo cuando el frontend
   # nuevo ya esté desplegado; mientras sea false se acepta el login antiguo.
   PASSWORD_TRANSPORT_REQUIRE_ENCRYPTION=false
   # Opcional: secreto del que se deriva la clave ECDH. Si no se define, usa JWT_SECRET.
   # PASSWORD_TRANSPORT_SECRET=...
   ```
   No hace falta una variable nueva para que funcione: por defecto reutiliza
   `JWT_SECRET`. Cambiar `PASSWORD_TRANSPORT_SECRET` (o `JWT_SECRET`) solo obliga a
   recargar la pestaña que tuviera la clave anterior.
   `https://localhost` y `capacitor://localhost` dejan de ser necesarios: la app
   Android ahora usa el origen `https://migueltalavera.site` (mismo sitio que la API,
   requisito de `SameSite=Strict`).
3. En el **sitio estático** de Render, añadir en *Headers* (no en el repositorio):
   | Path | Header | Value |
   |---|---|---|
   | `/*` | `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' https: data:; font-src 'self'; connect-src 'self' https://backend.migueltalavera.site; frame-src https://www.youtube-nocookie.com; media-src https:; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; trusted-types angular angular#unsafe-bypass; require-trusted-types-for 'script'` |
   | `/*` | `X-Frame-Options` | `DENY` |
   | `/*` | `X-Content-Type-Options` | `nosniff` |
   | `/*` | `Referrer-Policy` | `strict-origin-when-cross-origin` |
   | `/*` | `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()` |
   | `/*` | `Cross-Origin-Opener-Policy` | `same-origin` |
   | `/*` | `Cross-Origin-Resource-Policy` | `same-site` |
   | `/*` | `X-Permitted-Cross-Domain-Policies` | `none` |
   | `/*` | `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` |

   `require-trusted-types-for 'script'` obliga a que todo `innerHTML`/`script` pase por
   una Trusted Types policy. La compilación de Angular crea exactamente dos:
   `angular` y `angular#unsafe-bypass` (esta última para el iframe validado de
   YouTube). Si al abrir la consola aparece un error indicando otra policy
   bloqueada, añade su nombre a la lista `trusted-types`.

   La CSP es la defensa principal contra XSS: aunque un atacante lograra inyectar
   HTML, no puede ejecutar scripts inline ni cargar código de otros orígenes.

   La compilación de producción ya desactiva el CSS crítico incrustado
   (`inlineCritical: false`) para que la CSP pueda ser estricta sin
   `'unsafe-inline'`. Si algún día se vuelve a activar, habrá que permitir
   `style-src 'unsafe-inline'` y `script-src 'unsafe-inline'`.
4. **App Android:** ejecutar `npm run cap:sync` y recompilar el APK. El WebView
   ahora se identifica como `https://migueltalavera.site` para que las cookies
   `SameSite=Strict` viajen a la API. Antes de activar
   `PASSWORD_TRANSPORT_REQUIRE_ENCRYPTION=true`, inicia sesión con el APK nuevo:
   necesita `crypto.subtle` (WebCrypto); si faltara, la app enviaría texto plano
   y el flag en `true` lo rechazaría.
5. Desplegar backend y frontend. Cada sesión abierta con el esquema anterior deja
   de valer: los usuarios tendrán que iniciar sesión una vez.

## 4. Lista de verificación en producción

```bash
API=https://backend.migueltalavera.site/api/v1
WEB=https://migueltalavera.site
```

- [ ] `curl -i -X POST $API/auth/login -H "Origin: $WEB" -H "X-Requested-With: CyberAcademy" -H "Content-Type: application/json" -d '{"email":"...","password":"..."}'`
      → `200`, dos `Set-Cookie` con `HttpOnly; Secure; SameSite=Strict` y respuesta **sin** `token`.
- [ ] La misma petición sin `X-Requested-With` → `403`.
- [ ] Con `Origin: https://evil.example` → `403` y sin `Access-Control-Allow-Origin`.
- [ ] `curl -i -X OPTIONS $API/auth/login -H "Origin: $WEB" -H "Access-Control-Request-Method: POST" -H "Access-Control-Request-Headers: content-type,x-requested-with"`
      → `200` con `Access-Control-Allow-Credentials: true`.
- [ ] `GET $API/auth/me` con la cookie `ca_access` → `200` con el usuario.
- [ ] `POST $API/auth/refresh` con la cookie `ca_refresh` → `200` y cookies nuevas.
- [ ] Repetir el refresh con la cookie **anterior** → `401`, y el access nuevo también deja de valer.
- [ ] `POST $API/auth/refresh/logout` → cookies con `Max-Age=0`; otro refresh → `401`.
- [ ] En DevTools → Application: `localStorage` y `sessionStorage` sin tokens;
      las cookies aparecen marcadas como `HttpOnly` y no son legibles desde la consola.
- [ ] Recargar `/dashboard/student` estando dentro → la sesión se restaura sin volver al login.
- [ ] Panel de administración accesible solo con rol ADMIN real.
- [ ] En la pestaña Network, la respuesta de `/auth/login` no incluye ningún token.
- [ ] `GET $API/auth/encryption-key` → `200` con `keyId`, `algorithm` y `publicKey`
      (Base64 de un punto P-256 sin comprimir de 65 bytes; ~88 caracteres).
- [ ] En DevTools → Network → `login`: el cuerpo contiene `sealedPassword` y **no**
      contiene `password`; el valor de `data` no contiene el texto introducido.
- [ ] Repetir el login enviando `password` en claro: con
      `PASSWORD_TRANSPORT_REQUIRE_ENCRYPTION=false` funciona (compatibilidad) y con
      `true` devuelve `400` pidiendo recargar.
- [ ] Registro + OTP con Resend siguen funcionando.

## 5. Límites conocidos

- `HttpOnly` impide robar el token con JavaScript, pero un XSS en código propio
  podría actuar como el usuario mientras dure la sesión. La CSP estricta con
  Trusted Types, el saneado de Angular y la rotación de 10 minutos reducen la
  superficie; ningún control elimina al 100 % un XSS en primera persona.
- El rate limiting es local a la instancia (Caffeine). Con varias instancias haría
  falta Redis/Bucket4j compartido.
- `JWT_SECRET` y la tabla `revoked_tokens` se conservan por compatibilidad, pero
  ya no autentican ninguna petición.

## 6. Cifrado de la contraseña en el navegador

La contraseña sigue existiendo y el backend la compara con Argon2id, pero el
navegador ya no la envía en claro. Antes de cada envío:

1. La SPA pide `GET /api/v1/auth/encryption-key` y recibe
   `{ keyId, algorithm, publicKey }`. El par es **ECDH P-256** y se deriva de
   forma determinista de `PASSWORD_TRANSPORT_SECRET` (por defecto `JWT_SECRET`),
   así que no cambia al reiniciar ni entre instancias. La SPA la cachea en memoria.
2. Genera un par ECDH P-256 **efímero**, acuerda el secreto con la clave pública
   del servidor y deriva una clave AES-256 con HKDF-SHA256
   (`salt = cyberacademy-password-salt-v1`, `info = cyberacademy-password-v1`).
3. Cifra con AES-256-GCM (IV de 12 bytes) y envía
   `sealedPassword: { keyId, clientPublicKey, data }`, donde
   `data = Base64(IV || ciphertext || tag)`. El campo `password` ya no viaja.

Se aplica a **todos** los flujos con contraseña:

| Flujo | Campo en claro (clientes antiguos) | Campo cifrado |
|---|---|---|
| `POST /auth/login` | `password` | `sealedPassword` |
| `POST /auth/verify-otp` | `password` | `sealedPassword` |
| `POST /auth/password-reset/confirm` | `newPassword` | `sealedNewPassword` |
| `PUT /profile/password` | `currentPassword`, `newPassword` | `sealedCurrentPassword`, `sealedNewPassword` |

Compatibilidad y despliegue:

- Por defecto `PASSWORD_TRANSPORT_REQUIRE_ENCRYPTION=false`: se acepta texto plano
  para no romper una pestaña antigua abierta durante el despliegue.
- Cuando el frontend nuevo esté publicado, ponlo en `true`: un cuerpo sin sobre
  cifrado recibe `400` con un mensaje que pide recargar.
- La clave del servidor cambia solo si cambias `PASSWORD_TRANSPORT_SECRET` o
  `JWT_SECRET`. Una pestaña con la clave anterior recibe `400` y, tras recargar,
  vuelve a funcionar.
- Si el navegador no expone `crypto.subtle` (contexto no seguro), la SPA envía
  texto plano y el backend decide según el flag. En producción todo es HTTPS.
- El servidor valida que la contraseña descifrada tenga entre 8 y 128 caracteres
  y rechaza un GCM manipulado, una clave desconocida o un sobre mal formado.

### Qué protege y qué no

- **Sí:** evita que la contraseña quede visible en el inspector de red, en una
  captura de pantalla o en un proxy que registre cuerpos. El texto cifrado tampoco
  es reutilizable: cada envío usa un par ECDH efímero distinto.
- **No:** no protege frente a XSS ni malware. Quien ejecuta código en la página
  puede leer la contraseña antes de cifrarla, y TLS ya protege el tránsito. Es
  ofuscación del payload, no una defensa criptográfica completa.
- Para que la contraseña no sea recuperable ni siquiera con código en la página
  haría falta **PAKE** (OPAQUE o SRP-6a) o **passkeys/WebAuthn**. Ninguna está
  implementada: WebAuthn no está soportado en el WebView de Android y PAKE exige
  un protocolo nuevo en cliente y servidor con una librería auditada.


