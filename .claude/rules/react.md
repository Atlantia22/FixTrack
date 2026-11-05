# Frontend de FixTrack: React con Vite

Buenas prácticas para `fixtrack-front` (React 19 con Vite, Axios, `react-router` y `react-icons` de la familia Material Design, `react-icons/md`). Las reglas de negocio están en `.claude/rules/fixtrack.md` y las del backend en `.claude/rules/springboot.md`.

## Estructura actual

```
FixTrack/
├── .claude/rules/              # fixtrack.md, springboot.md, react.md
├── fixtrackback/               # backend Spring Boot (API en http://localhost:8080/api)
└── fixtrack-front/             # React 19 + Vite 8, oxlint (npm run dev | build | lint)
    ├── .env.example            # VITE_API_URL=http://localhost:8080/api (copiar como .env)
    ├── vite.config.js          # puerto fijo 5173 (strictPort)
    └── src/
        ├── main.jsx            # importa los estilos globales en orden
        ├── App.jsx             # AccessibilityProvider > BrowserRouter > ToastProvider > AuthProvider > AppRoutes
        ├── config/
        │   └── apiConfig.js    # API_CONFIG: cliente Axios, token y cierre de sesión ante 401
        ├── services/           # authService, portalService, personService (personas, especialidades,
        │                       # disponibilidad, búsqueda por documento), catalogService (marcas, modelos, tipos),
        │                       # sparePartService, deviceService (equipos del cliente), interventionService (órdenes,
        │                       # historial, reprogramación, estados, repuestos usados, daños, resultado, entrega,
        │                       # cancelación y comprobantes), paymentService, dashboardService (panel de Inicio)
        ├── context/
        │   ├── session.js      # lectura y escritura de la sesión en sessionStorage
        │   ├── AuthContext.js  # createContext de la sesión
        │   ├── AuthProvider.jsx
        │   ├── ToastContext.js # createContext de los mensajes
        │   ├── ToastProvider.jsx
        │   ├── AccessibilityContext.js # createContext de los ajustes de accesibilidad y FONT_SCALES
        │   └── AccessibilityProvider.jsx # ajustes en localStorage, aplicados como data-* de <html>
        ├── hooks/              # useAuth, useToast, useAccessibility, useDialog (Esc y foco), useGuardedClose (cambios sin guardar),
        │                       # useRemoteData (carga con estado y reload), useDebouncedValue (búsquedas)
        ├── routes/
        │   ├── AppRoutes.jsx
        │   ├── ProtectedRoute.jsx
        │   └── menuConfig.js   # opciones del menú lateral y roles que las ven
        ├── pages/
        │   ├── Login/          # /login
        │   ├── Portal/         # /consulta: PortalOrderResult, PortalStatusHero, PortalTimeline, PortalWarranty, PortalReceiptButtons,
        │   │                   # PortalListenButton («Escuchar» con speechSynthesis)
        │   ├── Home/           # / con GET /api/dashboard: indicadores por rol (homePanels.js), órdenes recientes
        │   │                   # (técnico: sus abiertas), TechnicianAvailabilityList y StockAlertList
        │   ├── Orders/         # /ordenes: OrdersPage (filtros, también desde la URL; técnico = sus órdenes), OrdersTable
        │   │   ├── Intake/     # IntakeModal + CustomerSection, DeviceSection (marca y modelo con SearchSelect), ServiceSection, DepositSection,
        │   │   │               # IntakeResult (código, clave e impresión del comprobante)
        │   │   └── Detail/     # OrderDetailPanel (SidePanel) + OrderInfoTab, OrderRescheduleForm, OrderDiagnosisTab,
        │   │                   # OrderStatusActions, OrderOutcomeForm, OrderFindingsTab + AdditionalFindingForm, OrderPartsTab +
        │   │                   # UsageQuantityModal + OrderLaborForm + OrderPartRequests, OrderFrontDeskActions (reimpresión,
        │   │                   # cancelación), OrderWarrantiesTab + WarrantyModal (solo en la orden original), OrderHistoryTab
        │   ├── Customers/      # /clientes: CustomersPage (búsqueda), CustomerDetailPanel (contacto, equipos y órdenes)
        │   │                   # y CustomerContactModal (celular y correo con PATCH /people/{id}/contact); el admin edita
        │   │                   # todos los datos con PersonFormModal (rol fijo Cliente) y da de baja
        │   ├── Delivery/       # /salida: DeliveryPage (listas con total, abonado y saldo), PaymentModal, DeliveredList,
        │   │                   # UnclaimedList (no reclamados resaltados con su contacto)
        │   ├── Cash/           # /caja: CashTodayCard, CashClosingForm y el historial de cierres
        │   ├── PartRequests/   # /solicitudes: bandeja del coordinador (PartRequestActionModal); técnico = sus solicitudes
        │   ├── Suppliers/      # /proveedores: SuppliersPage y SupplierFormModal
        │   ├── Settings/       # /parametros: SettingCard por parámetro
        │   ├── Reports/        # /informes (carga diferida por Recharts): pestañas según el rol
        │   ├── Profile/        # /perfil: datos propios y disponibilidad del técnico
        │   ├── Password/       # /perfil/contrasena: cambio de la contraseña propia
        │   ├── People/         # /personas «Personal» (admin): PeopleList (solo internos, staff=true) y especialidades
        │   ├── Catalogs/       # /catalogos: marcas, DeviceModelsTab + DeviceModelFormModal, tipos de repuesto
        │   ├── Inventory/      # /inventario: pestañas Repuestos y Movimientos (StockMovementsPanel); InventoryFilters,
        │   │                   # StockAlertsPanel, SparePartFormModal, StockEntryModal, MinStockModal y SparePartLinksModal
        │   │                   # (PartSuppliersEditor y PartCompatibilitiesEditor)
        │   ├── NotFound/       # «Página no encontrada» para direcciones inexistentes (con sesión)
        │   └── PendingModule/  # provisional de los módulos del menú aún no construidos
        ├── components/
        │   ├── AppLayout/      # menú lateral + contenido
        │   ├── AccessibilityPanel/ # botón fijo de accesibilidad y su ventana (AccessibilityDialog)
        │   ├── PublicLayout/   # tarjeta centrada de login y portal
        │   ├── Sidebar/
        │   ├── Logo/
        │   ├── PageHeader/
        │   ├── StatCard/
        │   ├── StatusBadge/
        │   ├── DataTable/
        │   ├── Modal/
        │   ├── SidePanel/
        │   ├── ConfirmDialog/
        │   ├── DiscardPrompt/  # aviso de cambios sin guardar dentro de Modal y SidePanel
        │   ├── PersonFormModal/ # alta y edición de personas: Personal (roles internos) y Clientes (fixedRole)
        │   ├── ContactField/   # celular o correo con su casilla «No tiene» (ingreso, garantía y clientes)
        │   ├── SearchSelect/   # lista desplegable en la que se escribe para filtrar (combobox), para catálogos largos
        │   ├── FormField/      # label + control + ayuda + error, con aria enlazado (render prop)
        │   ├── PasswordInput/  # campo oculto con botón mostrar/ocultar
        │   ├── Tabs/           # pestañas de página
        │   ├── DateRangeFilter/ # rango de fechas con atajos (este mes, mes pasado, últimos 30 días)
        │   ├── ChartCard/      # contenedor de un gráfico de Recharts con su resumen accesible
        │   ├── PartRequestStatusTag/
        │   ├── NameCatalog/    # catálogo de solo nombre: tabla, modal y borrado (marcas, tipos, especialidades)
        │   └── Toast/
        ├── utils/              # format.js (dinero, fechas, hoy en Bogotá, código de orden, NT), labels.js, statusIcons.js, errors.js, files.js (PDF),
        │                       # permissions.js (mismas reglas de rol del backend para mostrar u ocultar acciones)
        └── styles/
            ├── variables.css   # colores, tipografía, espacios, radios, sombras y estados; temas por data-* de <html>
            ├── base.css        # reset y estilos de etiquetas HTML
            ├── layout.css      # contenedores de página y rejillas
            └── utilities.css   # botones, campos de formulario y clases de ayuda
```

- El backend acepta CORS solo desde `http://localhost:5173`; por eso Vite usa ese puerto fijo.
- Los contextos separan el `createContext` (`AuthContext.js`) del componente proveedor (`AuthProvider.jsx`) y el hook (`hooks/useAuth.js`), para que el recargado en caliente de Vite funcione.
- Cada módulo nuevo agrega su servicio en `services/` y su página en el mapa `PAGES` de `AppRoutes.jsx`; los roles de la ruta salen de `menuConfig.js`.
- Todas las páginas se importan con `lazy()` en `AppRoutes.jsx`: el login y el portal no descargan el código interno. El `Suspense` de `AppLayout` mantiene visible el menú lateral mientras carga cada pantalla.
- Sesión: `sessionStorage` guarda `{ authorization, user }`, donde `authorization` es `Bearer <token>` del login y `apiConfig.js` la envía en cada petición. Cuando el token vence, o el usuario se desactiva o cambia de rol, el backend responde 401 y la sesión se cierra sola.
- Al cerrar sesión a propósito (`endedByUser`), `ProtectedRoute` no guarda la página de origen: el siguiente usuario entra al Inicio. Si la sesión vence (`sessionExpired`), el login lo avisa y vuelve a esa página.
- Las tarjetas del Inicio llevan al listado con filtros en la URL (`/ordenes?overdue=true`, `/inventario?alertas=1`). Las páginas toman esos filtros al montarse y en cada navegación (`location.key`), no al cambiar el texto de la URL; así escribir en los filtros nunca se pisa con una navegación en curso.
- Los gráficos usan Recharts con los colores de `variables.css` (`fill="var(--color-primary)"`) y van dentro de `ChartCard`, que agrega un resumen en texto para lectores de pantalla.
- Tiempo real: `config/realtimeClient.js` es la única conexión STOMP (SockJS, reconexión automática y re-suscripción); `AuthProvider` la abre con el token y la cierra al salir. Las vistas usan `useRealtime(TOPICS.X, onEvent, { enabled })` y actualizan solo lo afectado con `mutate` o `reload({ silent: true })` de `useRemoteData`. Solo se suscriben a topics que su rol puede ver: el servidor corta la conexión ante una suscripción prohibida.
- Los PDF se abren con `openPdf` (la pestaña se abre en el mismo clic para que el navegador no la bloquee) o se descargan con `downloadFile`.
- Las acciones de la orden se muestran según `utils/permissions.js` y `nextStatuses` del detalle; el backend valida igual. El asesor ve el total de repuestos sin precios unitarios (sin desglose).
- Los formularios en modal se montan con `key` al abrirse (`{editing && <XFormModal key={...} />}`) para arrancar con su estado inicial, comparan el formulario con ese estado para `isDirty` y borran el error de un campo al editarlo.

## Componentes y nombres

- Solo componentes funcionales con hooks; nada de clases.
- Nombres en inglés: componentes en PascalCase (`OrderDetailPanel`), variables, funciones y hooks en camelCase (`fetchOrders`, `useAuth`). Comentarios en español, según la regla de comentarios de `fixtrack.md`.
- Cada componente vive en su carpeta con su `.jsx` y su `.css` del mismo nombre (`StatCard.jsx` y `StatCard.css`); el `.jsx` importa su propio `.css`.
- Las clases CSS de un componente llevan su nombre como prefijo (`.stat-card`, `.stat-card__value`) para que no choquen con otras.
- Un componente por archivo. Si un componente crece mucho, se divide en componentes más pequeños o se saca su lógica a un hook propio (`useOrders`).
- Los textos que ve el usuario van en español; los valores de los enums se traducen con un mapa (`RECEIVED` → «Recibido»), nunca se muestran crudos.

## Flujo de datos

`módulos de vista (pages y components) → servicios de API (services) → API_CONFIG (config/apiConfig.js) → backend`

- Las vistas nunca importan Axios ni escriben URLs; llaman funciones del servicio de su dominio (`interventionService.getById(id)`).
- Los servicios solo hacen la petición y devuelven `response.data`; no manejan estado de React.
- `apiConfig.js` crea la única instancia de Axios con `baseURL` desde `import.meta.env.VITE_API_URL`, agrega el `Authorization: Bearer <token>` de la sesión y, ante un 401, cierra la sesión y lleva al login.
- Los errores del backend llegan como `ProblemDetail`: se muestra `detail` y, si hay `errors`, cada mensaje junto a su campo del formulario.
- La sesión (token y datos del usuario con su rol) vive en `AuthContext` y se guarda en `sessionStorage`, no en `localStorage`, para que se cierre al cerrar el navegador.
- Mientras carga, se muestra un estado de carga; si la lista está vacía, un mensaje que lo diga; si falla, el error con opción de reintentar.

## Rutas

- Se usa el paquete `react-router` (no `react-router-dom`).
- Las URL van en español (`/consulta`, `/ordenes`, `/inventario`); los nombres de componentes y archivos siguen en inglés.
- `/login` y el portal público del cliente (`/consulta`) son las únicas rutas sin sesión; el login enlaza al portal con «Consulta tu equipo».
- `/` es el panel de Inicio de cada usuario interno según su rol (indicadores y accesos de su trabajo). No hay una página de Inicio vacía ni una página de indicadores aparte.
- `ProtectedRoute` exige sesión y, si la ruta lo pide, uno de los roles permitidos; si no cumple, redirige al Inicio.
- El menú lateral muestra solo las opciones del rol, con los permisos de `fixtrack.md`. Ocultar una opción no reemplaza la validación del backend.

## Diseño

- CSS propio. Sin Bootstrap, Tailwind ni librerías de componentes (MUI, Ant Design, Chakra...).
- Todo color, tipografía, espacio, radio y sombra sale de una variable de `src/styles/variables.css` (`var(--color-primary)`, `var(--space-4)`, `var(--radius-md)`, `var(--shadow-sm)`); no se escriben valores sueltos en los componentes.
- Íconos solo con `react-icons`, de una misma familia en todo el sistema.

### Esquema de pantalla

- Menú lateral oscuro y fijo a la izquierda, con el logo de FixTrack arriba, las opciones según el rol y, abajo, el nombre del usuario y el botón de cerrar sesión. La opción activa se resalta.
- Área de contenido clara, con un encabezado de página (`PageHeader`): título, descripción corta y acción principal (por ejemplo, «Nueva orden»).
- Tarjetas de indicadores (`StatCard`) con ícono, título y número.
- Tablas (`DataTable`) con filtros arriba, paginación abajo y el estado en una etiqueta de color (`StatusBadge`).

### Colores por estado de la orden

Iguales en todo el sistema (tablas, panel de detalle, portal e Inicio), definidos una sola vez en `variables.css` y usados solo a través de `StatusBadge`:

| Estado | Color | Variable |
|---|---|---|
| `RECEIVED` | gris | `--status-received` |
| `IN_DIAGNOSIS` | azul | `--status-in-diagnosis` |
| `WAITING_PARTS` | naranja | `--status-waiting-parts` |
| `IN_REPAIR` | morado | `--status-in-repair` |
| `READY` | verde | `--status-ready` |
| `DELIVERED` | verde oscuro | `--status-delivered` |
| `CANCELLED` | rojo | `--status-cancelled` |

## Ventanas sobre la pantalla

Las acciones se hacen sin salir del listado. Solo hay un `Modal` y un `SidePanel` reutilizables en `src/components`; las demás ventanas se construyen con ellos.

- **Modal centrado** (`Modal`) para crear y editar: personas, especialidades, marcas, modelos, tipos de repuesto, repuestos, proveedores, entradas de mercancía, pagos, daños adicionales y solicitudes de repuestos. Al guardar se cierra y la tabla de fondo se actualiza sin recargar la página.
- **Modal grande por secciones** (`Modal` en tamaño grande) para el ingreso de una orden: cliente, equipo y servicio.
- **Panel lateral** (`SidePanel`) que se desliza desde la derecha con el detalle de la orden, en pestañas: información, diagnóstico, repuestos, pagos e historial. El listado sigue visible detrás.
- **Modal pequeño de confirmación** (`ConfirmDialog`, construido sobre `Modal`) para acciones irreversibles: cancelar una orden, dar de baja, entregar. Dice qué va a pasar y el botón de confirmar nombra la acción («Cancelar orden»).

Comportamiento común:

- Se cierran con Esc, con el botón cerrar o al hacer clic afuera. Si hay cambios sin guardar, primero piden confirmación.
- Al abrir, el foco va al primer campo; mientras están abiertos, el foco no sale de la ventana; al cerrar, vuelve al botón que la abrió.
- Usan `role="dialog"`, `aria-modal="true"` y un título enlazado con `aria-labelledby`.
- El botón de guardar se deshabilita mientras se envía, para no duplicar registros.
- En celular ocupan la pantalla completa.

## Responsive y accesibilidad

- Diseño pensado primero para escritorio, con un único punto de quiebre: `@media (max-width: 768px)` (las media queries no aceptan variables CSS). En celular el menú lateral se vuelve un menú desplegable y las tablas se muestran como tarjetas.
- Cada campo tiene su `<label>` asociado; los errores se muestran junto al campo y se anuncian con `aria-describedby`.
- Foco visible en todos los elementos interactivos (`:focus-visible`); nunca `outline: none` sin reemplazo.
- Contraste mínimo AA en textos y etiquetas de estado. El estado no se comunica solo con color: la etiqueta también lleva el texto.
- Botones que solo tienen ícono llevan `aria-label`.
- Ajustes de accesibilidad (`AccessibilityPanel`, en el login, el portal y el layout interno): tamaño de letra, alto contraste, modo oscuro, reducir animaciones y resaltar el foco. `AccessibilityProvider` los guarda en `localStorage` y los aplica como `data-font-scale`, `data-contrast`, `data-theme`, `data-motion` y `data-focus` en `<html>`; `variables.css` redefine las variables según esos atributos.
- Por eso las medidas van en `rem` (crecen con el tamaño de letra) y los colores solo como variables (cambian con el tema). La única excepción es la media query de 768px.
- En alto contraste, el estado de la orden lleva su ícono (`utils/statusIcons.js`) además del color.
- Los mensajes (`Toast`) se anuncian por una región `aria-live="polite"`; los errores, además, con `role="alert"`.

## Formatos

- Dinero en pesos colombianos con `Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' })`.
- Fechas en `America/Bogota` con `Intl.DateTimeFormat('es-CO', { timeZone: 'America/Bogota' })`.
- El código de la orden (`FT-00001`, `FT-00001-G1`) se arma en un solo utilitario de `src/utils`, igual que en el backend.
- Celular o correo vacíos se muestran como `NT`.
