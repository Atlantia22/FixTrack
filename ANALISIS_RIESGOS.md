# Informe Integral de Análisis de Riesgos y Seguridad — FixTrack

**Fecha de emisión:** 31 de octubre de 2026  
**Alcance:** Código fuente completo de Backend (`fixtrackback`), Frontend (`fixtrack-front`) y configuraciones de despliegue.

---

## 1. Resumen Ejecutivo

El presente documento consolida la auditoría técnica de seguridad, arquitectura, resiliencia y operación del sistema **FixTrack**.

El proyecto evidencia un nivel alto de madurez en aspectos de diseño de software (separación en capas por dominio, validación estricta con Bean Validation, manejo unificado de errores bajo RFC 7807, cifrado AES-GCM para datos sensibles de desbloqueo y control de concurrencia optimista/pesimista). No obstante, se han identificado **vulnerabilidades críticas y riesgos operativos** que deben ser abordados antes de pasar a un entorno de producción público.

### Matriz de Riesgos Identificados

| Nivel | Hallazgo | Componente Afectado | Impacto Principal |
|---|---|---|---|
| 🚨 **Crítico** | Exposición de PII sensible (Cédula, IMEI, teléfono) por clave predecible y códigos secuenciales | Portal Público (`/api/public/...`) | Violación de Ley 1581 (Habeas Data) y robo de identidad de terminales móviles |
| 🚨 **Crítico** | Ausencia de Rate Limiting y protección contra fuerza bruta en Login de empleados | Autenticación (`/api/auth/login`) | Compromiso de credenciales por ataques de diccionario masivos |
| ⚠️ **Alto** | DoS global en el portal si se despliega tras Proxy Inverso (`getRemoteAddr`) | Seguridad IP (`IpAttemptLimiter`) | Bloqueo total del portal de consulta para todos los clientes |
| ⚠️ **Alto** | Alteración y desconfiguración accidental de cuentas internas al registrar clientes | Gestión de Personas (`PersonServiceImpl`) | Pérdida de acceso de empleados o secuestro de cuentas |
| ⚠️ **Alto** | Tokens JWT sin posibilidad de revocación tras Logout o cambio de clave | Gestión de Sesión (`JwtAuthFilter`, `JwtService`) | Sesiones comprometidas activas durante 8 horas ininterrumpidas |
| ⚡ **Medio** | Uso de `hibernate.ddl-auto: update` y credenciales por defecto | Persistencia (`application.yml`) | Riesgo de inconsistencia de esquema o pérdida de datos en despliegue |
| ⚡ **Medio** | Consumo excesivo de memoria en reportes Excel anuales (`XSSFWorkbook`) | Reportes (`ProfitExcelExporter`) | Riesgo de `OutOfMemoryError` en el servidor JVM |
| ⚡ **Medio** | Control de intentos en memoria RAM no distribuido | Escalabilidad (`IpAttemptLimiter`) | Evasión de bloqueos en arquitecturas multi-instancia |
| 🔍 **Bajo** | Versiones experimentales/anómalas en `pom.xml` (Spring Boot 4.1.1 / Java 25) | Build y Dependencias (`pom.xml`) | Incompatibilidad con plugins CI/CD y analizadores SAST estándar |
| 🔍 **Bajo** | Políticas de contraseña básicas sin comprobación de entropía | Seguridad de Cuentas | Vulnerabilidad ante contraseñas débiles |

---

## 2. Hallazgos Detallados por Severidad

### 🚨 2.1. Riesgos Críticos (Severidad Alta)

#### 2.1.1. Exposición Masiva de Datos Personales (PII) e IMEI en el Portal Público
* **Ubicación en código:**
  - `fixtrackback/src/main/java/com/fixtrack/intervention/service/impl/InterventionServiceImpl.java` (Líneas 129–133)
  - `fixtrackback/src/main/java/com/fixtrack/intervention/service/impl/ReceiptServiceImpl.java` (Líneas 138–158)
  - `fixtrackback/src/main/java/com/fixtrack/intervention/controller/PortalController.java` (Líneas 34–38)
  - `fixtrackback/src/main/java/com/fixtrack/intervention/mapper/InterventionCode.java` (Líneas 26–28)
* **Descripción de la falla:**
  1. La clave de consulta (`accessKey`) asignada a la orden de reparación no es aleatoria: cuando el cliente tiene teléfono registrado, la clave corresponde sistemáticamente a los **últimos 4 dígitos de su teléfono celular**:
     ```java
     String accessKey = customer.getPhone() != null
         ? customer.getPhone().substring(customer.getPhone().length() - 4)
         : randomAccessKey();
     ```
  2. Los códigos de orden de reparación son enteros autoincrementales predecibles formateados como `FT-%05d` (`FT-00001`, `FT-00002`, `FT-00003`...).
  3. A través del endpoint público `/api/public/interventions/receipts/intake`, cualquier persona que consulte con código y clave descarga el comprobante oficial en PDF, exponiendo:
     - Nombre completo del cliente.
     - Tipo y número de documento (Cédula de Ciudadanía).
     - Teléfono celular y correo electrónico.
     - **IMEI** y número de serie del dispositivo móvil.
     - Diagnóstico, presupuesto y notas físicas.
* **Impacto:** Si un tercero conoce el teléfono celular de una persona y sospecha que envió a reparar un equipo, o si prueba combinaciones sobre los códigos secuenciales, puede extraer toda la información personal y el IMEI del equipo. En Colombia, esto constituye una violación grave del principio de confidencialidad de la **Ley Estatutaria 1581 de 2012 (Habeas Data)** y expone al cliente a suplantación o bloqueo de terminales.
* **Mitigación recomendada:**
  1. Generar **siempre** un PIN criptográfico aleatorio mediante `SecureRandom` para la `accessKey`, desvinculándolo del celular del cliente.
  2. Opcionalmente, complementar el código visible con caracteres aleatorios no correlativos (e.g. `FT-4829-K7`).
  3. Enmascarar los datos en el PDF de consulta pública (`CC *****123`, `IMEI **********3456`) o generar una versión simplificada de comprobante sin datos sensibles de identidad.

---

#### 2.1.2. Ausencia de Rate Limiting en el Endpoint de Autenticación de Empleados
* **Ubicación en código:**
  - `fixtrackback/src/main/java/com/fixtrack/person/controller/AuthController.java` (Líneas 29–32)
  - `fixtrackback/src/main/java/com/fixtrack/person/service/impl/AuthServiceImpl.java` (Líneas 43–58)
* **Descripción de la falla:**
  - El componente `IpAttemptLimiter` fue inyectado y utilizado únicamente en el portal de clientes (`PortalServiceImpl`).
  - El endpoint de inicio de sesión de empleados (`POST /api/auth/login`) no cuenta con limitación de peticiones por IP, ni bloqueo de cuentas tras fallos reiterados, ni protección tipo Captcha.
* **Impacto:** Un atacante puede ejecutar ataques masivos de fuerza bruta, *credential stuffing* o ataques de diccionario contra cuentas administrativas (`admin@fixtrack.local`) y de personal operativo, facilitando la toma de control del sistema interno.
* **Mitigación recomendada:**
  - Implementar rate limiting en `/api/auth/login` (usando un filtro servlet, interceptor o librería como Bucket4j).
  - Agregar un contador de intentos fallidos en la entidad `Person` (`failedLoginAttempts`, `lockedUntil`) que bloquee la cuenta por 15 minutos tras 5 intentos fallidos consecutivos.

---

### ⚠️ 2.2. Riesgos Altos (Seguridad, Integridad y Disponibilidad)

#### 2.2.1. Riesgo de DoS Global en Portal Público por uso de `getRemoteAddr()`
* **Ubicación en código:**
  - `fixtrackback/src/main/java/com/fixtrack/intervention/controller/PortalController.java` (Líneas 30–44)
  - `fixtrackback/src/main/java/com/fixtrack/infra/security/IpAttemptLimiter.java` (Líneas 28–53)
  - `fixtrackback/src/main/resources/application.yml`
* **Descripción de la falla:**
  - `PortalController` obtiene la dirección del cliente mediante `http.getRemoteAddr()`.
  - Cuando la aplicación se despliega en producción detrás de un balanceador o proxy inverso (Nginx, Traefik, Cloudflare, AWS ALB), `getRemoteAddr()` retorna la IP del proxy (`127.0.0.1` o una IP local del clúster).
  - `application.yml` no tiene habilitado `server.forward-headers-strategy=framework`.
* **Impacto:** Si un solo usuario o atacante falla 10 veces en el portal público, la IP del balanceador/proxy queda bloqueada en `IpAttemptLimiter`. En consecuencia, **todos los clientes legítimos quedan bloqueados simultáneamente**, provocando una Denegación de Servicio (DoS) total.
* **Mitigación recomendada:**
  - Agregar en `application.yml`:
    ```yaml
    server:
      forward-headers-strategy: framework
    ```
  - Configurar las cabeceras `X-Forwarded-For` de forma segura en el proxy inverso.

---

#### 2.2.2. Corrupción y Modificación Accidental de Cuentas Internas al Registrar Clientes
* **Ubicación en código:**
  - `fixtrackback/src/main/java/com/fixtrack/person/service/impl/PersonServiceImpl.java` (Líneas 166–178 y 230–249)
* **Descripción de la falla:**
  - Al abrir una orden de reparación en el mostrador, se ejecuta `registerOrUpdateCustomer(CustomerRequest)`.
  - La consulta `personRepository.findByDocumentTypeAndDocumentNumber(...)` busca en toda la tabla `person` sin validar el rol.
  - Si un asesor registra como cliente a un empleado que lleva un equipo personal y escribe un correo personal o genérico, el método `updateContact(person, request)` **sobrescribe el email del usuario interno**:
    ```java
    if (existing.isPresent()) {
        Person person = existing.get();
        if (!person.isActive()) {
            throw new ConflictException("La persona con documento ... está dada de baja");
        }
        updateContact(person, request); // <-- Modifica email de la entidad interna
        return person;
    }
    ```
* **Impacto:** El empleado interno pierde su correo institucional de inicio de sesión o un asesor puede accidentalmente cambiar las credenciales de un técnico o coordinador.
* **Mitigación recomendada:**
  - Validar si la persona encontrada tiene un rol interno (`person.getRole() != Role.CUSTOMER`). Si es así, no modificar su correo institucional de acceso ni permitir cambios no autorizados sin validación previa.

---

#### 2.2.3. Ausencia de Revocación e Invalidación de Tokens JWT (8 Horas Fijas)
* **Ubicación en código:**
  - `fixtrackback/src/main/java/com/fixtrack/infra/security/JwtAuthFilter.java` (Líneas 62–80)
  - `fixtrackback/src/main/java/com/fixtrack/person/service/impl/AuthServiceImpl.java` (Líneas 63–73)
* **Descripción de la falla:**
  - Los tokens JWT tienen una vida útil predeterminada de 8 horas (`fixtrack.jwt.expiration: 8h`).
  - Al cerrar sesión en el frontend (`AuthProvider.logout`), solo se elimina el token de `sessionStorage`; el token permanece criptográficamente válido en el servidor.
  - Al cambiar la contraseña en `/api/auth/password`, no se registra la fecha de cambio ni se invalidan los tokens previamente emitidos.
* **Impacto:** Si un token es sustraído (por ejemplo, en un navegador en una terminal compartida o por spyware), el atacante conserva acceso irrestricto durante 8 horas, aun si el usuario legítimo cerró sesión o cambió su contraseña inmediatamente.
* **Mitigación recomendada:**
  - Añadir en `Person` el campo `LocalDateTime passwordChangedAt`.
  - En `JwtAuthFilter`, comparar el claim `iat` (issued at) del JWT: si el token fue emitido antes de `passwordChangedAt`, rechazar la petición con error 401.

---

### ⚡ 2.3. Riesgos Medios (Arquitectura, Resiliencia y Rendimiento)

#### 2.3.1. Uso de `hibernate.ddl-auto: update` en Producción
* **Ubicación en código:**
  - `fixtrackback/src/main/resources/application.yml` (Líneas 14–15)
* **Descripción:** La propiedad `spring.jpa.hibernate.ddl-auto: update` delega a Hibernate la modificación del esquema de la base de datos al arrancar la aplicación.
* **Impacto:** Hibernate noversiona cambios, no genera scripts de rollback, no renombra columnas de forma segura (crea columnas duplicadas y deja datos huérfanos) y puede provocar bloqueos de tablas prolongados en arranques de producción.
* **Mitigación recomendada:**
  - Configurar `ddl-auto: validate` o `none` para producción.
  - Implementar **Flyway** o **Liquibase** para manejar migraciones de esquema SQL trazables y versionadas.

---

#### 2.3.2. Parámetros de Base de Datos y CORS Rígidos en Configuración
* **Ubicación en código:**
  - `fixtrackback/src/main/resources/application.yml` (Líneas 8–12 y 62–63)
* **Descripción:**
  - La URL de conexión asume `localhost:3306` y el usuario por defecto es `root`, sin variables específicas como `${DB_HOST}`, `${DB_PORT}`, `${DB_USER}`.
  - La propiedad `fixtrack.cors.allowed-origins` está fijada como literal `http://localhost:5173` sin plantilla para variables de entorno (`${CORS_ALLOWED_ORIGINS}`).
* **Impacto:** Dificulta el despliegue en contenedores Docker, clústeres Kubernetes o servicios gestionados (AWS RDS, Google Cloud SQL), y bloqueará las peticiones de cualquier frontend desplegado bajo un dominio real de producción.
* **Mitigación recomendada:**
  - Ajustar en `application.yml`:
    ```yaml
    datasource:
      url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:fixtrack}?serverTimezone=America/Bogota
      username: ${DB_USER:root}
      password: ${DB_PASSWORD}
    cors:
      allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}
    ```

---

#### 2.3.3. Sobrecarga de Memoria en Exportación de Informes Excel (`XSSFWorkbook`)
* **Ubicación en código:**
  - `fixtrackback/src/main/java/com/fixtrack/intervention/service/impl/ProfitExcelExporter.java` (Líneas 31–41)
* **Descripción:** `ProfitExcelExporter` utiliza la clase `XSSFWorkbook` estándar de Apache POI para construir el informe anual de ganancias.
* **Impacto:** `XSSFWorkbook` mantiene todo el modelo de objetos XML del libro en memoria Heap. Cuando el negocio acumula miles de órdenes a lo largo de un año, la exportación puede agotar la memoria de la JVM y provocar caídas por `OutOfMemoryError`.
* **Mitigación recomendada:**
  - Migrar a `SXSSFWorkbook` (la versión optimizada para streaming de Apache POI), la cual mantiene una ventana deslizante de filas en memoria y descarga el excedente a archivos temporales.

---

#### 2.3.4. Estado en Memoria de `IpAttemptLimiter` no Distribuido
* **Ubicación en código:**
  - `fixtrackback/src/main/java/com/fixtrack/infra/security/IpAttemptLimiter.java` (Líneas 25–27)
* **Descripción:** Los intentos fallidos y bloqueos temporales se gestionan en un `ConcurrentHashMap` local en memoria RAM.
* **Impacto:**
  - Si la aplicación se escala a dos o más instancias/contenedores detrás de un balanceador de carga, los atacantes pueden rotar peticiones entre réplicas eludiendo el bloqueo.
  - Cualquier reinicio de la aplicación o del contenedor borra todos los bloqueos activos.
* **Mitigación recomendada:**
  - Externalizar el almacenamiento de contadores de intentos a una instancia de Redis o usar una tabla en base de datos si se requiere persistencia distribuida.

---

### 🔍 2.4. Riesgos Menores y Deuda Técnica

#### 2.4.1. Versiones Anómalas en `pom.xml`
* **Ubicación en código:**
  - `fixtrackback/pom.xml` (Líneas 8 y 30)
* **Descripción:** El archivo `pom.xml` declara `spring-boot-starter-parent` en versión `4.1.1` y `<java.version>25</java.version>`.
* **Impacto:** Spring Boot 4 aún no existe como versión de producción (la versión GA estable del mercado es Spring Boot 3.4.x). Declarar versiones atípicas o experimentales puede provocar fallos de resolución en entornos CI/CD estándar e incompatibilidad con herramientas de análisis estático (SonarQube, Snyk, Dependabot).
* **Mitigación recomendada:**
  - Establecer una versión de soporte a largo plazo (LTS) probada, como Spring Boot `3.4.x` con Java `21` o `25`.

---

#### 2.4.2. Políticas de Contraseña Básicas
* **Ubicación en código:**
  - `fixtrackback/src/main/java/com/fixtrack/person/dto/ChangePasswordRequest.java` (Línea 6)
  - `fixtrackback/src/main/java/com/fixtrack/person/dto/PersonRequest.java` (Línea 23)
* **Descripción:** La validación de contraseñas únicamente verifica `@Size(min = 8, max = 72)`, sin exigir combinación de números, letras mayúsculas, minúsculas o símbolos especiales.
* **Mitigación recomendada:**
  - Incorporar una validación con expresión regular o validador personalizado (e.g. `@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)...")`).

---

## 3. Puntos Fuertes Identificados en la Arquitectura

Es relevante destacar que el proyecto cuenta con defensas y estándares técnicos sobresalientes:

1. **Cifrado de Alta Calidad para Datos de Desbloqueo:** Los patrones y contraseñas de desbloqueo de los dispositivos de clientes se almacenan cifrados con **AES-GCM (128-bit tag e IV aleatorio de 12 bytes)** en `EncryptedStringConverter.java` y se borran automáticamente al entregar o cancelar la orden.
2. **Control de Concurrencia Robusto:** El inventario implementa bloqueo optimista con `@Version` en `SparePart.java`, y bloqueo pesimista con `@Lock(LockModeType.PESSIMISTIC_WRITE)` en `PartRequestRepository.java` para prevenir condiciones de carrera en el stock.
3. **Manejo Centralizado de Errores:** `GlobalExceptionHandler.java` neutraliza fugas de stack traces internos y estandariza los errores de la API bajo el formato RFC 7807 (`ProblemDetail`).
4. **Calidad de Código en Frontend:** `fixtrack-front` superó el análisis con 0 errores y 0 advertencias en 145 archivos analizados con Oxlint; no presenta usos riesgosos de `dangerouslySetInnerHTML` y mantiene la sesión aislada en `sessionStorage`.

---

## 4. Plan de Acción Priorizado

1. **Inmediato (P0 — Urgente de Seguridad):**
   - Corregir la generación de la `accessKey` para que use siempre números aleatorios seguros de 4 dígitos.
   - Enmascarar la cédula e IMEI en el PDF de consulta pública del portal.
   - Implementar rate limiting en `/api/auth/login`.
   - Activar `server.forward-headers-strategy=framework` en `application.yml`.
2. **Corto Plazo (P1 — Integridad de Datos y Operación):**
   - Proteger las cuentas internas en `PersonServiceImpl.registerOrUpdateCustomer` para que no se sobrescriba el email de empleados.
   - Añadir validación de revocación de JWT tras cambio de contraseña (`passwordChangedAt`).
   - Parametrizar variables de entorno para CORS y conexión a base de datos.
3. **Medio Plazo (P2 — Resiliencia e Infraestructura):**
   - Reemplazar `ddl-auto: update` por Flyway para migraciones SQL.
   - Migrar la exportación de reportes Excel a `SXSSFWorkbook`.

