import { useState } from 'react'
import { MdAdd } from 'react-icons/md'
import DataTable from '../../components/DataTable/DataTable'
import FormField from '../../components/FormField/FormField'
import PageHeader from '../../components/PageHeader/PageHeader'
import PartRequestStatusTag from '../../components/PartRequestStatusTag/PartRequestStatusTag'
import { useAuth } from '../../hooks/useAuth'
import { TOPICS, useRealtime } from '../../hooks/useRealtime'
import { useRemoteData } from '../../hooks/useRemoteData'
import * as partRequestService from '../../services/partRequestService'
import * as sparePartService from '../../services/sparePartService'
import * as supplierService from '../../services/supplierService'
import { formatCurrency, formatDateTime } from '../../utils/format'
import { PART_REQUEST_STATUS_LABELS, ROLES, toOptions } from '../../utils/labels'
import NewPartRequestModal from './NewPartRequestModal'
import PartRequestActionModal from './PartRequestActionModal'
import './PartRequestsPage.css'

const ACTIONS = {
  PENDING: [{ action: 'approve', label: 'Aprobar' }, { action: 'reject', label: 'Rechazar' }],
  APPROVED: [{ action: 'order', label: 'Pedir' }, { action: 'reject', label: 'Rechazar' }],
  ORDERED: [{ action: 'receive', label: 'Recibir' }],
}

// Bandeja del coordinador (aprobar, rechazar, pedir y recibir) y, para el técnico, el estado de sus solicitudes
export default function PartRequestsPage() {
  const { user } = useAuth()
  const technicianView = user.role === ROLES.TECHNICIAN
  const [status, setStatus] = useState(technicianView ? '' : 'PENDING')
  const [page, setPage] = useState(0)
  const [active, setActive] = useState(null)
  const [creating, setCreating] = useState(false)
  const requests = useRemoteData(
    () => (technicianView ? partRequestService.listMine({ status, page }) : partRequestService.searchPartRequests({ status, page })),
    [technicianView, status, page],
  )
  // El técnico ve el avance de sus solicitudes y el coordinador las nuevas, sin recargar la página
  useRealtime(TOPICS.PART_REQUESTS, ({ payload }) => {
    if (!technicianView || payload.requestedById === user.id) requests.reload({ silent: true })
  })
  const parts = useRemoteData(() => (technicianView ? Promise.resolve({ content: [] }) : sparePartService.searchSpareParts({ size: 500 })), [technicianView])
  const suppliers = useRemoteData(() => (technicianView ? Promise.resolve({ content: [] }) : supplierService.searchSuppliers({ size: 200 })), [technicianView])

  const columns = [
    { key: 'id', header: '#', render: (request) => `#${request.id}` },
    { key: 'interventionCode', header: 'Orden' },
    {
      key: 'description',
      header: 'Repuesto',
      render: (request) => (
        <span className="part-requests__part">
          {request.description} × {request.quantity}
          <span>{request.sku ?? 'Descrito a mano'}</span>
        </span>
      ),
    },
    { key: 'status', header: 'Estado', render: (request) => <PartRequestStatusTag status={request.status} /> },
    {
      key: 'supplier',
      header: 'Proveedor',
      render: (request) => request.supplierName ?? (request.suggestedSupplier ? `Sugerido: ${request.suggestedSupplier.name}` : '—'),
    },
    { key: 'cost', header: 'Costo', align: 'right', render: (request) => formatCurrency(request.actualCost ?? request.estimatedCost) },
    ...(technicianView ? [] : [{ key: 'requestedByName', header: 'Técnico' }]),
    { key: 'requestedAt', header: 'Solicitada', render: (request) => formatDateTime(request.requestedAt) },
    ...(technicianView
      ? [{ key: 'notes', header: 'Detalle', render: (request) => request.rejectionReason ?? (request.receivedAt ? `Recibida ${formatDateTime(request.receivedAt)}` : '—') }]
      : [{
        key: 'actions',
        header: 'Acciones',
        align: 'right',
        render: (request) => (
          <span className="row-actions">
            {(ACTIONS[request.status] ?? []).map(({ action, label }) => (
              <button key={action} type="button" className={`btn ${action === 'reject' ? 'btn--ghost' : 'btn--secondary'}`} onClick={() => setActive({ action, request })}>
                {label}
              </button>
            ))}
          </span>
        ),
      }]),
  ]

  return (
    <div className="page">
      <PageHeader
        title={technicianView ? 'Mis solicitudes' : 'Solicitudes de repuestos'}
        description={technicianView
          ? 'Repuestos que pidió para sus órdenes y en qué van. También puede pedirlos desde la pestaña Repuestos de la orden.'
          : 'Apruebe, pida al proveedor y reciba los repuestos que piden los técnicos desde sus órdenes.'}
        actions={technicianView && (
          <button type="button" className="btn btn--primary" onClick={() => setCreating(true)}>
            <MdAdd aria-hidden="true" />
            Nueva solicitud
          </button>
        )}
      />
      <div className="filters">
        <FormField id="part-requests-status" label="Estado">
          {(props) => (
            <select {...props} className="form-input" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}>
              <option value="">Todas</option>
              {toOptions(PART_REQUEST_STATUS_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
            </select>
          )}
        </FormField>
      </div>
      <DataTable
        caption={technicianView ? 'Mis solicitudes' : 'Solicitudes de repuestos'}
        columns={columns}
        rows={requests.data?.content ?? []}
        loading={requests.loading}
        error={requests.error}
        onRetry={requests.reload}
        emptyMessage="No hay solicitudes con ese estado."
        page={requests.data?.page ?? 0}
        totalPages={requests.data?.totalPages ?? 1}
        totalElements={requests.data?.totalElements}
        onPageChange={setPage}
      />
      {creating && (
        <NewPartRequestModal
          onClose={() => setCreating(false)}
          onRequested={() => {
            setCreating(false)
            requests.reload()
          }}
        />
      )}
      {active && (
        <PartRequestActionModal
          key={`${active.action}-${active.request.id}`}
          action={active.action}
          request={active.request}
          parts={parts.data?.content ?? []}
          suppliers={suppliers.data?.content ?? []}
          onClose={() => setActive(null)}
          onDone={() => {
            setActive(null)
            requests.reload()
          }}
        />
      )}
    </div>
  )
}
