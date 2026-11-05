# Backend de FixTrack: Java 25 y Spring Boot

Buenas prácticas para el servicio `fixtrackback` (Java 25, Spring Boot 4.1, Spring Data JPA, Spring Security, Bean Validation, MySQL y Lombok). Las reglas de negocio están en `.claude/rules/fixtrack.md`.

## Estructura actual

```
FixTrack/
├── .claude/rules/              # fixtrack.md (negocio) y springboot.md (este archivo)
└── fixtrackback/               # servicio Spring Boot (Maven, usar mvnw / mvnw.cmd)
    ├── pom.xml
    ├── .env.example            # variables de entorno: DB_PASSWORD, ENCRYPTION_KEY
    └── src/
        ├── main/
        │   ├── java/com/fixtrack/
        │   │   ├── FixtrackApplication.java
        │   │   ├── infra/security/EncryptedStringConverter.java
        │   │   ├── person/entity/          # Person, Specialty, PersonSpecialty
        │   │   ├── device/entity/          # Brand, DeviceModel, Device
        │   │   ├── inventory/entity/       # PartType, SparePart, SparePartCompatibility, Supplier,
        │   │   │                           # SupplierSparePart, PartRequest, StockMovement
        │   │   ├── intervention/entity/    # Intervention, InterventionFinding, InterventionStatusHistory,
        │   │   │                           # DeliveryDateChange, InterventionSparePart
        │   │   ├── cash/entity/            # Payment, CashClosing
        │   │   └── setting/entity/         # SystemSetting
        │   └── resources/
        │       └── application.yml
        └── test/java/com/fixtrack/
            └── FixtrackApplicationTests.java
```

Por ahora solo existen las entidades (con sus enums en el mismo paquete `entity`); las demás capas se crean a medida que se implementa cada dominio.

## Convenciones de las entidades

- Lombok: `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor(access = PROTECTED)` y `@AllArgsConstructor(access = PRIVATE)`; los valores por defecto llevan `@Builder.Default`.
- Las tablas intermedias con llave compuesta usan `@EmbeddedId` (clase `...Id`) con `@MapsId` y se crean con su constructor, no con builder.
- Los opcionales únicos (correo, IMEI) se guardan como `null`, nunca como texto vacío, para que la restricción única no choque.
- `StockMovement` es `@Immutable` y no tiene setters.
- La contraseña o patrón de desbloqueo usa `@Convert(converter = EncryptedStringConverter.class)`.

## Estructura objetivo

```
com.fixtrack
├── FixtrackApplication.java
├── config/                     # SecurityConfig (incluye el PasswordEncoder) y CorsConfig
├── infra/security/             # JwtAuthFilter
├── common/                     # excepciones de negocio y el @RestControllerAdvice
├── person/
├── device/
├── inventory/
├── intervention/
├── cash/
└── setting/
```

Cada dominio tiene las mismas capas:

```
<dominio>/
├── controller/                 # endpoints REST
├── service/                    # interfaces de los servicios
│   └── impl/                   # implementaciones (@Service)
├── repository/                 # interfaces que extienden JpaRepository
├── entity/                     # entidades JPA
├── dto/                        # records de entrada y de salida
└── mapper/                     # conversión entidad ↔ DTO
```

## Nombres, idioma y comentarios

- Clases, métodos, atributos, variables, paquetes y endpoints en inglés.
- Comentarios en español, según la regla de comentarios de `fixtrack.md`.
- Clases en PascalCase, métodos y atributos en camelCase, constantes en UPPER_SNAKE_CASE.
- Sufijos por capa: `PersonController`, `PersonService`, `PersonServiceImpl`, `PersonRepository`, `Person` (entidad), `PersonMapper`.
- DTO con sufijo según su uso: `CreatePersonRequest`, `UpdatePersonRequest`, `PersonResponse`, `PersonSummaryResponse`.

## Flujo de dependencias

`Controller → interfaz del Service → Repository (extiende JpaRepository) → Spring Data JPA → MySQL`

- El controlador solo depende de interfaces de servicio; nunca usa un repositorio ni una entidad.
- Un servicio solo usa repositorios de su propio dominio. Para datos de otro dominio usa la interfaz del servicio de ese dominio.
- Evitar ciclos entre servicios de distintos dominios; si aparecen, mover la lógica al dominio dueño de los datos.
- Inyección siempre por constructor (`private final` + `@RequiredArgsConstructor`); nunca `@Autowired` en atributos.

## DTO y mappers

- Todos los DTO son `record`, de entrada y de salida. Las entidades nunca se exponen en la API, ni dentro de otro DTO.
- Las anotaciones de Bean Validation van en los componentes del record de entrada.
- Los DTO de salida no incluyen datos sensibles: contraseñas, hashes, la clave de consulta ni la contraseña o patrón de desbloqueo (salvo el endpoint que la muestra al asesor y al técnico asignado).
- Los mappers son clases `@Component` del paquete `mapper` del dominio, con métodos como `toResponse(entity)` y `toEntity(request)`. No contienen lógica de negocio ni consultan repositorios.
- El código de la orden (`FT-00001`) se calcula en el mapper o en un utilitario a partir del id; nunca se guarda.

## Controladores y API REST

- Todos los endpoints empiezan por `/api` y usan sustantivos en plural en inglés: `/api/people`, `/api/devices`, `/api/orders`, `/api/orders/{id}/parts`.
- Las acciones que no son CRUD se expresan como subrecurso o acción sobre el recurso: `POST /api/orders/{id}/deliveries`, `PATCH /api/orders/{id}/status`.
- Verbos y códigos HTTP: `GET` 200, `POST` 201 (con el recurso creado), `PUT`/`PATCH` 200, baja lógica 204; 400 validación, 401 sin autenticar, 403 sin permiso, 404 no existe, 409 conflicto de negocio.
- Los `@RequestBody` llevan `@Valid`; los `@PathVariable` y `@RequestParam` se validan con restricciones y `@Validated` en el controlador.
- Listados con paginación (`Pageable`) y filtros por `@RequestParam`; responder con `PageResponse<T>` de `common`, no con `Page<Entidad>`. Los catálogos simples (marcas, modelos, tipos) se devuelven completos como lista ordenada, porque alimentan listas desplegables.
- Edición completa con `PUT` (catálogos); edición parcial con `PATCH` y un record de campos opcionales, donde `null` significa «no cambiar».
- Los controladores son delgados: reciben, validan, delegan al servicio y responden. Sin lógica de negocio ni `try/catch`.
- El portal público del cliente vive bajo `/api/public/...` y es el único grupo, junto con el login, que no exige autenticación.

## Servicios y transacciones

- La interfaz en `service/` define el contrato; la implementación en `service/impl/` lleva `@Service`.
- `@Transactional` va en la implementación del servicio, nunca en el controlador ni en el repositorio. Las consultas usan `@Transactional(readOnly = true)`.
- Toda operación que escribe en varias tablas (recibir una orden, registrar un repuesto usado con su movimiento de stock, registrar un abono, cerrar la caja) va en un solo método transaccional, para que todo se guarde o nada.
- Los cambios de stock se protegen contra concurrencia (bloqueo optimista con `@Version` o bloqueo pesimista en la consulta) y nunca dejan stock negativo.
- Todo cambio de stock pasa por `StockService` (dominio `inventory`): actualiza el stock con `saveAndFlush`, para que el conflicto de versión salte dentro de la transacción, y registra el `StockMovement` con el saldo resultante. Los demás dominios no tocan `spare_part.stock` directamente.
- `StockService` también registra las entradas de mercancía (`IN_PURCHASE`, con costo, motivo y responsable) y las correcciones de un consumo: solo mueve la diferencia (`OUT_REPAIR` si sube, `RETURN` si baja) al costo guardado en el consumo.
- El estado de stock (`StockStatus`: `OUT` sin unidades, `LOW` en el mínimo o por debajo, `OK` por encima) se calcula, no se guarda.
- Las búsquedas de texto parciales usan `collate(campo as utf8mb4_0900_ai_ci) LIKE :pattern ESCAPE '!'` para ignorar mayúsculas y tildes; el servicio escapa `!`, `%` y `_` antes de armar el patrón.
- El orden de un listado paginado solo acepta un mapa de campos permitidos del servicio (por ejemplo `brand` → `deviceModel.brand.name`); un campo desconocido responde 400.
- El dinero de la orden (total, lo que se debe según el estado y la revisión de los daños adicionales frente al presupuesto) vive en `InterventionPricingService`. Lo usan la orden, sus repuestos, la caja y los comprobantes, y así `cash` depende de `intervention` sin crear un ciclo con `InterventionService`.
- Todo cambio que mueva el total (mano de obra, repuestos, daños) llama a `reviewAdditionalFindings`: los daños adicionales sin respuesta del cliente quedan `PENDING` si el total supera el presupuesto y `APPROVED` si no. Los que respondió el asesor (`respondedBy`) no se tocan.
- La entrega y la cancelación tienen su propio endpoint (`/deliver`, `/cancel`); `PATCH /status` las rechaza. Sin reparación (`RepairOutcome.endsWithoutRepair`) la orden solo puede pasar a `READY` y ya no acepta repuestos, mano de obra ni daños.
- Los comprobantes PDF se generan con OpenPDF (`ReceiptServiceImpl`). La clave de consulta solo se guarda con hash, así que el comprobante de ingreso exige la clave vigente en la cabecera `X-Access-Key`; para reimprimirlo se genera una nueva con `POST /{id}/access-key`.
- Cuando un dominio debe reaccionar a otro que ya depende de él, se usa un evento de Spring en lugar de inyectar el servicio: por ejemplo `InterventionCancelledEvent`, que publica la cancelación de la orden y que `inventory` escucha con `@EventListener` dentro de la misma transacción para cancelar las solicitudes de repuestos sin pedir.
- Las solicitudes de repuestos siguen `PENDING → APPROVED → ORDERED → RECEIVED` (o `REJECTED`/`CANCELLED`) y se bloquean con `PESSIMISTIC_WRITE` al cambiar de estado. Al recibir, en una sola transacción: `IN_PURCHASE` ligado a la solicitud, consumo `OUT_REPAIR` en la orden al costo real (si sigue en trabajo) y último costo del proveedor.
- En los records de entrada, los campos opcionales son wrappers (`Boolean`, `Integer`), no primitivos: Jackson 3 rechaza el cuerpo si falta un primitivo.
- Informes: las sumas salen de consultas agregadas con proyección a records (`SELECT new ...`). Cuando dos sumas vienen de tablas distintas (lo cobrado en `intervention`, el costo en `intervention_spare_part`), se agregan por separado y se combinan en Java, porque sumarlas con un `JOIN` duplicaría filas. Las fechas se filtran como `[from, to + 1 día)` y el rango máximo es de 366 días.
- El informe de ganancias del técnico se limita en el servicio a sus propias órdenes, aunque envíe otro `technicianId`.
- Tiempo real (STOMP sobre WebSocket con SockJS de respaldo, endpoint `/ws`): la configuración vive en `config/websocket` (`WebSocketConfig`, `StompAuthChannelInterceptor`, `StompErrorHandler`) y la base en `infra/realtime` (`RealtimeEvent`, `RealtimeTopics`, `RealtimePublisher`, `RealtimeBroadcaster`). Cada dominio publica con su `events/<Dominio>EventPublisher` desde el servicio; `RealtimeBroadcaster` envía con `@TransactionalEventListener(AFTER_COMMIT)`, así que un cambio revertido no avisa.
- Los eventos son delgados (`{ type, entity, id, payload, timestamp }`, sin dinero, contraseñas, contacto ni documento): el front vuelve a pedir por REST lo que su rol puede ver.
- El JWT se valida en el `CONNECT` con `JwtTokenVerifier` (mismas reglas de `JwtAuthFilter`) y cada `SUBSCRIBE` revisa el rol: repuestos, alertas, órdenes y solicitudes para el personal; personas y equipos solo para recepción. `/topic/orders/{código}` es público, pero exige la clave de consulta (cabecera `accessKey`) con el mismo conteo de intentos del portal.
- Jackson rechaza decimales en campos enteros (`accept-float-as-int: false`): una cantidad `1.5` responde 400 en vez de truncarse.
- Las reglas de negocio se validan en el servicio y fallan con excepciones de negocio de `common`, no con `RuntimeException` genéricas.

## Entidades y base de datos

- Tablas y columnas en snake_case (`service_order`, `estimated_delivery_date`); se declaran con `@Table(name = ...)` y `@Column(name = ...)` cuando el nombre no sale directo del atributo.
- Llave primaria `Long id` con `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
- Los enums se guardan con `@Enumerated(EnumType.STRING)`, nunca por ordinal.
- Relaciones `@ManyToOne` y `@OneToOne` con `fetch = FetchType.LAZY`; usar `@EntityGraph` o `JOIN FETCH` donde se necesiten los datos y evitar consultas N+1.
- En entidades usar `@Getter`/`@Setter` y constructor sin argumentos protegido; no usar `@Data`, `@EqualsAndHashCode` ni `@ToString` sobre relaciones.
- Fechas con `java.time`: `LocalDate` para fechas de calendario (fecha estimada de entrega) e `Instant` o `LocalDateTime` para momentos, siempre en `America/Bogota`.
- Auditoría con `createdAt` y `updatedAt` (`@CreationTimestamp`/`@UpdateTimestamp` o auditoría de Spring Data).
- Repositorios: métodos derivados o `@Query` en JPQL; SQL nativo solo cuando JPQL no alcance (por ejemplo, informes).
- Cada repositorio empieza solo con la búsqueda y existencia por llave única (`findBy...`, `existsBy...` y `existsBy...AndIdNot` para validar al editar); las consultas de cada historia se agregan en su sprint.
- Cuando solo se necesitan totales o unas pocas columnas, `@Query` con proyección a un `record` (`SELECT new ...`) o a una interfaz, nunca cargar entidades completas para sumarlas en Java.

## Bajas lógicas

- Nada que tenga historial se borra. Personas, repuestos, proveedores y similares tienen `active` y la baja lo pone en `false`.
- Los catálogos simples (marcas, modelos, tipos de repuesto, especialidades) no tienen `active`: se eliminan solo si ningún registro los usa; si están en uso, el servicio responde 409 con el motivo.
- Las consultas normales filtran los registros activos; los históricos (órdenes, movimientos, pagos) siguen mostrando los datos inactivos que referencian.
- Las órdenes no se borran: se cancelan con el estado `CANCELLED`.

## Dinero

- Siempre `BigDecimal`, nunca `double` ni `float`.
- Escala 2 con `RoundingMode.HALF_UP`, aplicada al calcular y al guardar: `@Column(precision = 12, scale = 2)`.
- Comparar con `compareTo`, no con `equals`.
- Precio de venta y costo se copian al registro del repuesto usado al momento del uso; los totales y la ganancia se calculan, no se guardan.

## Validación

- Toda entrada se valida en el servidor con Bean Validation (`@NotNull`, `@NotBlank`, `@Size`, `@Pattern`, `@Email`, `@Positive`, `@FutureOrPresent`, etc.), aunque el front también valide.
- Las reglas que dependen de otros campos o de la base de datos (IMEI obligatorio solo en teléfonos, correo único, garantía vigente) se validan en el servicio o con validadores personalizados.
- Los mensajes de validación se devuelven en español.

## Errores

- Las excepciones de negocio viven en `common` (por ejemplo `NotFoundException`, `BusinessRuleException`, `ConflictException`).
- Un único `@RestControllerAdvice` en `common` las traduce a respuestas `ProblemDetail` (RFC 9457) con el código HTTP correcto e incluye los errores de validación por campo.
- Nunca se devuelven trazas, mensajes de SQL ni detalles internos al cliente; se registran en el log.

## Seguridad

- `SecurityConfig` define la cadena de filtros sin estado (JWT), el `PasswordEncoder` (`BCryptPasswordEncoder`) y `@EnableMethodSecurity`. `CorsConfig` define los orígenes permitidos del front.
- `JwtAuthFilter` (en `infra.security`) valida el token y carga el usuario en el `SecurityContext`.
- Login: `AuthService.authenticate` verifica credenciales con el `AuthenticationManager` (sobre `PersonUserDetailsService`) y `JwtService` emite el token HS256 con `sub` = correo, `uid` y `role`. La llave y la duración llegan por `JWT_SECRET` y `JWT_EXPIRATION` (por defecto 8h).
- `JwtAuthFilter` se crea dentro de `SecurityConfig` (no como bean, para que no se registre también como filtro de servlet). En cada petición recarga al usuario: si está inactivo o su rol cambió desde que se emitió el token, responde 401 y debe iniciar sesión de nuevo. Sin token la petición sigue anónima y la ruta responde 401.
- Rutas abiertas sin token: `POST /api/auth/login`, `/api/public/**` (portal del cliente) y `/error`. El resto de `/api/**` exige sesión, y cada endpoint lleva su `@PreAuthorize`; un endpoint nuevo sin `@PreAuthorize` queda abierto a cualquier usuario interno.
- Jerarquía de roles (`RoleHierarchy`): `ADMIN` implica `COORDINATOR` y `TECHNICIAN`; `COORDINATOR` implica `ADVISOR`. Por eso `hasRole('ADVISOR')` es «recepción» (asesor, coordinador y administrador), `hasRole('COORDINATOR')` es inventario y catálogos, y `hasRole('ADMIN')` es personas y especialidades. La lectura de catálogos y repuestos es `isAuthenticated()`.
- El usuario de la petición es un `AuthenticatedUser` (en `infra.security`); los controladores lo obtienen con `CurrentUserProvider.require()` y lo pasan al servicio cuando una regla depende de quién actúa.
- Toda falla de login responde «Correo o contraseña incorrectos.», sin revelar si el correo existe, si es cliente o si está inactivo.
- El login y el portal cuentan los fallos por IP en `IpAttemptLimiter` (llaves separadas, 10 en 15 minutos → 429). No se bloquea la cuenta por correo, para que nadie pueda dejar sin acceso a otro usuario. Detrás de un proxy se usa `server.forward-headers-strategy: native`, nunca `framework`.
- Cambiar la contraseña (propia o por el administrador) guarda `passwordChangedAt`; `JwtAuthFilter` rechaza los tokens emitidos antes. `PATCH /api/auth/password` devuelve un token nuevo para la sesión actual.
- Los comprobantes descargados desde el portal público enmascaran documento, celular, correo, IMEI y serie.
- Permisos por rol con `@PreAuthorize("hasRole('ADVISOR')")` y similares en los métodos del servicio o del controlador, según los permisos de `fixtrack.md`. Las reglas de pertenencia (el técnico solo ve sus órdenes) se validan en el servicio.
- Las contraseñas y la clave de consulta se guardan con el `PasswordEncoder`; nunca en texto plano ni en logs.
- La contraseña o patrón de desbloqueo se cifra de forma reversible (AES-GCM) con una llave que llega por variable de entorno.

## Configuración y credenciales

- Credenciales y secretos solo por variables de entorno: `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`, `${JWT_SECRET}`, `${ENCRYPTION_KEY}`. Nada de credenciales en `application.yaml` ni en el repositorio, tampoco contraseñas fijas en inicializadores.
- `CORS_ALLOWED_ORIGINS` y `DDL_AUTO` (`validate` en producción) también llegan por entorno. Los datos de ejemplo solo se cargan con `SEED_DATA=true` y `DEMO_USERS_PASSWORD`.
- Zona horaria `America/Bogota`: `spring.jpa.properties.hibernate.jdbc.time_zone`, la zona de la JVM y un bean `Clock` que se inyecta donde se calcule «hoy», para poder probarlo.
- `spring.jpa.open-in-view: false`.
- `spring.jpa.hibernate.ddl-auto` nunca en `create` ni `create-drop` fuera del desarrollo local.
- Hilos virtuales activados con `spring.threads.virtual.enabled: true`.

## Java 25

- `record` para DTO y valores inmutables; `var` cuando el tipo es evidente en la misma línea.
- `switch` con flechas y pattern matching en lugar de cadenas de `if`/`else`; `sealed` cuando una jerarquía es cerrada.
- Para transiciones de estado de la orden, un método en el enum o en el servicio que diga qué estados siguientes se permiten, en lugar de condicionales dispersos.
- Text blocks (`"""`) para consultas JPQL o SQL largas.
- `Stream.toList()` en vez de `collect(Collectors.toList())`; colecciones inmutables (`List.of`, `Map.of`) cuando no cambian.
- `Optional` solo como tipo de retorno; nunca como atributo ni parámetro. Usar `orElseThrow` con la excepción de negocio.
- No devolver `null` en colecciones: devolver una lista vacía.

## Pruebas

- Pruebas unitarias de los servicios con JUnit 5 y Mockito para las reglas de negocio (cálculo de totales, transiciones de estado, garantías, clave de consulta).
- `@WebMvcTest` para controladores (validación, códigos HTTP y permisos) y `@DataJpaTest` para repositorios con consultas especiales.
- Nombres de pruebas en inglés y descriptivos: `shouldRejectWarrantyWhenOriginalIsExpired`.
