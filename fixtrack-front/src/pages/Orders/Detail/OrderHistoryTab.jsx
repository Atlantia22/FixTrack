import StatusBadge from '../../../components/StatusBadge/StatusBadge'
import { useRemoteData } from '../../../hooks/useRemoteData'
import * as interventionService from '../../../services/interventionService'
import { formatDateTime, formatDelivery } from '../../../utils/format'
import './OrderHistoryTab.css'

// Historial de la orden: cambios de estado y reprogramaciones de la fecha de entrega
export default function OrderHistoryTab({ order }) {
  const history = useRemoteData(() => interventionService.getStatusHistory(order.id), [order.id, order.status])
  const changes = useRemoteData(() => interventionService.getDeliveryChanges(order.id), [order.id, order.estimatedDeliveryDate])

  return (
    <div className="order-history">
      <section aria-labelledby="order-history-status">
        <h3 id="order-history-status" className="order-info__title">Estados</h3>
        {history.loading && !history.data && <p className="text-muted">Cargando…</p>}
        {history.error && <p className="form-error">{history.error}</p>}
        <ol className="order-history__timeline">
          {(history.data ?? []).map((entry) => (
            <li key={entry.id} className="order-history__item">
              <div className="order-history__line">
                {entry.fromStatus ? <StatusBadge status={entry.fromStatus} /> : <span className="text-sm text-muted">Ingreso</span>}
                <span aria-hidden="true">→</span>
                <StatusBadge status={entry.toStatus} />
              </div>
              <p className="order-history__meta">{formatDateTime(entry.changedAt)} · {entry.changedBy.fullName}</p>
              {entry.note && <p className="order-history__note">{entry.note}</p>}
            </li>
          ))}
        </ol>
      </section>

      <section aria-labelledby="order-history-delivery">
        <h3 id="order-history-delivery" className="order-info__title">Reprogramaciones de la entrega</h3>
        {changes.data?.length === 0 && <p className="text-sm text-muted">La fecha de entrega no se ha reprogramado.</p>}
        <ol className="order-history__timeline">
          {(changes.data ?? []).map((change) => (
            <li key={change.id} className="order-history__item">
              <p className="order-history__line">
                <span>{formatDelivery(change.previousDate, change.previousTime)}</span>
                <span aria-hidden="true">→</span>
                <strong>{formatDelivery(change.newDate, change.newTime)}</strong>
              </p>
              <p className="order-history__meta">{formatDateTime(change.changedAt)} · {change.changedBy.fullName}</p>
              <p className="order-history__note">{change.reason}</p>
            </li>
          ))}
        </ol>
      </section>
    </div>
  )
}
