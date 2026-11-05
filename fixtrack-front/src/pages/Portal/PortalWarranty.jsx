import StatusBadge from '../../components/StatusBadge/StatusBadge'
import { formatDateTime, formatDelivery } from '../../utils/format'
import { OUTCOME_LABELS } from '../../utils/labels'
import PortalReceiptButtons from './PortalReceiptButtons'
import PortalTimeline from './PortalTimeline'
import './PortalWarranty.css'

// Garantía anexada a la orden, con su propio estado, fechas, línea de tiempo y comprobantes
export default function PortalWarranty({ warranty, accessKey }) {
  return (
    <article className="portal-warranty" aria-label={`Garantía ${warranty.code}`}>
      <header className="portal-warranty__header">
        <strong>{warranty.code}</strong>
        <StatusBadge status={warranty.status} />
      </header>
      <dl className="portal-warranty__data">
        <div><dt>Ingreso</dt><dd>{formatDateTime(warranty.receivedAt)}</dd></div>
        <div><dt>Entrega estimada</dt><dd>{formatDelivery(warranty.estimatedDeliveryDate, warranty.estimatedDeliveryTime)}</dd></div>
        {warranty.deliveredAt && <div><dt>Entregada</dt><dd>{formatDateTime(warranty.deliveredAt)}</dd></div>}
        {warranty.repairOutcome && <div><dt>Resultado</dt><dd>{OUTCOME_LABELS[warranty.repairOutcome]}</dd></div>}
      </dl>
      <PortalTimeline entries={warranty.timeline} />
      <PortalReceiptButtons code={warranty.code} accessKey={accessKey} delivered={warranty.status === 'DELIVERED'} />
    </article>
  )
}
