import { formatCurrency, formatDate, formatDateLong, formatDateTime, formatDelivery } from '../../utils/format'
import { DEVICE_TYPE_LABELS, OUTCOME_LABELS, STATUS_LABELS } from '../../utils/labels'
import { orderPrice } from '../../utils/orderPrice'
import PortalListenButton from './PortalListenButton'
import PortalReceiptButtons from './PortalReceiptButtons'
import PortalStatusHero from './PortalStatusHero'
import PortalTimeline from './PortalTimeline'
import PortalWarranty from './PortalWarranty'
import './PortalOrderResult.css'

function warrantyText(order) {
  if (!order.warrantyUntil) {
    return order.status === 'DELIVERED' ? 'Sin garantía' : 'Empieza al entregar el equipo'
  }
  return `${order.warrantyDays} días, hasta el ${formatDate(order.warrantyUntil)} (${order.warrantyActive ? 'vigente' : 'vencida'})`
}

// Resumen que se lee en voz alta: equipo, estado y fecha estimada de entrega
function spokenSummary(order) {
  const device = [DEVICE_TYPE_LABELS[order.deviceType], order.brandName, order.modelName].filter(Boolean).join(' ')
  return `Equipo: ${device}. Estado: ${STATUS_LABELS[order.status]}. `
    + `Fecha estimada de entrega: ${formatDateLong(order.estimatedDeliveryDate)}`
    + `${order.estimatedDeliveryTime ? `, a las ${order.estimatedDeliveryTime.slice(0, 5)}` : ''}.`
}

// Resultado de la consulta: estado, equipo, fechas, valores (sin desglose), garantía, historial y garantías anexadas
export default function PortalOrderResult({ order, accessKey }) {
  const price = orderPrice({ status: order.status, estimatedPrice: order.estimatedPrice, total: order.total })
  return (
    <div className="portal-order">
      <PortalStatusHero status={order.status} />
      <PortalListenButton text={spokenSummary(order)} />
      <dl className="portal-result">
        <div className="portal-result__row">
          <dt>Equipo</dt>
          <dd>{DEVICE_TYPE_LABELS[order.deviceType]} {order.brandName} {order.modelName}</dd>
        </div>
        {order.repairOutcome && (
          <div className="portal-result__row"><dt>Resultado</dt><dd>{OUTCOME_LABELS[order.repairOutcome]}</dd></div>
        )}
        <div className="portal-result__row"><dt>Ingreso</dt><dd>{formatDateTime(order.receivedAt)}</dd></div>
        <div className="portal-result__row"><dt>Entrega estimada</dt><dd>{formatDelivery(order.estimatedDeliveryDate, order.estimatedDeliveryTime)}</dd></div>
        {order.deliveredAt && (
          <div className="portal-result__row"><dt>Entregado</dt><dd>{formatDateTime(order.deliveredAt)}</dd></div>
        )}
        <div className="portal-result__row">
          <dt>{price.priced ? 'Valor final' : 'Valor (presupuesto)'}</dt>
          <dd>{formatCurrency(price.value)}{!price.priced && <span className="portal-result__note"> · puede cambiar cuando el técnico revise el equipo</span>}</dd>
        </div>
        <div className="portal-result__row"><dt>Garantía</dt><dd>{warrantyText(order)}</dd></div>
      </dl>

      <section className="portal-order__section" aria-labelledby="portal-timeline-title">
        <h2 id="portal-timeline-title" className="portal-order__title">Historial</h2>
        <PortalTimeline entries={order.timeline} />
      </section>

      <section className="portal-order__section" aria-labelledby="portal-receipts-title">
        <h2 id="portal-receipts-title" className="portal-order__title">Comprobantes</h2>
        <PortalReceiptButtons code={order.code} accessKey={accessKey} delivered={order.status === 'DELIVERED'} />
      </section>

      {order.warranties.length > 0 && (
        <section className="portal-order__section" aria-labelledby="portal-warranties-title">
          <h2 id="portal-warranties-title" className="portal-order__title">Garantías</h2>
          {order.warranties.map((warranty) => (
            <PortalWarranty key={warranty.code} warranty={warranty} accessKey={accessKey} />
          ))}
        </section>
      )}
    </div>
  )
}
