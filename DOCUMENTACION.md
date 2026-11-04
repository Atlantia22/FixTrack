# FixTrack — Documentación del sistema

Guía de referencia de FixTrack: cómo se entra, quién puede hacer qué, qué se bloquea y por qué, cómo funcionan las órdenes, el dinero, las garantías y el inventario, y qué hacer cuando algo falla.

Para instalarlo, ver el [README](README.md). Para las reglas de código, ver `.claude/rules/`. Para los riesgos de seguridad, ver [ANALISIS_RIESGOS.md](ANALISIS_RIESGOS.md).

---

## 1. Direcciones y puertos

| Qué | Dirección | Notas |
|---|---|---|
| Aplicación web | http://localhost:5173 | Puerto fijo: el backend solo acepta peticiones (CORS) desde ese origen |
| Inicio de sesión | http://localhost:5173/login | Para el personal interno |
| Portal del cliente | http://localhost:5173/consulta | Público, sin cuenta; enlazado desde el login con «Consulta tu equipo» |
| API REST | http://localhost:8080/api | Todas las rutas empiezan por `/api` |
| Base de datos | MySQL 8 en `localhost:3306`, base `fixtrack` | Se crea sola al arrancar |

Toda la aplicación usa la zona horaria **America/Bogota**. «Hoy», las órdenes atrasadas, las no reclamadas, los informes y el cierre de caja se calculan con esa hora.

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
| Reprogramar la fecha estimada de entrega | ✔ | Sus órdenes | ✔ | ✔ |
| Registrar la respuesta del cliente sobre daños adicionales | ✔ | | ✔ | ✔ |
| Registrar abonos y cobro final, entregar, cancelar | ✔ | | ✔ | ✔ |
| Abrir garantías | ✔ | | ✔ | ✔ |
| Reimprimir el comprobante de ingreso (genera clave nueva) | ✔ | | ✔ | ✔ |
| Cierre de caja propio | ✔ | | ✔ | ✔ |
| Solicitar repuestos que no hay en inventario | | Sus órdenes | | ✔ |
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

- El **tipo y número de documento** son obligatorios y son la forma de reconocer a un cliente que regresa.
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

### 6.4 Fecha estimada de entrega

- Es obligatoria al abrir la orden y no puede ser anterior a hoy.
- Se reprograma con un motivo; queda registro de quién, cuándo y las fechas anterior y nueva.
- Una orden está **atrasada** si pasó la fecha y no está lista, entregada ni cancelada.
- Una orden **no reclamada** lleva en `READY` más días que los del parámetro `UNCLAIMED_ALERT_DAYS` (30 por defecto). Aparece resaltada en Salida, con el contacto del cliente.

### 6.5 Contraseña o patrón de desbloqueo

- Se guarda cifrada con AES-GCM (llave en `ENCRYPTION_KEY`).
- Solo la ven el asesor, el coordinador, el administrador y el técnico asignado.
- Se **borra** al entregar o cancelar la orden.

> Si se pierde o se cambia `ENCRYPTION_KEY`, las contraseñas de desbloqueo ya guardadas no se pueden leer.

### 6.6 Daños adicionales

Si el técnico encuentra un daño nuevo:

1. Lo agrega a la orden.
2. Si el total supera el presupuesto, el daño queda **pendiente** y el asesor registra si el cliente lo aprueba o lo rechaza.
3. Si el total no supera el presupuesto, queda aprobado solo.

Lo que respondió el asesor no lo vuelve a tocar el sistema.

---

## 7. Portal del cliente y clave de consulta

### 7.1 Cómo consulta el cliente

En `/consulta` escribe el **código** de la orden (o de la garantía) y su **clave de 4 dígitos**. Ve:

- El estado, con la línea de tiempo de cambios (sin notas ni nombres del personal).
- Equipo, fechas, presupuesto y total, sin desglose de costos.
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

### Solicitudes de repuestos

```
PENDING ──► APPROVED ──► ORDERED ──► RECEIVED
   │            │           │
   └────────────┴───────────┴──► REJECTED / CANCELLED
```

Al recibir el repuesto, en una sola operación: entra al inventario, se registra como usado en la orden (si sigue en trabajo) al costo real y se guarda como último costo del proveedor. Si la orden se cancela, sus solicitudes sin pedir se cancelan solas.

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

Los `.env` están en `.gitignore` y nunca se suben. Solo se suben los `.env.example`, sin valores.

---

## 14. Respuestas de la API

Los errores llegan como `ProblemDetail` (RFC 9457), con `detail` en español y, en los errores de validación, la lista de campos (`errors`). Nunca incluyen trazas ni SQL.

| Código | Significado | Ejemplo |
|---|---|---|
| 200 / 201 / 204 | Correcto, creado, sin contenido | — |
| 400 | Datos inválidos | Campo vacío, fecha anterior a hoy, `?sort=` con un campo inexistente, caracteres mal codificados |
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

Todo lo demás bajo `/api` exige sesión y su rol. Cualquier ruta fuera de `/api` se rechaza.

---

## 15. Seguridad en resumen

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
| Secretos | Solo variables de entorno; `.env` fuera del repositorio |

Para producción falta: HTTPS en el proxy, `Content-Security-Policy`, `DDL_AUTO=validate` y revisar los riesgos pendientes de [ANALISIS_RIESGOS.md](ANALISIS_RIESGOS.md).

---

## 16. Accesibilidad

Botón fijo de accesibilidad en el login, el portal y el sistema interno, con:

- Tamaño de letra.
- Alto contraste: el estado de la orden lleva además un ícono.
- Modo oscuro.
- Reducir animaciones.
- Resaltar el foco.

Los ajustes se guardan en el navegador. Todo se maneja con teclado, las ventanas devuelven el foco al cerrarse y los mensajes se anuncian a los lectores de pantalla.

---

## 17. Problemas frecuentes

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
| El front no conecta con la API | El backend no corre o el front no está en el puerto 5173 | Arrancar el backend y usar el puerto 5173 |
| El backend no arranca: falta `JWT_SECRET` o `ENCRYPTION_KEY` | Falta el `.env` o tiene llaves cortas | Generar las llaves con `openssl rand -base64 32` |
| No se crea el administrador inicial | Falta `ADMIN_PASSWORD`, ya hay un administrador activo o el correo o el documento ya los usa otra persona | Revisar el log de arranque |

---

## 18. Comandos

| Dónde | Comando | Qué hace |
|---|---|---|
| `fixtrackback` | `./mvnw spring-boot:run` | Arranca el backend |
| `fixtrackback` | `./mvnw clean test` | Compila desde cero y corre las pruebas |
| `fixtrack-front` | `npm run dev` | Servidor de desarrollo en el puerto 5173 |
| `fixtrack-front` | `npm run build` | Compila para producción en `dist/` |
| `fixtrack-front` | `npm run lint` | Revisa el código con oxlint |

> Use `clean test` y no solo `test`: si el editor compiló una clase con errores, Maven puede reutilizarla sin avisar.
