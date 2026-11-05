# FixTrack: contexto del proyecto

## Qué es

Sistema web de gestión de reparaciones e inventario para una tienda de reparación de dispositivos móviles (teléfonos, tablets, relojes inteligentes, parlantes y otros).

## Roles

Los roles internos inician sesión: `ADMIN`, `COORDINATOR` (coordinador de inventario), `ADVISOR` (asesor) y `TECHNICIAN` (técnico).

Los clientes (`CUSTOMER`) se registran como personas pero no inician sesión. Consultan su orden en un portal público, enlazado desde la pantalla de inicio de sesión, con el código de la orden (consecutivo, por ejemplo `FT-00001`) y su clave de consulta de 4 dígitos.

### Permisos por rol

- **Asesor**: registrar clientes y equipos, recibir equipos con su estado físico, dar el presupuesto inicial, abrir órdenes y asignar técnico, registrar la respuesta del cliente sobre los daños adicionales, registrar abonos y el cobro final, entregar equipos, reprogramar la fecha estimada de entrega, abrir garantías y hacer su cierre de caja.
- **Técnico**: ver sus órdenes asignadas, actualizar su disponibilidad, reprogramar la fecha estimada de entrega de sus órdenes, registrar el diagnóstico y la solución, agregar daños adicionales, definir la mano de obra, cambiar el estado, registrar y corregir los repuestos usados, y solicitar repuestos que no hay en inventario.
- **Coordinador**: todo lo del asesor, más gestionar el inventario, los catálogos, los proveedores, las entradas y las solicitudes de repuestos.
- **Administrador**: todo lo anterior, más gestionar personas, roles y especialidades, informes y parámetros.

## Estados de la orden

`RECEIVED`, `IN_DIAGNOSIS`, `WAITING_PARTS`, `IN_REPAIR`, `READY`, `DELIVERED` y `CANCELLED`.

«No reparable» y «rechazado por el cliente» son resultados de la orden, no estados.

## Equipos

- El IMEI es obligatorio solo en teléfonos; los demás se identifican por número de serie cuando lo tienen.
- La contraseña o patrón de desbloqueo se guarda cifrado en la orden, solo lo ven el asesor y el técnico asignado, y se borra al entregar o cancelar.

## Código de la orden

- No se guarda como columna. Se genera a partir del id de la base de datos como `FT-` + id con 5 dígitos (id 1 → `FT-00001`; desde el id 100000 crece sin recortarse). Así la única llave única es el id y nunca hay duplicados.
- Las garantías usan el código de la orden original + `-G` + su número de garantía.
- Para buscar por código, se convierte de vuelta a id.

## Zona horaria

`America/Bogota` en todo el sistema (backend, base de datos y front). «Hoy», las órdenes atrasadas, las no reclamadas, los informes por fecha y el cierre de caja se calculan con esa hora.

## Clientes

- El tipo y número de documento son obligatorios para guardarlos y son la forma de identificarlos cuando regresan.
- El celular y el correo son opcionales, pero cada uno se llena o se marca explícitamente «No tiene»: se guarda vacío y se muestra como `NT`.
- **Celular**: solo números, de 7 a 10 dígitos, no único (lo pueden compartir familiares).
- **Correo**: formato válido y único cuando existe; obligatorio para los usuarios internos, porque con él inician sesión.

## Clave de consulta

- Se asigna al abrir la orden y no cambia aunque el cliente cambie sus datos.
- Son los últimos 4 dígitos del celular o, si no tiene celular, 4 dígitos al azar impresos en el comprobante de ingreso.
- Se guarda con hash; al reimprimir el comprobante se genera una nueva y la anterior deja de servir.
- Si el cliente no tiene celular ni correo, el comprobante advierte «Conserve este comprobante, no podremos contactarlo».

## Fecha estimada de entrega

- Fecha y hora obligatorias al abrir la orden (puede ser el mismo día), posteriores al momento del ingreso y visibles en los comprobantes y el portal. Las órdenes anteriores a la hora solo tienen fecha (hora en `NULL`).
- Se puede reprogramar con un motivo; queda registro de quién, cuándo, la fecha y hora anterior y la nueva.
- Una orden está atrasada desde el minuto en que se cumple su fecha y hora sin estar en `READY`, `DELIVERED` ni `CANCELLED`; las que no tienen hora, desde el día siguiente a su fecha.
- El tiempo restante no se guarda: se calcula en America/Bogota (`DeliveryDeadline` en el backend, `utils/timeRemaining.js` en el front).

## Garantías

- Se anexan a la orden original con su código más un sufijo (`FT-00001-G1`, `FT-00001-G2`) y se consultan en el portal con el código y la clave de la original.
- Solo se abren mientras la garantía de la original esté vigente, no se abren sobre otra garantía y no la extienden.
- Siguen los mismos estados de una orden, con mano de obra y repuestos a precio 0.
- Al abrirla se pueden actualizar los datos de contacto del cliente.

## Repuestos

La calidad (`ORIGINAL`, `OEM`, `GENERIC`) es opcional; cada calidad es un repuesto distinto con su propio SKU, stock y precio.

## Dinero

- **Total de la orden** = mano de obra + suma de cantidad × precio de venta de cada repuesto usado, con el precio guardado al momento del uso.
- **Costo de repuestos** = suma de cantidad × costo guardado al momento del uso.
- **Ganancia** = total − costo de repuestos. Se calcula, no se guarda.
  - Cuentan las órdenes entregadas; las canceladas no.
  - Las no reparables cuentan con el valor del diagnóstico.
  - Las garantías van a precio 0 y su costo lo asume el almacén.
- El cliente y el asesor solo ven el presupuesto y el total, sin desglose. El técnico ve la ganancia de sus órdenes; el coordinador y el administrador, la de todas.

## Fuera del alcance

Correo al cliente, procedimiento legal de abandono, facturación electrónica y comisiones de los técnicos.

## Comentarios en el código

- En español y de una línea, diciendo qué hace cada área importante del código.
- Van en la responsabilidad de las clases y componentes principales, en los métodos con lógica de negocio y en los pasos principales de los procesos largos (recibir una orden, registrar un repuesto usado, cerrar la caja).
- No se comenta cada método ni cada línea, ni se explica cómo funciona por dentro.
- Los CRUD simples, getters, DTOs y repositorios sin consultas especiales no llevan comentarios.
