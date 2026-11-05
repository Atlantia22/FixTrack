import { useState } from 'react'
import PageHeader from '../../components/PageHeader/PageHeader'
import StatCard from '../../components/StatCard/StatCard'
import { useAuth } from '../../hooks/useAuth'
import { TOPICS, useInterventionsTopic, useRealtime } from '../../hooks/useRealtime'
import { useRemoteData } from '../../hooks/useRemoteData'
import * as dashboardService from '../../services/dashboardService'
import * as personService from '../../services/personService'
import { todayInBogota } from '../../utils/format'
import { ROLES } from '../../utils/labels'
import OrderDetailPanel from '../Orders/Detail/OrderDetailPanel'
import OrdersTable from '../Orders/OrdersTable'
import { HOME_PANELS } from './homePanels'
import StockAlertList from './StockAlertList'
import TechnicianAvailabilityList from './TechnicianAvailabilityList'
import './HomePage.css'

// Una página de órdenes para OrdersTable a partir de una lista sin paginar
function asPage(orders) {
  return orders ? { content: orders, page: 0, totalPages: 1, totalElements: orders.length } : undefined
}

// Panel de Inicio según el rol, con los datos que arma el backend en /api/dashboard
export default function HomePage() {
  const { user } = useAuth()
  const panel = HOME_PANELS[user.role]
  const technicianView = user.role === ROLES.TECHNICIAN
  const [selectedId, setSelectedId] = useState(null)
  const dashboard = useRemoteData(dashboardService.getDashboard, [user.id])
  // Los indicadores se recalculan cuando cambian órdenes, alertas de stock o la disponibilidad de los técnicos
  useRealtime(useInterventionsTopic(), ({ payload }) => {
    if (!technicianView || payload.technicianId === user.id) dashboard.reload({ silent: true })
  })
  useRealtime(TOPICS.STOCK_ALERTS, () => dashboard.reload({ silent: true }))
  useRealtime(TOPICS.PERSONS, ({ payload }) => {
    if (payload.role === ROLES.TECHNICIAN) dashboard.reload({ silent: true })
  }, { enabled: !technicianView })
  // El detalle de la orden necesita la lista de técnicos para reasignar
  const technicians = useRemoteData(
    () => (technicianView ? Promise.resolve({ content: [] }) : personService.searchPeople({ role: ROLES.TECHNICIAN, size: 100 })),
    [technicianView],
  )
  const data = dashboard.data
  const firstName = user.fullName?.split(' ')[0] ?? ''
  const orders = technicianView ? data?.openOrders : data?.recentOrders
  const ordersTitle = technicianView ? 'Mis órdenes abiertas' : 'Órdenes recientes'

  function statValue(key) {
    if (!data) {
      return null
    }
    return key === 'stockAlerts' ? (data.stockAlerts?.length ?? null) : data.counters[key]
  }

  return (
    <div className="page">
      <PageHeader title={`Hola, ${firstName}`} description={panel?.description} />

      {panel && (
        <section className="grid-cards" aria-label="Indicadores">
          {panel.stats.map(({ key, title, icon, tone, to }) => (
            <StatCard
              key={key}
              icon={icon}
              title={title}
              tone={tone}
              value={statValue(key)}
              to={typeof to === 'function' ? to(todayInBogota()) : to}
            />
          ))}
        </section>
      )}

      <section className="page__section">
        <h2 className="page__section-title">{ordersTitle}</h2>
        <OrdersTable
          caption={ordersTitle}
          page={asPage(orders)}
          loading={dashboard.loading}
          error={dashboard.error}
          onRetry={dashboard.reload}
          onSelect={setSelectedId}
          emptyMessage={technicianView ? 'No tiene órdenes abiertas asignadas.' : 'Aún no hay órdenes.'}
        />
      </section>

      {data?.technicians && (
        <section className="page__section" aria-labelledby="home-technicians-title">
          <h2 id="home-technicians-title" className="page__section-title">Técnicos</h2>
          <TechnicianAvailabilityList technicians={data.technicians} loading={dashboard.loading} />
        </section>
      )}

      {data?.stockAlerts && (
        <section className="page__section">
          <h2 className="page__section-title">Repuestos en alerta de stock</h2>
          <StockAlertList parts={data.stockAlerts} loading={dashboard.loading} error={dashboard.error} onRetry={dashboard.reload} />
        </section>
      )}

      {selectedId && (
        <OrderDetailPanel
          key={selectedId}
          orderId={selectedId}
          technicians={technicians.data?.content ?? []}
          onClose={() => setSelectedId(null)}
          onOpenOrder={setSelectedId}
          onChanged={dashboard.reload}
        />
      )}
    </div>
  )
}
