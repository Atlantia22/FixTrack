import { useState } from 'react'
import { MdAdd } from 'react-icons/md'
import { useLocation, useSearchParams } from 'react-router'
import FormField from '../../components/FormField/FormField'
import PageHeader from '../../components/PageHeader/PageHeader'
import { useAuth } from '../../hooks/useAuth'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { TOPICS, useRealtime } from '../../hooks/useRealtime'
import { useRemoteData } from '../../hooks/useRemoteData'
import * as catalogService from '../../services/catalogService'
import * as interventionService from '../../services/interventionService'
import * as personService from '../../services/personService'
import { ROLES, STATUS_LABELS, toOptions } from '../../utils/labels'
import { isFrontDesk } from '../../utils/permissions'
import OrderDetailPanel from './Detail/OrderDetailPanel'
import IntakeModal from './Intake/IntakeModal'
import OrdersTable from './OrdersTable'
import './OrdersPage.css'

const EMPTY_FILTERS = {
  code: '',
  documentNumber: '',
  status: '',
  technicianId: '',
  receivedFrom: '',
  receivedTo: '',
  overdue: false,
  inProgress: false,
  openOnly: false,
  unclaimed: false,
}
const FLAGS = ['overdue', 'inProgress', 'openOnly', 'unclaimed']

// Las tarjetas del Inicio llegan con filtros en la URL (?status=READY, ?overdue=true…)
function filtersFromParams(params) {
  const filters = { ...EMPTY_FILTERS }
  for (const key of Object.keys(EMPTY_FILTERS)) {
    if (params.has(key)) {
      filters[key] = FLAGS.includes(key) ? params.get(key) === 'true' : params.get(key)
    }
  }
  return filters
}

// Más urgentes primero: la entrega más próxima (o la más vencida) arriba; se ordena en el servidor porque el listado es paginado
const URGENT_SORT = ['estimatedDeliveryDate,asc', 'estimatedDeliveryTime,asc', 'id,asc']

// Órdenes: el técnico ve las suyas abiertas; los demás consultan todas con filtros combinados y reciben equipos
export default function OrdersPage() {
  const { user } = useAuth()
  const technicianView = user.role === ROLES.TECHNICIAN
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const [filters, setFilters] = useState(() => filtersFromParams(searchParams))
  const [appliedKey, setAppliedKey] = useState(location.key)
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState(null)
  const [intakeOpen, setIntakeOpen] = useState(false)
  const debouncedCode = useDebouncedValue(filters.code)
  const debouncedDocument = useDebouncedValue(filters.documentNumber)
  const query = { ...filters, code: debouncedCode, documentNumber: debouncedDocument }

  // Cada navegación a la página (tarjeta del Inicio, menú) aplica los filtros de su URL; escribir no navega
  if (location.key !== appliedKey) {
    setAppliedKey(location.key)
    setFilters(filtersFromParams(searchParams))
    setPage(0)
  }

  const [urgentFirst, setUrgentFirst] = useState(false)
  const sort = urgentFirst ? URGENT_SORT : undefined
  const orders = useRemoteData(
    () => (technicianView
      ? interventionService.listMyInterventions({ status: filters.status, overdue: filters.overdue, inProgress: filters.inProgress, sort, page })
      // Por urgencia solo tienen sentido las abiertas: las entregadas tienen fechas viejas y quedarían arriba
      : interventionService.searchInterventions({ ...query, openOnly: query.openOnly || urgentFirst, sort, page })),
    [technicianView, page, urgentFirst, ...Object.values(query)],
  )
  // Tiempo real: la fila de la orden se actualiza en sitio con los datos del evento (estado, técnico y entrega);
  // solo se recarga si la orden puede entrar, salir o cambiar de lugar en el listado
  useRealtime(TOPICS.INTERVENTIONS, ({ type, id, payload }) => {
    const inList = orders.data?.content?.some((order) => order.id === id)
    if (inList) {
      orders.mutate((current) => ({
        ...current,
        content: current.content.map((order) => (order.id === id
          ? {
            ...order,
            status: payload.status,
            technicianName: payload.technicianName,
            estimatedDeliveryDate: payload.estimatedDeliveryDate,
            estimatedDeliveryTime: payload.estimatedDeliveryTime,
            estimatedDeliveryAt: payload.estimatedDeliveryAt,
          }
          : order)),
      }))
      const filtered = filters.status || filters.overdue || filters.inProgress || filters.technicianId
      const reordered = urgentFirst && type === 'RESCHEDULED'
      if ((type === 'STATUS_CHANGED' && filtered) || reordered) orders.reload({ silent: true })
      return
    }
    const mine = !technicianView || payload.technicianId === user.id
    if (mine) orders.reload({ silent: true })
  })

  const technicians = useRemoteData(() => personService.searchPeople({ role: ROLES.TECHNICIAN, size: 100 }), [])
  const brands = useRemoteData(catalogService.listBrands, [])
  const models = useRemoteData(() => catalogService.listDeviceModels(), [])
  const technicianList = technicians.data?.content ?? []
  const intakeReady = Boolean(brands.data && models.data && technicians.data)

  function changeFilter(field, value) {
    setFilters((current) => ({ ...current, [field]: value }))
    setPage(0)
  }

  const hasFilters = JSON.stringify(filters) !== JSON.stringify(EMPTY_FILTERS)

  function clearFilters() {
    setFilters(EMPTY_FILTERS)
    setPage(0)
  }

  return (
    <div className="page">
      <PageHeader
        title={technicianView ? 'Mis órdenes' : 'Órdenes'}
        description={technicianView ? 'Sus órdenes abiertas, de la más antigua a la más reciente.' : 'Consulta de órdenes de reparación y garantía.'}
        actions={isFrontDesk(user) && (
          <button type="button" className="btn btn--primary" disabled={!intakeReady} onClick={() => setIntakeOpen(true)}>
            <MdAdd aria-hidden="true" />
            Nueva orden
          </button>
        )}
      />

      <div className="filters orders-page__filters">
        {!technicianView && (
          <>
            <FormField id="orders-code" label="Código">
              {(props) => <input {...props} type="search" className="form-input" placeholder="FT-00001" value={filters.code} onChange={(event) => changeFilter('code', event.target.value.toUpperCase())} />}
            </FormField>
            <FormField id="orders-document" label="Documento del cliente">
              {(props) => <input {...props} type="search" className="form-input" value={filters.documentNumber} onChange={(event) => changeFilter('documentNumber', event.target.value)} />}
            </FormField>
          </>
        )}
        <FormField id="orders-sort" label="Ordenar por">
          {(props) => (
            <select {...props} className="form-input" value={urgentFirst ? 'urgent' : 'recent'} onChange={(event) => { setUrgentFirst(event.target.value === 'urgent'); setPage(0) }}>
              <option value="recent">{technicianView ? 'Ingreso más antiguo' : 'Ingreso más reciente'}</option>
              <option value="urgent">Tiempo restante: más urgentes primero</option>
            </select>
          )}
        </FormField>
        <FormField id="orders-status" label="Estado">
          {(props) => (
            <select {...props} className="form-input" value={filters.status} onChange={(event) => changeFilter('status', event.target.value)}>
              <option value="">{technicianView ? 'Abiertas' : 'Todos'}</option>
              {toOptions(STATUS_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
            </select>
          )}
        </FormField>
        {!technicianView && (
          <>
            <FormField id="orders-technician" label="Técnico">
              {(props) => (
                <select {...props} className="form-input" value={filters.technicianId} onChange={(event) => changeFilter('technicianId', event.target.value)}>
                  <option value="">Todos</option>
                  {technicianList.map((technician) => <option key={technician.id} value={technician.id}>{technician.fullName}</option>)}
                </select>
              )}
            </FormField>
            <FormField id="orders-from" label="Recibidas desde">
              {(props) => <input {...props} type="date" className="form-input" value={filters.receivedFrom} onChange={(event) => changeFilter('receivedFrom', event.target.value)} />}
            </FormField>
            <FormField id="orders-to" label="Hasta">
              {(props) => <input {...props} type="date" className="form-input" min={filters.receivedFrom || undefined} value={filters.receivedTo} onChange={(event) => changeFilter('receivedTo', event.target.value)} />}
            </FormField>
            <label className="orders-page__overdue">
              <input type="checkbox" checked={filters.openOnly} onChange={(event) => changeFilter('openOnly', event.target.checked)} />
              Sin entregar
            </label>
            <label className="orders-page__overdue" title="Listas para entregar desde hace más de los días configurados en Parámetros">
              <input type="checkbox" checked={filters.unclaimed} onChange={(event) => changeFilter('unclaimed', event.target.checked)} />
              Listas sin reclamar
            </label>
          </>
        )}
        <label className="orders-page__overdue">
          <input type="checkbox" checked={filters.overdue} onChange={(event) => changeFilter('overdue', event.target.checked)} />
          Solo atrasadas
        </label>
        <label className="orders-page__overdue">
          <input type="checkbox" checked={filters.inProgress} onChange={(event) => changeFilter('inProgress', event.target.checked)} />
          En proceso
        </label>
        {hasFilters && (
          <button type="button" className="btn btn--ghost" onClick={clearFilters}>
            Limpiar filtros
          </button>
        )}
      </div>

      <OrdersTable
        page={orders.data}
        loading={orders.loading}
        error={orders.error}
        onRetry={orders.reload}
        onPageChange={setPage}
        onSelect={setSelectedId}
        emptyMessage={technicianView ? 'No tiene órdenes abiertas asignadas.' : 'No hay órdenes con esos filtros.'}
      />

      {intakeOpen && (
        <IntakeModal
          brands={brands.data}
          models={models.data}
          technicians={technicianList}
          onClose={() => setIntakeOpen(false)}
          onCreated={orders.reload}
          onOpenOrder={(id) => {
            setIntakeOpen(false)
            setSelectedId(id)
          }}
        />
      )}

      {selectedId && (
        <OrderDetailPanel
          key={selectedId}
          orderId={selectedId}
          technicians={technicianList}
          onClose={() => setSelectedId(null)}
          onOpenOrder={setSelectedId}
          onChanged={orders.reload}
        />
      )}
    </div>
  )
}
