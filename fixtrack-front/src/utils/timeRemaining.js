// Tiempo restante hasta la entrega acordada (fecha + hora en Bogotá); se calcula en el navegador cada minuto, no se guarda
const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR
// Colombia no cambia de horario: la hora de Bogotá siempre es UTC−5
const BOGOTA_OFFSET = '-05:00'
const CLOSED_LABELS = { DELIVERED: 'Entregado', CANCELLED: 'Cancelada', READY: 'Listo para entregar' }

// «2 d 5 h», «5 h 20 min», «35 min»: solo las dos unidades más grandes que importan
export function formatDuration(ms) {
  const totalMinutes = Math.floor(Math.abs(ms) / MINUTE)
  const days = Math.floor(totalMinutes / (24 * 60))
  const hours = Math.floor((totalMinutes % (24 * 60)) / 60)
  const minutes = totalMinutes % 60
  if (days > 0) return hours > 0 ? `${days} d ${hours} h` : `${days} d`
  if (hours > 0) return minutes > 0 ? `${hours} h ${minutes} min` : `${hours} h`
  return minutes > 0 ? `${minutes} min` : 'menos de 1 min'
}

// Momento de entrega como Date; null en las órdenes antiguas que solo tienen fecha
export function deliveryDeadline({ estimatedDeliveryAt, estimatedDeliveryDate, estimatedDeliveryTime }) {
  if (estimatedDeliveryAt) return new Date(estimatedDeliveryAt)
  if (estimatedDeliveryDate && estimatedDeliveryTime) {
    return new Date(`${estimatedDeliveryDate}T${estimatedDeliveryTime.slice(0, 5)}:00${BOGOTA_OFFSET}`)
  }
  return null
}

// La entrega acordada debe quedar después de este momento (puede ser hoy mismo); misma regla del backend
export function isFutureDelivery(date, time, now = Date.now()) {
  const deadline = deliveryDeadline({ estimatedDeliveryDate: date, estimatedDeliveryTime: time })
  return Boolean(deadline) && deadline.getTime() > now
}

function todayInBogota(now) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota' }).format(now)
}

// Misma regla del backend (DeliveryDeadline): atrasada si no está lista, entregada ni cancelada y venció el plazo;
// sin hora, desde el día siguiente a la fecha
export function isOverdue(order, now) {
  if (CLOSED_LABELS[order.status] || !order.estimatedDeliveryDate) return false
  const deadline = deliveryDeadline(order)
  return deadline ? deadline.getTime() <= now : order.estimatedDeliveryDate < todayInBogota(now)
}

// Texto y nivel de urgencia para la columna «Tiempo restante»; el nivel decide el color, el texto siempre se muestra
// level: ok (> 24 h), soon (≤ 24 h), urgent (≤ 2 h), overdue (vencido), closed (lista, entregada, cancelada), none (sin dato)
export function formatTimeRemaining(order, now) {
  if (CLOSED_LABELS[order.status]) return { text: CLOSED_LABELS[order.status], level: 'closed', ms: null }
  if (!order.estimatedDeliveryDate) return { text: 'Sin fecha', level: 'none', ms: null }
  const deadline = deliveryDeadline(order)
  if (!deadline) {
    // Órdenes antiguas: solo fecha, sin cuenta regresiva
    return isOverdue(order, now)
      ? { text: 'Vencido (sin hora)', level: 'overdue', ms: null }
      : { text: 'Sin hora', level: 'none', ms: null }
  }
  const ms = deadline.getTime() - now
  if (ms <= 0) return { text: `Vencido hace ${formatDuration(ms)}`, level: 'overdue', ms }
  const level = ms <= 2 * HOUR ? 'urgent' : ms <= DAY ? 'soon' : 'ok'
  return { text: formatDuration(ms), level, ms }
}
