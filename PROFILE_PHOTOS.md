# Fotos de perfil

Antes de desplegar este backend, aplicar manualmente
`src/main/resources/db/migration/V13__profile_photos.sql` en la base correcta.
Flyway está desactivado. Después desplegar backend y finalmente frontend.
No hacen falta buckets públicos, claves externas ni cambios de las funciones SQL existentes.

- La tabla guarda una miniatura WebP de 256×256 por usuario (máximo 256 KiB).
  Reemplazarla elimina la foto anterior; borrar el usuario elimina su foto.
- La subida acepta contenido PNG/JPEG de hasta 2 MiB, 16 megapíxeles y 6000 px
  por lado; valida el contenido decodificado, no la extensión o MIME declarados.
  Recorta al centro y vuelve a codificar en WebP (calidad 82/100, con pérdida)
  sin EXIF, GPS ni otros metadatos. Calidad visual alta no significa conservación
  exacta de todos los píxeles; el ahorro real depende de cada imagen.
  No se conservan los archivos originales ni sus nombres. HEIC, SVG y GIF no se admiten.
- Solo dos conversiones simultáneas; límite de operaciones por usuario y
  límites multipart del servidor. Las escrituras conservan sesión HttpOnly y
  protección Origin/X-Requested-With. El servidor infiere al propietario desde la sesión.
- Las URLs versionadas son privadas: propietario, administrador que consulta
  estudiantes o estudiante que consulta otro estudiante activo, verificado y
  visible en clasificación. No se publican fotos de administradores ni de
  estudiantes ocultos a otros estudiantes. Cada lectura vuelve a validar permisos,
  con `Cache-Control: no-store` y `X-Content-Type-Options: nosniff`.
- Elegir foto borra el avatar seleccionado; elegir avatar borra la foto.
  El catálogo de avatares se conserva para STUDENT; ADMIN solo foto o ninguna.
- El listado administrativo y la clasificación obtienen metadatos de imágenes
  por lote; los bytes se sirven separadamente, no dentro de respuestas JSON.

Prueba manual tras publicar: subir y reemplazar una foto con ambos roles,
quitarla, volver a un avatar como estudiante, comprobar listado/detalle admin,
clasificación y ocultar un estudiante. Una sesión estudiante no debe poder
leer la URL de la foto de un estudiante oculto ni la de un administrador.
En localhost contra producción las cookies SameSite=Strict no viajan; probar
ambos servicios localmente o ambos en el sitio desplegado.

## Conversión a WebP

- No requiere otra migración después de V13 ni cambios de frontend: se sirve
  `image/webp` en las mismas URLs. Los archivos originales de subida siguen siendo JPG/PNG.
- Las miniaturas JPEG anteriores se convierten progresivamente en su primera lectura
  autorizada y se guardan en WebP, sin cambiar la URL. Si el conversor está ocupado,
  se sirve el JPEG correcto y la conversión se intenta en una lectura posterior.
  No hay un proceso masivo al arrancar ni conversión en cada lectura.
- La actualización condicionada por versión no resucita una foto borrada ni
  sobrescribe una foto recién reemplazada. Los permisos se comprueban antes de convertir.
- Se mantienen `no-store`, límites de tamaño, rate limit y dos conversiones simultáneas.
  No se añaden hilos nativos por imagen. La conversión reduce almacenamiento y descarga,
  no el tamaño del archivo original que envía el navegador.
- WebP4j 2.5.0 (libwebp 1.6.0) incluye los binarios nativos para Java 21 en Windows/Linux
  x64 y ARM64. El backend comprueba su disponibilidad al iniciar; no se instala ningún
  ejecutable externo. Las pruebas de CI deben pasar también en Linux antes de desplegar.
