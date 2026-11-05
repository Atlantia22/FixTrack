# Análisis de seguridad — FixTrack

**Fecha:** 3 de noviembre de 2026, actualizado el 5 de noviembre de 2026
**Alcance:** backend `fixtrackback` (Spring Boot 4.1, Java 25), frontend `fixtrack-front` (React 19 + Vite) y su configuración.
**Versión anterior:** 31 de octubre de 2026 (en el historial de git). Este documento la reemplaza: verifica cada hallazgo contra el código, descarta los que no aplicaban y agrega los encontrados en esta revisión.
**Actualización del 5 de noviembre:** revisión del canal en tiempo real (WebSocket) y de los cambios funcionales de esos días (sección 7), y reducción de los riesgos pendientes con sus pruebas (sección 8).

---

## 1. Resumen

| Estado | Cantidad |
|---|---|
| Corregidos en la revisión del 3 de noviembre | 10 |
| Riesgos evitados en el diseño del canal en tiempo real (5 de noviembre) | 5 |
| Pendientes mitigados el 5 de noviembre (4.2, 4.8, 4.9, 4.10) y reducidos (4.5, 4.6) | 6 |
| Aceptados que quedan (negocio, una sola instancia o despliegue) | 6 |
| Descartados del informe anterior | 3 |

No se encontraron inyección SQL, XSS, endpoints internos sin control de rol ni accesos a órdenes de otro técnico (IDOR). El hallazgo más grave de esta revisión fue nuevo: `SampleDataInitializer` creaba por defecto usuarios con la contraseña fija `Password123*`. Ya está corregido.

Pruebas (3 de noviembre): 67 pruebas del backend en verde (6 nuevas, cada una verificada en rojo sin su corrección), `oxlint` sin advertencias, `vite build` correcto y `npm audit` sin vulnerabilidades.

Pruebas (5 de noviembre): 108 pruebas del backend en verde, 11 del front, `oxlint` sin advertencias, `vite build` correcto, `npm audit` sin vulnerabilidades y una batería de 23 verificaciones de seguridad contra el servidor en marcha, todas correctas (sección 8.2).

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
| 4.2 | ~~Los listados aceptan ordenar por cualquier campo de la entidad (por ejemplo `sort=password`)~~ | — | **Mitigado (5 de noviembre, sección 8).** Todos los listados paginados pasan por `SortWhitelist` con su mapa de campos permitidos |
| 4.3 | `IpAttemptLimiter` vive en memoria | Bajo | Válido mientras haya una sola instancia. Con varias, pasarlo a Redis o a una tabla |
| 4.4 | `ddl-auto: update` por defecto | Medio | Válido en desarrollo. En producción, `DDL_AUTO=validate`; Flyway, si el esquema empieza a cambiar con datos reales |
| 4.5 | Sin HTTPS ni cabeceras del servidor web del front | Bajo (despliegue) | **Reducido (5 de noviembre, sección 8):** la API envía CSP, `Referrer-Policy` y `Permissions-Policy`, y el build del front lleva su propia CSP. Queda para el despliegue: TLS en el proxy (con él, Spring envía `Strict-Transport-Security`), `frame-ancestors` en las cabeceras del servidor que sirva el front (una CSP en `<meta>` no lo admite) y límite de tamaño del cuerpo |
| 4.6 | Token en `sessionStorage` | Muy bajo | No hay vectores de XSS y la CSP del build solo permite scripts propios, así que un script inyectado no puede ejecutarse ni enviar el token a otro origen (`connect-src` limitado a la API) |
| 4.7 | El portal bloquea la fila de la orden (`PESSIMISTIC_WRITE`) en cada consulta | Bajo | Necesario para contar bien los fallos. Muchas consultas a un mismo código solo hacen esperar a esa orden, y el límite por IP las frena |
| 4.8 | ~~`PATCH /api/auth/password` no limita los intentos con la contraseña actual~~ | — | **Mitigado (5 de noviembre, sección 8).** 10 fallos por usuario en 15 minutos responden 429 |
| 4.9 | ~~Un usuario dado de baja con el canal en tiempo real abierto sigue recibiendo avisos hasta que vence su token~~ | — | **Mitigado (5 de noviembre, sección 8).** La baja, el cambio de rol y el cambio de contraseña cortan sus avisos al instante |
| 4.10 | ~~En `/topic/interventions` el técnico recibe el código, el estado y el nombre del técnico de todas las órdenes~~ | — | **Mitigado (5 de noviembre, sección 8).** El técnico recibe solo sus órdenes y solicitudes por su cola privada |
| 4.11 | El broker del canal en tiempo real vive en memoria | Bajo | Válido con una sola instancia, igual que 4.3. Con varias, un broker externo (RabbitMQ con STOMP) |
| 4.12 | Con «Ya lo tengo», el técnico da entrada de stock al crear un repuesto nuevo desde la orden | Bajo (control interno) | Decisión del negocio para registrar repuestos conseguidos por fuera. Queda en el kárdex como `IN_PURCHASE` con su nombre, el costo y la orden; el coordinador lo ve en Movimientos. Si se quiere, exigir aprobación del coordinador |

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
- El canal en tiempo real usa la misma validación del token que la API, permiso por rol en cada suscripción, avisos sin datos sensibles y solo avisa después de que el cambio se guarda.

---

## 7. Revisión del 5 de noviembre de 2026: tiempo real y cambios funcionales

### 7.1 Qué cambió

| Cambio | Superficie nueva |
|---|---|
| Canal en tiempo real: STOMP sobre WebSocket en `/ws`, con SockJS de respaldo | Una conexión de larga duración, un endpoint abierto en HTTP y un canal público para el portal |
| Fecha y hora estimada de entrega con tiempo restante | Validación de fechas en el servidor |
| Varios daños al recibir, patrón de desbloqueo y validación del documento por tipo | Nuevos datos de entrada |
| «Otro» repuesto desde la orden, con entrada de stock por el técnico | Escritura en el inventario desde un rol que antes no la tenía (ver 4.12) |

### 7.2 Riesgos evitados en el diseño del canal en tiempo real

Antes de implementarlo se hizo un diagnóstico, y estos riesgos se resolvieron en el diseño:

| # | Riesgo | Nivel | Cómo se evitó |
|---|---|---|---|
| 7.2.1 | **Canal público por código consecutivo:** con `/topic/orders/{código}` abierto, cualquiera podría suscribirse a `FT-00001`, `FT-00002`… y ver el estado y la entrega de todas las órdenes, saltándose la clave del portal | Alto | La suscripción exige el código **y la clave** (`accessKey`), y se valida con `PortalService.authorizeFollow`: mismo hash, mismo bloqueo del código tras 5 fallos y mismo límite por IP que la consulta. Solo se permite el canal de la orden original. Pruebas: `StompAuthChannelInterceptorTest` |
| 7.2.2 | **Eventos con el DTO completo:** enviar la orden completa a todos rompía los permisos (el asesor no ve el desglose, el técnico solo ve sus órdenes, la contraseña de desbloqueo es solo del asesor y del técnico asignado) | Alto | Eventos «delgados» (`{ type, entity, id, payload, timestamp }`) sin dinero, contraseñas, documentos ni contactos; cada pantalla vuelve a pedir por la API, que aplica los permisos. Pruebas: `InterventionEventPublisherTest`, `RealtimeIntegrationTest` |
| 7.2.3 | **Token sin validar en la conexión:** el navegador no envía `Authorization` en el handshake WebSocket | Alto | `StompAuthChannelInterceptor` valida el token en el `CONNECT` con `JwtTokenVerifier`, la misma clase que usa `JwtAuthFilter`: firma, vencimiento, usuario activo, rol sin cambios y contraseña sin cambiar. Cada `SUBSCRIBE` revisa el rol del canal y rechaza todo `SEND` del cliente |
| 7.2.4 | **Conexión viva después de vencer el token** | Medio | `ExpiredSessionOutboundInterceptor` deja de entregar mensajes a una sesión cuyo token venció, y las suscripciones nuevas también se rechazan |
| 7.2.5 | **Avisar cambios que luego se revierten** | Bajo | `RealtimeBroadcaster` publica con `@TransactionalEventListener(AFTER_COMMIT)` |

### 7.3 Configuración revisada

- **CORS del canal:** SockJS pide `/ws/info` con credenciales, y sin `Access-Control-Allow-Credentials: true` el navegador bloqueaba la conexión. Se permitió **solo para `/ws/**`** y con los mismos orígenes de `CORS_ALLOWED_ORIGINS`; la API sigue sin credenciales y un origen ajeno recibe 403. El canal no usa cookies para autenticar (el token va en el `CONNECT`), así que esto no abre la API. Prueba: `CorsConfigTest`.
- **Mensajes de rechazo:** `StompErrorHandler` responde el motivo del rechazo («No tiene permiso para recibir estas actualizaciones») y no el mensaje interno de Spring.
- **Validación de entrada nueva:** la fecha y hora de entrega debe ser posterior al ingreso (400 si no). El documento se valida por tipo en el servidor (cédulas solo con números y su largo; pasaporte alfanumérico), aunque el front ya lo limite.

### 7.4 Verificación de punta a punta (5 de noviembre de 2026)

| Comprobación | Resultado |
|---|---|
| Cliente STOMP con token falso | Rechazado en el `CONNECT` |
| Técnico se suscribe a `/topic/persons` | Rechazado: «No tiene permiso para recibir estas actualizaciones» |
| Anónimo se suscribe a `/topic/orders/FT-00001` sin clave | Rechazado: «No encontramos una orden con ese código y clave» |
| Cambio por la API con el coordinador escuchando | Llega el evento, sin teléfono, correo ni documento |
| `/ws/info` desde `http://localhost:5173` y desde un origen ajeno | Con `Allow-Credentials: true`, y 403 respectivamente |
| Chrome sin ventana en Inventario y Órdenes | La pantalla recibe los eventos en vivo, sin errores en la consola |
| Orden con entrega a una hora ya pasada | 400 «La fecha y hora estimada de entrega debe ser posterior al momento del ingreso» |

---

## 8. Reducción de riesgos del 5 de noviembre de 2026

### 8.1 Qué se cambió

| Riesgo | Antes | Ahora | Prueba |
|---|---|---|---|
| **4.2** Ordenar por cualquier campo | `?sort=password` o `?sort=accessKeyHash` llegaban a la consulta | `SortWhitelist` (en `common`) traduce el orden con el mapa de campos permitidos de cada listado (órdenes, personas, proveedores, solicitudes, movimientos, cierres de caja; repuestos ya lo tenía). Cualquier otro campo responde 400 antes de tocar la base, y el id desempata para que la paginación sea estable | `SortWhitelistTest`; batería contra el servidor |
| **4.5** Cabeceras de seguridad | Solo las de Spring por defecto | La API envía `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, `Referrer-Policy: no-referrer` y `Permissions-Policy` (sin cámara, micrófono, ubicación ni pagos), además de `X-Frame-Options` y `X-Content-Type-Options`. El build del front incluye una CSP: solo scripts propios, conexiones solo a la API y su canal, `frame-src blob:` para los PDF y `object-src 'none'` | `SecurityConfigTest.shouldSendSecurityHeadersOnEveryResponse`; Chrome contra el build |
| **4.6** Token en `sessionStorage` | Protegido solo por la ausencia de XSS | La CSP impide ejecutar scripts ajenos y enviar datos a otros orígenes | Igual que 4.5 |
| **4.8** Cambio de contraseña sin límite | Con un token robado se podía adivinar la contraseña actual sin freno | `IpAttemptLimiter` con llave `password:<id>`: 10 fallos en 15 minutos → 429 «Demasiados intentos con la contraseña actual» | `AuthServiceImplTest.shouldBlockPasswordChangeAfterTenWrongCurrentPasswords` |
| **4.9** Avisos a un usuario dado de baja | Seguía recibiéndolos hasta que vencía su token (hasta 8 h) | `PersonAccessRevokedEvent` se publica al dar de baja, cambiar el rol o la contraseña (propia o por el administrador). `RealtimeAccessRevoker` marca vencidas sus sesiones STOMP al confirmar la transacción: dejan de recibir mensajes y de suscribirse | `StompAuthChannelInterceptorTest.shouldRejectSubscriptionsOnceTheUserLosesAccess`, `PersonServiceImplTest` |
| **4.10** El técnico veía órdenes ajenas en el canal | `/topic/interventions` y `/topic/part-requests` llegaban a todo el personal | Esos topics son solo de recepción. El técnico recibe sus órdenes y solicitudes en `/user/queue/interventions` y `/user/queue/part-requests`, que Spring entrega únicamente a su sesión; si le quitan una orden, recibe el aviso para sacarla de su lista | `StompAuthChannelInterceptorTest`, `InterventionEventPublisherTest`, `RealtimeIntegrationTest.shouldDeliverOrderEventsOnlyToTheAssignedTechnician` |

### 8.2 Pruebas ejecutadas

| Conjunto | Resultado |
|---|---|
| Backend (`./mvnw test`): unitarias, `@WebMvcTest` e integración con un cliente STOMP real contra MySQL | **108 de 108** en verde, ninguna omitida |
| Front: `npm test` (utilidades), `oxlint`, `vite build` | **11 de 11**, sin advertencias, build correcto |
| Dependencias: `npm audit` (producción y desarrollo) | **0 vulnerabilidades** |
| Batería de seguridad contra el servidor en marcha (cabeceras, orden de listados, límite de contraseña, permisos del canal, colas privadas, token falso, rutas sin sesión) | **23 de 23** correctas |
| Build de producción con CSP en Chrome sin ventana (Órdenes e Inventario) | Recibe los eventos en vivo; ningún bloqueo de la CSP ni error en la consola |

Detalle de la batería:

| Verificación | Resultado |
|---|---|
| `GET /api/people?sort=password`, `/api/interventions?sort=accessKeyHash`, `?sort=unlockCode`, `/api/part-requests?sort=description` | 400 |
| `GET /api/people?sort=fullName,desc`, `/api/interventions?sort=estimatedDeliveryDate&sort=estimatedDeliveryTime`, `/api/suppliers?sort=taxId`, `/api/stock-movements?sort=createdAt,desc` | 200 |
| 11 cambios de contraseña con la contraseña actual errada | 10 × 400 y el 11.º 429 |
| Técnico se suscribe a `/topic/interventions` o `/topic/part-requests` | Rechazado |
| Técnico se suscribe a `/user/queue/interventions`; coordinador a `/topic/interventions` | Aceptado |
| Anónimo se suscribe a `/topic/spare-parts`, o sigue una orden sin clave; conexión con token falso | Rechazado |
| API sin token; ruta fuera de `/api` | 401; rechazada |

### 8.3 Riesgos que quedan

| # | Riesgo | Nivel | Por qué queda |
|---|---|---|---|
| 4.1 | Clave de consulta = últimos 4 dígitos del celular | Medio | Regla de negocio de `fixtrack.md`; mitigada por bloqueos y comprobante enmascarado |
| 4.3 / 4.11 | Límite de intentos y broker en memoria | Bajo | Válidos con una sola instancia; con varias, Redis o un broker externo |
| 4.4 | `ddl-auto: update` por defecto | Medio | Necesario para el desarrollo; en producción `DDL_AUTO=validate` con los scripts de `db/migration/` |
| 4.5 | TLS y cabeceras del servidor web del front | Bajo | Dependen del despliegue |
| 4.7 | Bloqueo de la fila en el portal | Bajo | Necesario para contar bien los fallos |
| 4.12 | El técnico da entrada de stock con «Ya lo tengo» | Bajo | Decisión del negocio; queda trazado en el kárdex |
