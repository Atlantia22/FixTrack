# Análisis de seguridad — FixTrack

**Fecha:** 3 de noviembre de 2026
**Alcance:** backend `fixtrackback` (Spring Boot 4.1, Java 25), frontend `fixtrack-front` (React 19 + Vite) y su configuración.
**Versión anterior:** 31 de octubre de 2026 (en el historial de git). Este documento la reemplaza: verifica cada hallazgo contra el código, descarta los que no aplicaban y agrega los encontrados en esta revisión.

---

## 1. Resumen

| Estado | Cantidad |
|---|---|
| Corregidos en esta revisión | 10 |
| Aceptados o pendientes (riesgo bajo o de despliegue) | 8 |
| Descartados del informe anterior | 3 |

No se encontraron inyección SQL, XSS, endpoints internos sin control de rol ni accesos a órdenes de otro técnico (IDOR). El hallazgo más grave de esta revisión fue nuevo: `SampleDataInitializer` creaba por defecto usuarios con la contraseña fija `Password123*`. Ya está corregido.

Pruebas: 67 pruebas del backend en verde (6 nuevas, cada una verificada en rojo sin su corrección), `oxlint` sin advertencias, `vite build` correcto y `npm audit` sin vulnerabilidades.

---

## 2. Qué se revisó y cómo

| Área | Método | Resultado |
|---|---|---|
| Rutas y permisos | Se listaron los 101 endpoints con su `@PreAuthorize` | Todos los internos tienen regla de rol. Sin token solo quedan `POST /api/auth/login` y `/api/public/**` |
| Cadena de seguridad | Lectura de `SecurityConfig` y `JwtAuthFilter` | Sin estado, sin CSRF (no hay cookies), `anyRequest().denyAll()` fuera de `/api`, HS256 con emisor y expiración sin tolerancia |
| Pertenencia (IDOR) | Revisión de cada método con `isAuthenticated()` o `TECHNICIAN` | El técnico solo lee y trabaja sus órdenes (`findVisibleIntervention`, `requireRepairer`), sus repuestos usados, sus solicitudes, su disponibilidad y sus ganancias |
| Inyección SQL | Búsqueda de SQL nativo, `EntityManager`, concatenación en `@Query` y `Sort` dinámico; prueba real contra MySQL con `sort=fullName); DROP TABLE x;--` | No hay SQL nativo. Toda consulta usa parámetros. El `sort` malicioso lo rechaza Spring Data |
| Comodines de LIKE | Revisión de las 4 búsquedas de texto | 3 escapaban `!`, `%` y `_`; la de personas no (corregida) |
| Validación de entrada | `@RequestBody` sin `@Valid`; DTO con `@Size` y `@Pattern` | Todos los cuerpos llevan `@Valid`. Enums e ids tipados en la ruta |
| Datos sensibles | DTO de salida, logs y PDF | Sin hashes ni claves en las respuestas. Los logs no imprimen contraseñas ni tokens. El PDF del portal exponía datos de identidad (corregido) |
| Cifrado | `EncryptedStringConverter` | AES-256-GCM, IV aleatorio de 12 bytes con `SecureRandom` y llave de 32 bytes obligatoria |
| Frontend | `dangerouslySetInnerHTML`, `eval`, `href` dinámicos, almacenamiento, secretos | Sin XSS. Los `href` solo son `tel:` y `mailto:`. La sesión vive en `sessionStorage` y no hay secretos en el código |
| Dependencias | `npm audit --omit=dev` | 0 vulnerabilidades |

---

## 3. Corregidos en esta revisión

### 3.1 Usuarios con contraseña fija en los datos de ejemplo — Crítico (nuevo)
`SampleDataInitializer` estaba activo por defecto (`fixtrack.seed-data:true`). Sobre una base vacía creaba admin, coordinador, asesor y técnico con la contraseña `Password123*`, escrita en el código. Eso anulaba la protección de `InitialAdminInitializer` y `DemoUsersInitializer`, que no crean usuarios si falta la contraseña.

**Corrección:** queda apagado por defecto (`SEED_DATA=false`), usa `DEMO_USERS_PASSWORD` y, si está vacía, no carga nada.

> **Acción manual:** si alguna base se pobló con esos datos, cambie la contraseña de esas cuentas o desactívelas.

### 3.2 Login sin límite de intentos — Crítico
`POST /api/auth/login` no tenía ningún freno ante la fuerza bruta.

**Corrección:** `AuthServiceImpl.login` usa `IpAttemptLimiter`, con llaves `login:<ip>` separadas de las del portal. Con 10 fallos en 15 minutos responde 429 sin verificar la contraseña. A propósito no se bloquea la cuenta por correo: así un atacante no puede dejar al administrador sin acceso. Pruebas: `AuthServiceImplTest`.

### 3.3 Comprobante del portal con cédula, IMEI y contactos completos — Crítico (parcial)
Quien tuviera el código y la clave descargaba el PDF con todos los datos de identidad.

**Corrección:** cuando el comprobante sale del portal (`masked = true`), muestra `CC *****890`, `IMEI ***********3456`, `Celular ********67` y `a***@dominio.co`. El que se imprime en mostrador sigue completo. La consulta JSON del portal ya no exponía estos datos.

### 3.4 El ingreso de una orden podía cambiar el correo de un empleado — Alto
`registerOrUpdateCustomer` busca la persona por documento sin importar su rol. Si un empleado traía su equipo y el asesor escribía otro correo, ese cambio reemplazaba el correo con el que el empleado inicia sesión.

**Corrección:** si la persona es interna y el correo llega distinto, responde 400 («su correo solo lo cambia el administrador en Personal»). El celular sí se actualiza. Pruebas: `PersonServiceImplTest`.

### 3.5 Tokens válidos después de cambiar la contraseña — Alto
**Corrección:** `Person.passwordChangedAt`, truncado al segundo, se guarda al cambiar la contraseña propia o cuando la cambia el administrador. `JwtAuthFilter` rechaza los tokens cuyo `iat` sea anterior con «Su contraseña cambió; inicie sesión de nuevo». `PATCH /api/auth/password` devuelve un token nuevo y el front lo guarda, así que la sesión actual sigue y las demás se cierran. Prueba: `SecurityConfigTest.shouldRejectTokenIssuedBeforePasswordChange`.

### 3.6 Búsqueda de personas sin escapar los comodines — Bajo (nuevo)
`PersonRepository.search` armaba `LIKE CONCAT('%', :search, '%')` sin escapar nada: buscar `%` o `_` devolvía todos los registros. No era inyección, porque el texto llega como parámetro, pero incumplía la regla del proyecto.

**Corrección:** ahora el servicio escapa con `!` y la consulta usa `collate(... utf8mb4_0900_ai_ci) LIKE :pattern ESCAPE '!'`, igual que repuestos y proveedores. Además ya ignora las tildes.

### 3.7 Un `sort` inválido respondía 500 — Bajo (nuevo)
`?sort=noExiste` lanzaba `InvalidDataAccessApiUsageException`, que caía en el manejador genérico.

**Corrección:** `GlobalExceptionHandler` responde 400 «El orden o los filtros pedidos no son válidos».

### 3.8 Un parámetro mal codificado respondía 500 — Bajo (nuevo)
Al probar contra el servidor real, `?search=G%F3mez` (una «ó» en Latin-1 en vez de UTF-8) respondía 500: Tomcat lanza `InvalidParameterException` y caía en el manejador genérico. Los navegadores envían UTF-8, así que solo lo provoca un cliente mal hecho o malicioso.

**Corrección:** `GlobalExceptionHandler` responde 400 «La petición tiene caracteres no válidos». Prueba: `GlobalExceptionHandlerTest.shouldReturnBadRequestWhenParameterHasInvalidEncoding`.

### 3.9 Base de datos, CORS y esquema fijos en la configuración — Medio
**Corrección:** `DB_URL`, `DB_USERNAME`, `CORS_ALLOWED_ORIGINS` y `DDL_AUTO` llegan por entorno, con valores por defecto para desarrollo. `.env.example` ya no trae una contraseña de ejemplo.

### 3.10 Proxy inverso y `X-Forwarded-For` — Alto (corregida la recomendación)
El informe anterior recomendaba `forward-headers-strategy: framework`. Eso empeora el problema: Spring confiaría en cualquier `X-Forwarded-For`, y un atacante se saltaría el límite por IP cambiando la cabecera en cada intento. Hoy no hay proxy, así que no se activa nada. `application.yml` deja documentado que, detrás de un proxy, se usa `native`, porque Tomcat solo confía en las IPs privadas.

### Verificación de punta a punta (3 de noviembre de 2026)
Con el backend y el build del front corriendo contra MySQL local:

| Comprobación | Resultado |
|---|---|
| PDF de ingreso del portal frente al de mostrador (`FT-00006`) | Portal: `CC *******012`, `********44`, `v***@gmail.com`, `IMEI ***********3455`. Mostrador: completo |
| Login: 10 fallos desde la misma IP | Intento 10 → 429; con credenciales correctas también 429; el portal sigue respondiendo |
| Cambio de contraseña desde el formulario (Chrome headless) | La sesión queda con el token nuevo (200), el anterior responde 401 «Su contraseña cambió» y la contraseña vieja ya no inicia sesión |
| `?sort=noExiste` y `?sort=fullName);DROP TABLE x;--` | 400 |
| Búsqueda de personas `%`, `_`, «Gómez», «gomez», «GOMEZ» | 0, 0, 2, 2, 2 de 11 |
| Login, portal y 8 pantallas internas con carga diferida | Se muestran con su título y el menú lateral, sin errores en la consola |

---

## 4. Aceptados o pendientes

| # | Riesgo | Nivel | Decisión |
|---|---|---|---|
| 4.1 | La clave de consulta son los últimos 4 dígitos del celular | Medio | **Regla de negocio** (`fixtrack.md`). La mitigan el bloqueo por código (5 fallos → 15 min, en la base de datos), el límite por IP y el PDF enmascarado. Probar las 10.000 claves de una orden toma semanas. Cambiarla a siempre aleatoria es una decisión del negocio |
| 4.2 | Los listados aceptan ordenar por cualquier campo de la entidad (por ejemplo `sort=password`) | Bajo | Sin fuga práctica: ordenar por un hash bcrypt o un texto cifrado no revela nada, y los campos inválidos ya responden 400. Para cumplir la regla del «mapa de campos permitidos», llevar a todos los listados el `toSort` que ya usa `SparePartServiceImpl` |
| 4.3 | `IpAttemptLimiter` vive en memoria | Bajo | Válido mientras haya una sola instancia. Con varias, pasarlo a Redis o a una tabla |
| 4.4 | `ddl-auto: update` por defecto | Medio | Válido en desarrollo. En producción, `DDL_AUTO=validate`; Flyway, si el esquema empieza a cambiar con datos reales |
| 4.5 | Sin HTTPS, CSP ni cabeceras de seguridad en el front | Medio (despliegue) | La API ya envía las cabeceras por defecto de Spring Security. Al desplegar: TLS en el proxy, `Content-Security-Policy`, `Strict-Transport-Security` y límite de tamaño del cuerpo |
| 4.6 | Token en `sessionStorage` | Bajo | No hay vectores de XSS. Una CSP (4.5) lo refuerza |
| 4.7 | El portal bloquea la fila de la orden (`PESSIMISTIC_WRITE`) en cada consulta | Bajo | Necesario para contar bien los fallos. Muchas consultas a un mismo código solo hacen esperar a esa orden, y el límite por IP las frena |
| 4.8 | `PATCH /api/auth/password` no limita los intentos con la contraseña actual | Bajo | Exige un token válido. Si se quiere, aplicar la misma llave por usuario en `IpAttemptLimiter` |

---

## 5. Descartados del informe anterior

| Hallazgo | Motivo |
|---|---|
| «Spring Boot 4 / Java 25 son versiones experimentales» | Falso: Spring Boot 4 salió en noviembre de 2025 y Java 25 es LTS |
| Migrar el Excel a `SXSSFWorkbook` | El informe cubre como máximo 366 días de órdenes entregadas de una tienda, unos miles de filas que `XSSFWorkbook` maneja sin problema |
| Exigir mayúsculas, números y símbolos en las contraseñas | NIST SP 800-63B desaconseja las reglas de composición. Si se endurece, subir la longitud mínima a 10–12 caracteres |

---

## 6. Puntos fuertes confirmados

- Todo endpoint interno tiene `@PreAuthorize` y la cadena termina en `denyAll()`.
- Pertenencia validada en el servicio, no solo ocultando botones en el front.
- El token se revalida en cada petición: un usuario desactivado, con otro rol o con la contraseña cambiada queda fuera de inmediato.
- Login sin enumeración de usuarios y portal con hash ficticio para que el tiempo de respuesta no delate qué códigos existen.
- La contraseña de desbloqueo va con AES-GCM y se borra al entregar o cancelar. La clave de consulta y las contraseñas se guardan con bcrypt.
- `ProblemDetail` sin trazas ni SQL. Jackson rechaza decimales en enteros y todos los cuerpos pasan por Bean Validation.
- Stock protegido con `@Version` y bloqueo pesimista en las solicitudes de repuestos.
