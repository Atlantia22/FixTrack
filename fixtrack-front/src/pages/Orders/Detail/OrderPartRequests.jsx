import { useId } from 'react'
import PartRequestForm from '../../../components/PartRequestForm/PartRequestForm'
import PartRequestStatusTag from '../../../components/PartRequestStatusTag/PartRequestStatusTag'
import { TOPICS, useRealtime } from '../../../hooks/useRealtime'
import { useRemoteData } from '../../../hooks/useRemoteData'
import * as partRequestService from '../../../services/partRequestService'
import { formatDateTime } from '../../../utils/format'
import './OrderPartRequests.css'

// Repuestos que no hay en inventario: el técnico los solicita (del catálogo o descritos) y ve en qué van
export default function OrderPartRequests({ order, canRequest, onRequested }) {
  const formId = useId()
  const requests = useRemoteData(() => partRequestService.listByIntervention(order.id), [order.id])
  useRealtime(TOPICS.PART_REQUESTS, ({ payload }) => {
    if (payload.interventionId === order.id) requests.reload({ silent: true })
  })

  return (
    <section className="order-part-requests" aria-labelledby={`${formId}-title`}>
      <h3 id={`${formId}-title`} className="order-info__title">Solicitudes de repuestos</h3>
      {requests.data?.length === 0 && <p className="text-sm text-muted">La orden no tiene solicitudes.</p>}
      {requests.data?.length > 0 && (
        <ul className="order-part-requests__list">
          {requests.data.map((request) => (
            <li key={request.id} className="order-part-requests__item">
              <span className="order-part-requests__name">
                #{request.id} · {request.description} × {request.quantity}
                <span>{formatDateTime(request.requestedAt)}{request.supplierName ? ` · ${request.supplierName}` : ''}{request.rejectionReason ? ` · ${request.rejectionReason}` : ''}</span>
              </span>
              <PartRequestStatusTag status={request.status} />
            </li>
          ))}
        </ul>
      )}

      {canRequest && (
        <PartRequestForm
          orderId={order.id}
          onRequested={() => {
            requests.reload()
            onRequested()
          }}
        />
      )}
    </section>
  )
}
