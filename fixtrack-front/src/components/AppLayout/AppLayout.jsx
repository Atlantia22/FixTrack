import { Suspense } from 'react'
import { Outlet } from 'react-router'
import { useStockAlertToasts } from '../../hooks/useStockAlertToasts'
import AccessibilityPanel from '../AccessibilityPanel/AccessibilityPanel'
import Sidebar from '../Sidebar/Sidebar'
import './AppLayout.css'

// Estructura de las pantallas internas: menú lateral fijo, contenido claro y ajustes de accesibilidad
export default function AppLayout() {
  useStockAlertToasts()
  return (
    <div className="app-layout">
      <a className="skip-link" href="#main-content">
        Saltar al contenido
      </a>
      <Sidebar />
      <main id="main-content" className="app-layout__content" tabIndex={-1}>
        {/* Mientras se descarga la pantalla, el menú lateral sigue visible */}
        <Suspense fallback={<p className="page-loading" role="status">Cargando…</p>}>
          <Outlet />
        </Suspense>
      </main>
      <AccessibilityPanel />
    </div>
  )
}
