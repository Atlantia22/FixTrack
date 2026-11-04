import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router'
import AppLayout from '../components/AppLayout/AppLayout'
import PendingModulePage from '../pages/PendingModule/PendingModulePage'
import { MENU_ITEMS } from './menuConfig'
import ProtectedRoute from './ProtectedRoute'

// Cada pantalla se descarga al entrarle: el login y el portal no cargan el código interno (ni Recharts de Informes)
const CashPage = lazy(() => import('../pages/Cash/CashPage'))
const CatalogsPage = lazy(() => import('../pages/Catalogs/CatalogsPage'))
const CustomersPage = lazy(() => import('../pages/Customers/CustomersPage'))
const DeliveryPage = lazy(() => import('../pages/Delivery/DeliveryPage'))
const HomePage = lazy(() => import('../pages/Home/HomePage'))
const InventoryPage = lazy(() => import('../pages/Inventory/InventoryPage'))
const LoginPage = lazy(() => import('../pages/Login/LoginPage'))
const NotFoundPage = lazy(() => import('../pages/NotFound/NotFoundPage'))
const OrdersPage = lazy(() => import('../pages/Orders/OrdersPage'))
const ChangePasswordPage = lazy(() => import('../pages/Password/ChangePasswordPage'))
const PeoplePage = lazy(() => import('../pages/People/PeoplePage'))
const PartRequestsPage = lazy(() => import('../pages/PartRequests/PartRequestsPage'))
const PortalPage = lazy(() => import('../pages/Portal/PortalPage'))
const ProfilePage = lazy(() => import('../pages/Profile/ProfilePage'))
const SettingsPage = lazy(() => import('../pages/Settings/SettingsPage'))
const SuppliersPage = lazy(() => import('../pages/Suppliers/SuppliersPage'))
const ReportsPage = lazy(() => import('../pages/Reports/ReportsPage'))

// Pantallas ya construidas; los roles de cada ruta salen de menuConfig
const PAGES = {
  '/ordenes': OrdersPage,
  '/salida': DeliveryPage,
  '/clientes': CustomersPage,
  '/perfil': ProfilePage,
  '/personas': PeoplePage,
  '/catalogos': CatalogsPage,
  '/inventario': InventoryPage,
  '/proveedores': SuppliersPage,
  '/solicitudes': PartRequestsPage,
  '/caja': CashPage,
  '/parametros': SettingsPage,
  '/informes': ReportsPage,
}

const MODULE_ROUTES = MENU_ITEMS.filter((item) => item.path !== '/')

export default function AppRoutes() {
  return (
    <Suspense fallback={<p className="page-loading" role="status">Cargando…</p>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/consulta" element={<PortalPage />} />

        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route index element={<HomePage />} />
            <Route path="/perfil/contrasena" element={<ChangePasswordPage />} />
            {MODULE_ROUTES.map(({ path, label, roles }) => {
              // Los módulos sin pantalla muestran la página provisional hasta su sprint
              const Page = PAGES[path]
              return (
                <Route
                  key={path}
                  path={path}
                  element={
                    <ProtectedRoute roles={roles}>
                      {Page ? <Page /> : <PendingModulePage title={label} />}
                    </ProtectedRoute>
                  }
                />
              )
            })}
            {/* Dirección inexistente: con sesión se avisa dentro del layout; sin sesión ProtectedRoute lleva al login */}
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  )
}
