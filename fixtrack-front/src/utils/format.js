const TIME_ZONE = 'America/Bogota'
const EMPTY = '—'

const currencyFormatter = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
})

const dateFormatter = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeZone: 'UTC' })
const longDateFormatter = new Intl.DateTimeFormat('es-CO', { dateStyle: 'long', timeZone: 'UTC' })
const localDateTimeFormatter = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' })
const instantFormatter = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short', timeZone: TIME_ZONE })

export function formatCurrency(value) {
  return value == null ? EMPTY : currencyFormatter.format(Number(value))
}

// Fecha sin hora del backend (2026-10-26); se formatea en UTC para que no se corra un día
export function formatDate(value) {
  return value ? dateFormatter.format(new Date(`${value}T00:00:00Z`)) : EMPTY
}

// Fecha sin hora con el mes en palabras (30 de octubre de 2026), para leerla en voz alta
export function formatDateLong(value) {
  return value ? longDateFormatter.format(new Date(`${value}T00:00:00Z`)) : EMPTY
}

// Fecha y hora del backend: sin zona ya viene en hora de Bogotá; con zona se convierte a Bogotá
export function formatDateTime(value) {
  if (!value) {
    return EMPTY
  }
  const hasZone = /(Z|[+-]\d{2}:\d{2})$/.test(value)
  return hasZone ? instantFormatter.format(new Date(value)) : localDateTimeFormatter.format(new Date(`${value}Z`))
}

// Código visible de la orden: FT-00001, o FT-00001-G1 para las garantías sobre la orden original
export function formatOrderCode({ id, warrantyOfId, warrantyNumber }) {
  const baseId = warrantyOfId ?? id
  const code = `FT-${String(baseId).padStart(5, '0')}`
  return warrantyNumber ? `${code}-G${warrantyNumber}` : code
}

// Celular o correo marcados como «No tiene»
export function displayOrNT(value) {
  return value ? value : 'NT'
}

// Entrega acordada con fecha y hora («5 nov 2026, 3:30 p. m.»); las órdenes antiguas solo tienen fecha
export function formatDelivery(date, time) {
  if (!date) return EMPTY
  return time ? localDateTimeFormatter.format(new Date(`${date}T${time.slice(0, 5)}:00Z`)) : formatDate(date)
}

// Hora actual en Bogotá como HH:mm, para el mínimo del campo de hora cuando la entrega es hoy
export function currentTimeInBogota() {
  return new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: TIME_ZONE }).format(new Date())
}

// Fecha de hoy en Bogotá como YYYY-MM-DD, para el mínimo de los campos de fecha
export function todayInBogota() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: TIME_ZONE }).format(new Date())
}

export function formatNumber(value) {
  return value == null ? EMPTY : new Intl.NumberFormat('es-CO').format(value)
}
