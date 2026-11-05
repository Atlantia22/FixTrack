# FixTrack — Documentación del sistema

Guía de referencia de FixTrack: cómo se entra, quién puede hacer qué, qué se bloquea y por qué, cómo funcionan las órdenes, el dinero, las garantías y el inventario, y qué hacer cuando algo falla.

Para instalarlo, ver el [README](README.md). Para las reglas de código, ver `.claude/rules/`. Para los riesgos de seguridad, ver [ANALISIS_RIESGOS.md](ANALISIS_RIESGOS.md).

---

## 1. Direcciones y puertos

| Qué | Dirección | Notas |
|---|---|---|
| Aplicación web | http://localhost:5173 | Puerto fijo en desarrollo; el backend acepta los orígenes de `CORS_ALLOWED_ORIGINS`, que por defecto es este |
| Inicio de sesión | http://localhost:5173/login | Para el personal interno |
| Portal del cliente | http://localhost:5173/consulta | Público, sin cuenta; enlazado desde el login con «Consulta tu equipo» |
| API REST | http://localhost:8080/api | Todas las rutas empiezan por `/api` |
| Tiempo real | http://localhost:8080/ws | STOMP sobre WebSocket con SockJS de respaldo (ver sección 15) |
| Base de datos | MySQL 8 en `localhost:3306`, base `fixtrack` | Se crea sola al arrancar |

Toda la aplicación usa la zona horaria **America/Bogota**. «Hoy», las órdenes atrasadas, el tiempo restante de entrega, las no reclamadas, los informes y el cierre de caja se calculan con esa hora, sin importar la hora del equipo del usuario.

---

## 2. Acceso

### 2.1 Quién inicia sesión

| Tipo de persona | ¿Inicia sesión? | Cómo accede |
|---|---|---|
| Administrador, coordinador, asesor y técnico | Sí | Correo y contraseña en `/login` |
| Cliente | No | Código de la orden y clave de 4 dígitos en `/consulta` |

Toda falla de inicio de sesión responde lo mismo: «Correo o contraseña incorrectos.». No revela si el correo existe, si es de un cliente o si el usuario está dado de baja.

### 2.2 Cuentas iniciales

El backend crea estas cuentas al arrancar, solo si no existen y solo si su variable de entorno tiene valor:

| Cuenta | Correo por defecto | Contraseña | Se crea cuando |
|---|---|---|---|
| Administrador inicial | `admin@fixtrack.local` (`ADMIN_EMAIL`) | `ADMIN_PASSWORD` | No hay ningún administrador activo |
| Coordinador de prueba | `coordinador@fixtrack.local` | `DEMO_USERS_PASSWORD` | La variable tiene valor |
| Asesor de prueba | `asesor.prueba@fixtrack.local` | `DEMO_USERS_PASSWORD` | La variable tiene valor |
| Técnico de prueba | `tecnico@fixtrack.local` | `DEMO_USERS_PASSWORD` | La variable tiene valor |

Los **datos de ejemplo** (marcas, modelos, repuestos, clientes y órdenes) solo se cargan con `SEED_DATA=true`, en una base sin marcas y con `DEMO_USERS_PASSWORD` definido. En producción, deje vacías `DEMO_USERS_PASSWORD` y `SEED_DATA`.

> Ninguna contraseña vive en el código ni en el repositorio. Si una base se pobló antes del 3 de noviembre de 2026 con datos de ejemplo, sus cuentas pueden tener la contraseña fija antigua: cámbiela o dé de baja esas cuentas.

### 2.3 Contraseñas

- Entre 8 y 72 caracteres. Se guardan con bcrypt y nunca en texto plano ni en los registros.
- Cada usuario cambia la suya en **Mi perfil → Cambiar contraseña**: debe escribir la actual y la nueva tiene que ser distinta.
- El administrador cambia la de cualquier persona en **Personal**.
- No hay recuperación por correo (fuera del alcance). Si alguien olvida su contraseña, el administrador le asigna una nueva.
- Si el único administrador olvida su contraseña: dé de baja esa cuenta en la base de datos (`active = 0`) y reinicie el backend con `ADMIN_PASSWORD`, un `ADMIN_EMAIL` y un `ADMIN_DOCUMENT_NUMBER` que no usen otras personas. La cuenta vieja conserva su correo y su documento, y si se repiten, no se crea el administrador nuevo.

### 2.4 La sesión

| Regla | Detalle |
|---|---|
| Duración | 8 horas por defecto (`JWT_EXPIRATION`, por ejemplo `8h` o `30m`) |
| Dónde se guarda | `sessionStorage` del navegador: cerrar el navegador cierra la sesión |
| Cerrar sesión a propósito | El siguiente usuario entra al Inicio, no a la página del anterior |
| La sesión vence | El login avisa que la sesión expiró y, al volver a entrar, lleva a la misma página |

La sesión **se corta de inmediato** (responde 401 y lleva al login) cuando:

| Motivo | Mensaje |
|---|---|
| El token venció o no es válido | «La sesión expiró o no es válida; inicie sesión de nuevo» |
| El usuario fue dado de baja o le cambiaron el rol | «Su usuario fue desactivado o cambió de rol; inicie sesión de nuevo» |
| Su contraseña cambió después de iniciar sesión | «Su contraseña cambió; inicie sesión de nuevo» |

Quien cambia su propia contraseña conserva la sesión del equipo donde la cambió, porque recibe un token nuevo. Las sesiones abiertas en otros equipos se cierran.

---

## 3. Bloqueos y límites

| Qué se bloquea | Cuándo | Por cuánto | Qué ve el usuario | Cómo se levanta |
|---|---|---|---|---|
| **Login desde una IP** | 10 intentos fallidos en 15 minutos | Hasta que los fallos salgan de la ventana de 15 minutos | «Demasiados intentos fallidos desde su conexión. Espere unos minutos e intente de nuevo.» (429) | Esperar; reiniciar el backend lo borra |
| **Portal desde una IP** | 10 consultas fallidas en 15 minutos | Igual que el login | «Demasiados intentos desde su conexión…» (429) | Igual que el login |
| **Código de una orden en el portal** | 5 claves erradas seguidas sobre el mismo código | 15 minutos, aunque luego llegue la clave correcta | El mismo mensaje de «no encontramos una orden con ese código y clave» | Esperar 15 minutos. Se guarda en la base de datos, así que reiniciar no lo borra |
| **Cambio de la propia contraseña** | 10 contraseñas actuales erradas en 15 minutos, por usuario | Hasta que los fallos salgan de la ventana | «Demasiados intentos con la contraseña actual…» (429) | Esperar; reiniciar el backend lo borra |

Detalles:

- Los contadores del login y del portal son **independientes**: bloquear el login no afecta el portal, y al revés.
- **No se bloquean cuentas por correo**, a propósito: así nadie puede dejar al administrador sin acceso escribiendo mal su clave.
- El bloqueo por IP vive en memoria: sirve con una sola instancia del backend.
- Detrás de un proxy inverso todas las peticiones llegan con la IP del proxy. En ese caso configure `server.forward-headers-strategy: native`; nunca `framework`, que aceptaría cabeceras `X-Forwarded-For` falsas.
- En una oficina con una sola IP pública, 10 fallos de login entre todos los empleados bloquean a todos por unos minutos.

### Otros límites

| Límite | Valor |
|---|---|
| Rango de fechas de un informe | Máximo 366 días |
| Tamaño de página por defecto de los listados | 20 registros |
| Cierre de caja | Uno por asesor y por día; no se edita |
| Decimales en cantidades | Se rechazan (`1.5` unidades responde 400) |
| Dinero | Máximo 2 decimales |

---

## 4. Roles y permisos

### 4.1 Jerarquía

```
ADMIN ──► COORDINATOR ──► ADVISOR
  └────► TECHNICIAN
```

- El **administrador** puede todo lo del coordinador y lo del técnico.
- El **coordinador** puede todo lo del asesor.
- El **asesor** y el **técnico** son ramas separadas.

### 4.2 Qué hace cada uno

| Acción | Asesor | Técnico | Coordinador | Admin |
|---|:-:|:-:|:-:|:-:|
| Registrar clientes y equipos, recibir equipos y abrir órdenes | ✔ | | ✔ | ✔ |
| Asignar el técnico | ✔ | | ✔ | ✔ |
| Ver la contraseña de desbloqueo del equipo | ✔ | Solo de sus órdenes | ✔ | ✔ |
| Diagnóstico, solución, daños adicionales y mano de obra | | Sus órdenes | | ✔ |
| Registrar y corregir repuestos usados | | Sus órdenes | | ✔ |
| Cambiar el estado de la orden | | Sus órdenes | | ✔ |
| Reprogramar la fecha y hora estimada de entrega | ✔ | Sus órdenes | ✔ | ✔ |
| Registrar la respuesta del cliente sobre daños adicionales | ✔ | | ✔ | ✔ |
| Registrar abonos y cobro final, entregar, cancelar | ✔ | | ✔ | ✔ |
| Abrir garantías | ✔ | | ✔ | ✔ |
| Reimprimir el comprobante de ingreso (genera clave nueva) | ✔ | | ✔ | ✔ |
| Cierre de caja propio | ✔ | | ✔ | ✔ |
| Solicitar repuestos que no hay en inventario | | Sus órdenes | | ✔ |
| Agregar al inventario un repuesto nuevo desde la orden («Otro»): conseguido por fuera o para pedirlo | | Sus órdenes | | |
| Inventario, entradas, catálogos, proveedores y solicitudes | | | ✔ | ✔ |
| Personal, roles, especialidades y parámetros | | | | ✔ |
| Ver la ganancia | | De sus órdenes | De todas | De todas |
| Cambiar su disponibilidad (disponible, ocupado, ausente) | | ✔ | | |

El técnico solo ve las órdenes asignadas a él. Si intenta abrir otra, el backend responde 403 «Solo puede consultar las órdenes asignadas a usted».

### 4.3 Menú por rol

| Opción | Ruta | Asesor | Técnico | Coordinador | Admin |
|---|---|:-:|:-:|:-:|:-:|
| Inicio | `/` | ✔ | ✔ | ✔ | ✔ |
| Órdenes / Mis órdenes | `/ordenes` | ✔ | ✔ | ✔ | ✔ |
| Salida | `/salida` | ✔ | | ✔ | ✔ |
| Clientes y equipos | `/clientes` | ✔ | | ✔ | ✔ |
| Caja | `/caja` | ✔ | | ✔ | ✔ |
| Inventario | `/inventario` | | | ✔ | ✔ |
| Solicitudes de repuestos / Mis solicitudes | `/solicitudes` | | ✔ | ✔ | ✔ |
| Proveedores | `/proveedores` | | | ✔ | ✔ |
| Catálogos | `/catalogos` | | | ✔ | ✔ |
| Personal | `/personas` | | | | ✔ |
| Informes / Mis ganancias | `/informes` | | ✔ | ✔ | ✔ |
| Parámetros | `/parametros` | | | | ✔ |
| Mi perfil | `/perfil` | ✔ | ✔ | ✔ | ✔ |

Ocultar una opción del menú es solo comodidad: cada endpoint del backend valida el rol con `@PreAuthorize` y la pertenencia en el servicio. Sin permiso responde 403; una dirección inexistente muestra «Página no encontrada».

### 4.4 Personal y bajas

- Nada con historial se borra. Dar de baja a una persona la deja inactiva, y sus órdenes, pagos y movimientos la siguen mostrando.
- No se puede dar de baja al **único administrador activo**.
- Una persona dada de baja no puede iniciar sesión, y su sesión abierta se corta en la siguiente petición.
- Marcas, modelos, tipos de repuesto y especialidades se eliminan solo si nada los usa. Si están en uso, el sistema responde 409 con el motivo.

---

## 5. Clientes

- El **tipo y número de documento** son obligatorios y son la forma de reconocer a un cliente que regresa. El número se valida según el tipo, en el front y en el backend (también para el personal):

  | Tipo | Formato |
  |---|---|
  | Cédula de ciudadanía | Solo números, de 6 a 10 |
  | Tarjeta de identidad | Solo números, 10 u 11 |
  | Cédula de extranjería y PPT | Solo números, de 6 a 12 |
  | Pasaporte | Letras y números, de 6 a 15 |

  El campo no deja escribir letras en los tipos numéricos ni pasar del máximo, y al cambiar el tipo ajusta el número a lo que ese tipo admite.
- **Celular** (7 a 10 dígitos, puede repetirse entre familiares) y **correo** (único) son opcionales, pero se llenan o se marca «No tiene». Vacíos se muestran como `NT`.
- Cuando un cliente regresa, se reutiliza su registro y solo se actualizan el celular y el correo.
- Si el documento pertenece a un **empleado** que trae su equipo, el ingreso actualiza su celular, pero **no su correo**: con ese correo inicia sesión, y solo el administrador lo cambia en Personal.
- Desde **Clientes y equipos**, el asesor corrige el celular y el correo de un cliente. El administrador puede editar todos sus datos.

---

## 6. Órdenes

### 6.1 Código

- Se arma con el id: `FT-` + 5 dígitos (`FT-00001`). Desde el id 100000 crece sin recortarse.
- Las garantías llevan el código de la original más `-G` y su número: `FT-00001-G1`, `FT-00001-G2`.
- El código no se guarda en la base de datos: se calcula, así que nunca hay duplicados.

### 6.2 Estados

| Estado | Significado | Color |
|---|---|---|
| `RECEIVED` Recibido | El equipo llegó a la tienda | Gris |
| `IN_DIAGNOSIS` En diagnóstico | El técnico lo revisa | Azul |
| `WAITING_PARTS` Esperando repuestos | Falta un repuesto | Naranja |
| `IN_REPAIR` En reparación | El técnico lo repara | Morado |
| `READY` Listo para entregar | Terminado, espera al cliente | Verde |
| `DELIVERED` Entregado | El cliente se lo llevó | Verde oscuro |
| `CANCELLED` Cancelado | Se canceló | Rojo |

Transiciones permitidas:

```
RECEIVED ──► IN_DIAGNOSIS ──► IN_REPAIR ──► READY ──► DELIVERED
                  │              ▲   │
                  ▼              │   ▼
             WAITING_PARTS ──────┘ WAITING_PARTS

Cancelar: desde cualquier estado excepto DELIVERED.
```

- Para avanzar, la orden necesita un técnico asignado; un técnico **ausente** no recibe órdenes.
- Para pasar a `READY` hay que marcar el resultado, y no puede haber daños adicionales esperando la respuesta del cliente.
- La entrega y la cancelación tienen su propia acción; no se hacen cambiando el estado.

### 6.3 Resultados

| Resultado | Qué pasa |
|---|---|
| Reparado | Se cobran la mano de obra y los repuestos, y queda la garantía |
| No reparable / Rechazado por el cliente | Se cobra solo el valor del diagnóstico (puede ser 0), sin repuestos ni garantía; la orden solo puede pasar a `READY` y ya no se reabre |

### 6.4 Fecha y hora estimada de entrega

- Al abrir la orden (y la garantía) se piden la **fecha** y la **hora** acordadas con el cliente. Pueden ser el mismo día, pero deben quedar **después del momento del ingreso**; si no, el backend responde 400 «La fecha y hora estimada de entrega debe ser posterior al momento del ingreso». El ingreso es el momento exacto en que se registra la orden.
- Se reprograma (fecha y hora) con un motivo; queda registro de quién, cuándo, la fecha y hora anterior y la nueva. Solo en órdenes abiertas, lo hace el asesor (o quien tenga sus permisos) o el técnico asignado, y la nueva fecha y hora debe ser posterior al momento actual y distinta de la vigente; si no, el backend responde «La nueva fecha y hora de entrega debe ser posterior a este momento» o «La nueva fecha y hora es igual a la entrega estimada actual».
- La hora se agregó el 5 de noviembre de 2026: el script `fixtrackback/db/migration/2026-11-05_add_estimated_delivery_time.sql` crea las columnas `estimated_delivery_time` (orden) y `previous_time` / `new_time` (reprogramaciones), con su reversión comentada. Con `DDL_AUTO=update` Hibernate las crea solo.
- Una orden está **atrasada** desde el **minuto** en que se cumple su fecha y hora sin estar lista, entregada ni cancelada.
- Las órdenes creadas antes de que existiera la hora conservan su fecha y tienen la hora vacía; para ellas sigue la regla anterior: atrasadas desde el día siguiente a la fecha.
- La columna **Tiempo restante** del listado de órdenes (y el detalle) se calcula en el navegador cada minuto; no se guarda:

| Situación | Texto | Color |
|---|---|---|
| Falta más de un día | «2 d 5 h» | Verde |
| Faltan 24 horas o menos | «5 h 20 min» | Amarillo |
| Faltan 2 horas o menos | «1 h 10 min», «35 min» | Rojo |
| Venció | «Vencido hace 3 h» | Rojo oscuro, y la fila lleva la etiqueta «Atrasada» |
| Orden antigua sin hora | «Sin hora» o «Vencido (sin hora)» | Gris |
| Lista, entregada o cancelada | «Listo para entregar», «Entregado», «Cancelada»; el contador se detiene | Gris |

- El listado se puede ordenar por **Tiempo restante: más urgentes primero** (solo abiertas, las vencidas arriba).
- Una orden **no reclamada** lleva en `READY` más días que los del parámetro `UNCLAIMED_ALERT_DAYS` (30 por defecto). Aparece resaltada en Salida, con el contacto del cliente.

### 6.5 Contraseña o patrón de desbloqueo

- En el ingreso y en la garantía se elige **Contraseña o PIN** (se escribe) o **Patrón** (se dibuja en una cuadrícula de 3 × 3 con el mouse, el dedo o el teclado). El patrón se guarda como la secuencia de puntos del 1 al 9, por filas, separada por guiones: `1-5-9-6`. Necesita al menos 4 puntos y, si el trazo salta un punto (de 1 a 3), agrega el de en medio, como en el teléfono.
- En el detalle de la orden el patrón se muestra como texto y, al pulsar «mostrar», dibujado con el número de cada paso.
- Se guarda cifrada con AES-GCM (llave en `ENCRYPTION_KEY`).
- Solo la ven el asesor, el coordinador, el administrador y el técnico asignado.
- Se **borra** al entregar o cancelar la orden.

> Si se pierde o se cambia `ENCRYPTION_KEY`, las contraseñas de desbloqueo ya guardadas no se pueden leer.

### 6.6 Daños reportados y daños adicionales

Al recibir el equipo, el asesor agrega **todos los daños que reporta el cliente** (al menos uno) desde una lista con buscador. Cada uno queda como daño inicial aprobado, y todos salen en el comprobante de ingreso («Daños reportados»).

Si el técnico encuentra un daño nuevo:

1. Lo agrega a la orden.
2. Si el total supera el presupuesto, el daño queda **pendiente** y el asesor registra si el cliente lo aprueba o lo rechaza.
3. Si el total no supera el presupuesto, queda aprobado solo.

Lo que respondió el asesor no lo vuelve a tocar el sistema.

### 6.7 Comprobantes

- Al terminar el ingreso, el botón **Imprimir comprobante** abre directamente el diálogo de impresión del navegador; «Ver PDF» solo lo muestra.
- La reimpresión del ingreso (con clave nueva) y el comprobante de entrega también se imprimen directo; el de entrega además se puede descargar.
- Los comprobantes muestran la fecha y hora estimada de entrega (solo la fecha en las órdenes antiguas).

---

## 7. Portal del cliente y clave de consulta

### 7.1 Cómo consulta el cliente

En `/consulta` escribe el **código** de la orden (o de la garantía) y su **clave de 4 dígitos**. Ve:

- El estado, con la línea de tiempo de cambios (sin notas ni nombres del personal).
- Equipo y fechas, con la **fecha y hora** estimada de entrega.
- **Un solo valor**, sin desglose: «Valor (presupuesto)» mientras el técnico no da el precio y «Valor final» después.
- La página se actualiza sola cuando el taller cambia el estado o la entrega (sección 15).
- Las garantías, que se consultan con el código y la clave de la orden original.
- Un botón «Escuchar», que lee el estado en voz alta.
- La descarga de los comprobantes.

### 7.2 La clave de consulta

| Regla | Detalle |
|---|---|
| Cuál es | Los últimos 4 dígitos del celular del cliente o, si no tiene celular, 4 dígitos al azar |
| Cuándo se asigna | Al abrir la orden; no cambia aunque el cliente cambie su celular |
| Cómo se guarda | Solo con hash: nadie, ni el administrador, puede verla después |
| Si el cliente la pierde | El asesor **reimprime el comprobante de ingreso**: se genera una clave nueva al azar y la anterior deja de servir |
| Sin celular ni correo | El comprobante advierte «Conserve este comprobante, no podremos contactarlo» |

### 7.3 Datos que oculta el portal

Los comprobantes que el cliente descarga desde el portal **enmascaran** sus datos de identidad. Los que se imprimen en el mostrador salen completos.

| Dato | Mostrador | Portal |
|---|---|---|
| Documento | `CC 1067789012` | `CC *******012` |
| Celular | `3012223344` | `********44` |
| Correo | `valentina@gmail.com` | `v***@gmail.com` |
| IMEI | `354567890123455` | `***********3455` |
| Número de serie | completo | últimos 4 caracteres |

---

## 8. Equipos

- Tipos: teléfono, tablet, reloj inteligente, parlante y otro.
- El **IMEI** es obligatorio solo en los teléfonos; los demás se identifican por número de serie cuando lo tienen.
- El estado físico se registra al recibir el equipo y sale en el comprobante.

---

## 9. Dinero

### 9.1 Cálculos

| Concepto | Fórmula |
|---|---|
| Total de la orden | Mano de obra + Σ (cantidad × precio de venta de cada repuesto, guardado al momento del uso) |
| Costo de repuestos | Σ (cantidad × costo guardado al momento del uso) |
| Ganancia | Total − costo de repuestos (se calcula, no se guarda) |

- La ganancia cuenta solo las órdenes **entregadas**; las canceladas no cuentan.
- Las no reparables cuentan con el valor del diagnóstico.
- Las garantías van a precio 0 y su costo lo asume la tienda.
- El cliente y el asesor ven el presupuesto y el total, sin desglose. El técnico ve la ganancia de sus órdenes; el coordinador y el administrador, la de todas.
- Moneda: pesos colombianos (COP), con 2 decimales y redondeo hacia arriba en la mitad (`HALF_UP`).

**El precio de venta del repuesto incluye la instalación.** Lo que se cobra por un repuesto es el repuesto ya puesto; por eso la mano de obra de la orden es **mano de obra adicional** y puede quedar en 0. El técnico, el coordinador y el administrador ven el desglose:

| Renglón | Qué es |
|---|---|
| Repuestos (venta con instalación) | Σ cantidad × precio de venta |
| Mano de obra adicional | Trabajo extra que define el técnico |
| Ganancia en repuestos e instalación | Venta de repuestos − costo de repuestos |
| Ganancia por mano de obra adicional | La mano de obra adicional |
| Ganancia total | Total − costo de repuestos |

Al escribir la mano de obra, el formulario dice cuánto puede subir **sin superar el presupuesto** dado al cliente (o cuánto lo supera), y el botón «Completar hasta el presupuesto» la deja justa.

**Valor que se muestra.** En el detalle de la orden y en el portal hay un solo valor: el **presupuesto** mientras el técnico no ha puesto mano de obra ni repuestos («Se actualiza cuando el técnico dé el precio final»), y el **precio final** después, o cuando la orden está lista, entregada o cancelada. Mientras se muestra el presupuesto, el saldo se calcula contra él («Saldo estimado») y no aparece un «saldo a favor» falso.

### 9.2 Pagos

| Tipo | Cuándo |
|---|---|
| Abono (`DEPOSIT`) | Mientras la orden está abierta, incluso al recibirla |
| Cobro final (`FINAL`) | Cuando la orden está lista; no puede superar el saldo |
| Devolución (`REFUND`) | Cuando se abonó de más, por ejemplo al cancelar |

- Medios: efectivo, transferencia y tarjeta.
- Para **entregar**, el saldo debe estar en cero; si no, el sistema dice cuánto falta.
- Las garantías no reciben pagos.

### 9.3 Cierre de caja

- Cada asesor cierra **una vez al día** con el efectivo contado; el sistema calcula el esperado y la diferencia.
- Un cierre no se edita.
- Si no tiene pagos pendientes de cierre, el efectivo contado debe ser 0.

---

## 10. Garantías

- Se abren solo sobre una orden **original entregada** con garantía vigente; nunca sobre otra garantía.
- Los días salen de la orden original (por defecto `DEFAULT_WARRANTY_DAYS`, 30 días).
- No la extienden: la garantía de la original sigue venciendo en la misma fecha.
- Siguen los mismos estados que una orden, con mano de obra, diagnóstico y repuestos a precio 0.
- Al abrirla se pueden actualizar los datos de contacto del cliente.

---

## 11. Inventario y repuestos

- Cada **calidad** (original, OEM, genérico) es un repuesto distinto con su propio SKU, stock y precio.
- El **SKU** es el código único de cada repuesto, con el formato `TIPO-MODELO-CALIDAD` (`PAN-A54-ORI`: pantalla, Galaxy A54, original). El coordinador lo escribe al crear el repuesto; cuando el técnico lo agrega con «Otro», se genera solo con ese formato y, si ya existe, se numera (`-2`, `-3`).
- El precio de venta se registra con la instalación incluida (sección 9.1).
- Las listas de repuestos, tipos, marcas, modelos y proveedores tienen **buscador**: se escribe para filtrar, sin importar mayúsculas ni tildes.
- Estado del stock (se calcula): **Agotado** sin unidades, **Bajo** en el mínimo o por debajo, **OK** por encima.
- El stock nunca queda negativo y está protegido contra cambios simultáneos.
- Todo movimiento queda registrado con su saldo:

| Movimiento | Cuándo |
|---|---|
| `IN_PURCHASE` Entrada | Compra de mercancía, con costo, motivo y responsable |
| `OUT_REPAIR` Salida | Un técnico usa un repuesto en una orden |
| `RETURN` Devolución | Se corrige un consumo a la baja o se cancela la orden |
| `ADJUSTMENT` Ajuste | Reservado; hoy ninguna pantalla lo usa |

- Después de que la orden pasa a `READY`, sus repuestos ya no cambian.

### Repuesto que no está en el inventario («Otro»)

Al registrar un repuesto usado, si no existe, el técnico elige **«¿No está? Agregar otro repuesto»**. Se despliega un formulario (tipo, calidad, nombre, costo y precio de venta) con dos opciones:

| Opción | Qué pasa |
|---|---|
| **Ya lo tengo** (lo consiguió por otro lado) | El repuesto se crea para el modelo del equipo y entra al inventario con la **cantidad conseguida** (movimiento `IN_PURCHASE` «Conseguido por el técnico para la orden FT-…», a su nombre). En la orden se usa la cantidad indicada y lo que sobre queda en stock |
| **Pedirlo al coordinador** | El repuesto se crea sin stock y queda una solicitud; la orden pasa a «Esperando repuestos». Al recibirlo, entra al inventario y se carga a la orden |

Si ya existe un repuesto con el mismo tipo, modelo y calidad, no se duplica: el sistema pide elegirlo de la lista.

### Solicitudes de repuestos

```
PENDING ──► APPROVED ──► ORDERED ──► RECEIVED
   │            │           │
   └────────────┴───────────┴──► REJECTED / CANCELLED
```

Al recibir el repuesto, en una sola operación: entra al inventario, se registra como usado en la orden (si sigue en trabajo) al costo real y se guarda como último costo del proveedor. Si la orden se cancela, sus solicitudes sin pedir se cancelan solas.

Cuando un repuesto baja de su mínimo o se agota, el coordinador y el administrador ven un aviso emergente en cualquier pantalla, y el panel de alertas de Inventario se actualiza solo.

---

## 12. Parámetros del sistema

Los cambia el administrador en **Parámetros** (`/parametros`):

| Parámetro | Por defecto | Rango | Para qué |
|---|---|---|---|
| `DEFAULT_MIN_STOCK` | 2 | 0 a 1000 | Stock mínimo de un repuesto nuevo si no se indica otro |
| `DEFAULT_WARRANTY_DAYS` | 30 | 0 a 3650 | Días de garantía si el técnico no indica otros |
| `UNCLAIMED_ALERT_DAYS` | 30 | 1 a 3650 | Días en «Listo para entregar» para considerar una orden no reclamada |
| `SERVICE_TERMS` | Condiciones por defecto | hasta 5000 caracteres | Condiciones del servicio impresas en el comprobante, una por línea |

---

## 13. Variables de entorno

### Backend (`fixtrackback/.env`)

| Variable | Obligatoria | Por defecto | Uso |
|---|---|---|---|
| `DB_PASSWORD` | Sí | — | Contraseña de MySQL |
| `DB_URL` | No | `jdbc:mysql://localhost:3306/fixtrack?...` | Conexión a la base |
| `DB_USERNAME` | No | `root` | Usuario de MySQL |
| `ENCRYPTION_KEY` | Sí | — | Llave AES de 32 bytes en Base64 (contraseñas de desbloqueo) |
| `JWT_SECRET` | Sí | — | Llave HMAC de al menos 32 bytes en Base64 (sesiones) |
| `JWT_EXPIRATION` | No | `8h` | Duración de la sesión |
| `ADMIN_PASSWORD` | La primera vez | — | Contraseña del administrador inicial |
| `ADMIN_EMAIL` | No | `admin@fixtrack.local` | Correo del administrador inicial |
| `ADMIN_DOCUMENT_NUMBER` | No | `0000000000` | Documento del administrador inicial |
| `DEMO_USERS_PASSWORD` | No | vacío | Crea los usuarios de prueba |
| `SEED_DATA` | No | `false` | Carga datos de ejemplo (exige `DEMO_USERS_PASSWORD`) |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:5173` | Orígenes del front, separados por coma |
| `DDL_AUTO` | No | `update` | Esquema: `update` en desarrollo, `validate` en producción |
| `PORTAL_URL` | No | `http://localhost:5173/consulta` | Dirección del portal impresa en el comprobante |
| `SHOW_SQL` | No | `false` | Muestra el SQL en la consola para depurar |

Las llaves se generan con `openssl rand -base64 32`.

> Cambiar `JWT_SECRET` cierra todas las sesiones. Cambiar `ENCRYPTION_KEY` deja ilegibles las contraseñas de desbloqueo guardadas.

### Frontend (`fixtrack-front/.env`)

| Variable | Por defecto | Uso |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080/api` | Dirección de la API |
| `VITE_WS_URL` | La de la API sin `/api` + `/ws` | Canal en tiempo real; solo si vive en otra dirección |

Los `.env` están en `.gitignore` y nunca se suben. Solo se suben los `.env.example`, sin valores.

---

## 14. Respuestas de la API

Los errores llegan como `ProblemDetail` (RFC 9457), con `detail` en español y, en los errores de validación, la lista de campos (`errors`). Nunca incluyen trazas ni SQL.

| Código | Significado | Ejemplo |
|---|---|---|
| 200 / 201 / 204 | Correcto, creado, sin contenido | — |
| 400 | Datos inválidos | Campo vacío, fecha y hora de entrega anterior al ingreso, documento con letras en una cédula, `?sort=` con un campo inexistente, caracteres mal codificados |
| 401 | Sin sesión o sesión no válida | Token vencido, usuario dado de baja, contraseña cambiada |
| 403 | Sin permiso | Un técnico abre una orden que no es suya |
| 404 | No existe | Orden o persona inexistente; en el portal, código o clave errados |
| 409 | Choca con una regla de negocio | Entregar con saldo pendiente, abrir garantía vencida, borrar una marca en uso, cambio simultáneo del mismo registro |
| 429 | Demasiados intentos | Login o portal bloqueados por IP |
| 500 | Error inesperado | Se registra en el log del servidor |

### Rutas públicas

Solo estas rutas funcionan sin token:

| Método | Ruta | Para qué |
|---|---|---|
| POST | `/api/auth/login` | Iniciar sesión |
| POST | `/api/public/interventions/lookup` | Consultar una orden en el portal |
| POST | `/api/public/interventions/receipts/intake` | Comprobante de ingreso (enmascarado) |
| POST | `/api/public/interventions/receipts/delivery` | Comprobante de entrega (enmascarado) |
| GET | `/ws/**` | Conexión al canal en tiempo real; la sesión se valida después, al conectar por STOMP (sección 15) |

Todo lo demás bajo `/api` exige sesión y su rol. Cualquier otra ruta se rechaza.

---

## 15. Tiempo real

Las pantallas se actualizan solas cuando otra persona cambia algo, sin recargar. Usa STOMP sobre WebSocket en `/ws`, con SockJS de respaldo, y se conecta al iniciar sesión y se corta al cerrarla.

| Qué cambia | Qué se actualiza |
|---|---|
| Estado, técnico, fecha y hora de entrega de una orden | La fila en Órdenes (solo esa fila), el detalle abierto, Inicio y Salida |
| Repuestos usados, mano de obra, daños o pagos de una orden | El detalle abierto, sus pestañas, Salida e Inicio |
| Stock de un repuesto | La fila en Inventario y los repuestos disponibles al registrar uno en una orden |
| Un repuesto baja del mínimo o se agota | Aviso emergente al coordinador y al administrador, y el panel de alertas |
| Solicitudes de repuestos | La bandeja del coordinador, «Mis solicitudes» del técnico y la orden |
| Clientes y personal | Clientes y equipos, Personal y la disponibilidad de los técnicos en Inicio |
| La orden del cliente | El portal, si el cliente tiene su orden abierta |

| Canal (topic) | Quién puede escucharlo |
|---|---|
| `/topic/spare-parts`, `/topic/alerts/stock` | Todo el personal |
| `/topic/interventions`, `/topic/part-requests` | Asesor, coordinador y administrador |
| `/user/queue/interventions`, `/user/queue/part-requests` | Cola privada de cada técnico: solo sus órdenes y sus solicitudes (si le quitan una orden, recibe el aviso para sacarla de su lista) |
| `/topic/persons`, `/topic/devices` | Asesor, coordinador y administrador (llevan datos de clientes) |
| `/topic/orders/{código}` | Público, pero exige el código de la orden original **y su clave de consulta**, con el mismo conteo de intentos del portal |

Reglas:

- El token de la sesión se valida al conectar, con las mismas reglas de la API (sección 2.4). Si vence, o si la persona es dada de baja o le cambian el rol o la contraseña, el servidor deja de enviar avisos a esa conexión al instante.
- Los avisos son «delgados»: dicen qué cambió y su id, sin dinero, contraseñas, documentos ni contactos. Cada pantalla vuelve a pedir por la API lo que su rol puede ver.
- Solo se avisa después de que el cambio se guarda: si una operación falla, nadie recibe el aviso.
- El navegador no puede enviar mensajes por este canal; solo escucha.
- Los orígenes permitidos son los de `CORS_ALLOWED_ORIGINS`.

### Cómo comprobarlo

1. Abra dos navegadores (o una ventana normal y una privada) e inicie sesión con dos usuarios distintos, por ejemplo el asesor y el técnico de prueba.
2. Con el técnico, cambie el estado de una orden: en la ventana del asesor la fila de Órdenes y el Inicio cambian sin recargar.
3. Reprograme una orden para dentro de 10 minutos: la columna **Tiempo restante** se actualiza en ambas ventanas, baja cada minuto, pasa a rojo y al cumplirse dice «Vencido hace…».
4. Registre en una orden un repuesto que deje el stock en su mínimo: el coordinador ve el aviso emergente y el panel de alertas de Inventario se actualiza.
5. Abra la orden en el portal (`/consulta`) con su código y clave y cambie el estado desde el sistema: el portal se actualiza solo.

Las pruebas automáticas del canal están en `fixtrackback/src/test/java/com/fixtrack/config/websocket` (incluida una de integración con un cliente STOMP real) y las del tiempo restante en `fixtrack-front/src/utils/timeRemaining.test.js`.

---

## 16. Seguridad en resumen

| Tema | Cómo se protege |
|---|---|
| Contraseñas de usuarios y clave de consulta | bcrypt |
| Contraseña de desbloqueo del equipo | AES-GCM, se borra al entregar o cancelar |
| Sesión | JWT HS256 firmado, revalidado contra la base en cada petición |
| Fuerza bruta | Límite por IP en login y portal, más bloqueo por código en el portal |
| Permisos | `@PreAuthorize` en cada endpoint y pertenencia validada en el servicio |
| Inyección SQL | Solo consultas con parámetros; búsquedas con comodines escapados |
| XSS | React escapa todo; no se usa `dangerouslySetInnerHTML` |
| Datos personales en el portal | Comprobantes enmascarados; la consulta no muestra documento, contactos ni IMEI |
| Canal en tiempo real | Token validado al conectar, permiso por rol en cada canal, colas privadas para el técnico, avisos sin datos sensibles, corte inmediato al perder el acceso y canal del portal con código y clave |
| Cabeceras | La API envía `Content-Security-Policy`, `Referrer-Policy`, `Permissions-Policy`, `X-Frame-Options` y `X-Content-Type-Options`; el build del front lleva su propia CSP |
| Orden de los listados | Solo por los campos permitidos de cada listado; cualquier otro responde 400 |
| Datos de entrada | Bean Validation en el backend; documento validado por tipo (sección 5) |
| Secretos | Solo variables de entorno; `.env` fuera del repositorio |

Para producción falta: HTTPS en el proxy, `frame-ancestors` en las cabeceras del servidor que sirva el front, `DDL_AUTO=validate` y revisar los riesgos que quedan en [ANALISIS_RIESGOS.md](ANALISIS_RIESGOS.md) (sección 8.3).

---

## 17. Accesibilidad

Botón fijo de accesibilidad en el login, el portal y el sistema interno, con:

- Tamaño de letra.
- Alto contraste: el estado de la orden lleva además un ícono.
- Modo oscuro.
- Reducir animaciones.
- Resaltar el foco.

Los ajustes se guardan en el navegador. Todo se maneja con teclado, las ventanas devuelven el foco al cerrarse y los mensajes se anuncian a los lectores de pantalla.

---

## 18. Problemas frecuentes

| Problema | Causa probable | Solución |
|---|---|---|
| «Demasiados intentos fallidos desde su conexión» | 10 fallos de login en 15 minutos desde esa IP | Esperar unos minutos o reiniciar el backend |
| El cliente no puede consultar aunque la clave es correcta | Hubo 5 claves erradas seguidas en ese código | Esperar 15 minutos |
| El cliente perdió la clave | Solo se guarda con hash | Reimprimir el comprobante de ingreso: genera una clave nueva |
| «La sesión expiró o no es válida» | Pasaron las 8 horas o cambió `JWT_SECRET` | Iniciar sesión de nuevo |
| «Su contraseña cambió; inicie sesión de nuevo» | La contraseña se cambió en otro equipo o la cambió el administrador | Entrar con la contraseña nueva |
| «Su usuario fue desactivado o cambió de rol» | El administrador lo dio de baja o le cambió el rol | Hablar con el administrador |
| No se ve una opción del menú | El rol no tiene ese permiso | Ver la sección 4.3 |
| No se puede entregar la orden | Hay saldo pendiente o no está en `READY` | Registrar el cobro final o terminar la orden |
| No se puede pasar a «Listo» | Falta el resultado o hay daños pendientes de respuesta | Marcar el resultado o registrar la respuesta del cliente |
| No se puede asignar un técnico | Está ausente o dado de baja | Cambiar su disponibilidad o elegir otro |
| No se puede abrir la garantía | La original no está entregada, venció o es otra garantía | Revisar la fecha de garantía de la original |
| El front no conecta con la API | El backend no corre o el origen del front no está en `CORS_ALLOWED_ORIGINS` | Arrancar el backend; en desarrollo usar el puerto 5173 o agregar el origen a la variable |
| Las pantallas no se actualizan solas | El canal en tiempo real no está conectado: el backend se reinició, el origen del front no está en `CORS_ALLOWED_ORIGINS` o la consola del navegador (F12) dice «El servidor rechazó la conexión» | Recargar la página (F5); revisar `CORS_ALLOWED_ORIGINS` |
| No deja guardar la orden: «Debe ser posterior a este momento» | La fecha y hora de entrega ya pasó | Elegir una hora posterior a la actual (puede ser hoy) |
| No deja escribir letras en el documento | El tipo elegido es numérico | Elegir el tipo correcto (el pasaporte sí admite letras) |
| El repuesto no aparece al registrarlo en la orden | No existe en el inventario | Usar «¿No está? Agregar otro repuesto» (sección 11) |
| El backend no arranca: falta `JWT_SECRET` o `ENCRYPTION_KEY` | Falta el `.env` o tiene llaves cortas | Generar las llaves con `openssl rand -base64 32` |
| No se crea el administrador inicial | Falta `ADMIN_PASSWORD`, ya hay un administrador activo o el correo o el documento ya los usa otra persona | Revisar el log de arranque |

---

## 19. Comandos

| Dónde | Comando | Qué hace |
|---|---|---|
| `fixtrackback` | `./mvnw spring-boot:run` | Arranca el backend |
| `fixtrackback` | `./mvnw clean test` | Compila desde cero y corre las pruebas |
| `fixtrack-front` | `npm run dev` | Servidor de desarrollo en el puerto 5173 |
| `fixtrack-front` | `npm run build` | Compila para producción en `dist/` |
| `fixtrack-front` | `npm run lint` | Revisa el código con oxlint |
| `fixtrack-front` | `npm test` | Pruebas de las utilidades (tiempo restante) con el ejecutor de Node |

> Use `clean test` y no solo `test`: si el editor compiló una clase con errores, Maven puede reutilizarla sin avisar.

Los cambios de esquema que `ddl-auto: update` no hace solo (o para aplicarlos a mano con `DDL_AUTO=validate`) están en `fixtrackback/db/migration/`.

---

## 20. Registro de cambios

| Fecha | Cambio |
|---|---|
| 5 de noviembre de 2026 | Cambios sugeridos por el profesor: hora estimada de entrega obligatoria (ingreso, garantía y reprogramación), columna **Tiempo restante** con colores y orden «más urgentes primero», atraso desde el minuto acordado, actualización en tiempo real por WebSocket (sección 15), validación del documento por tipo, varios daños reportados al recibir, patrón de desbloqueo dibujado, repuesto nuevo desde la orden («Otro»), precio de venta con instalación incluida y valor único en el portal. Migración `2026-11-05_add_estimated_delivery_time.sql`. |

Pendiente a la fecha: los catálogos (marcas, modelos, tipos de repuesto, especialidades), los proveedores, los parámetros y los cierres de caja todavía no avisan en tiempo real; quien los tenga abiertos debe recargar para ver cambios de otra persona.
