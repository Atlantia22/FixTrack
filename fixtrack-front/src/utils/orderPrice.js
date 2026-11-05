// Valor único de la orden: el presupuesto hasta que el técnico dé el precio (mano de obra o repuestos) y luego el precio final.
// Lista, entregada o cancelada ya tiene su valor definitivo aunque sea 0 (por ejemplo, una garantía)
const FINAL_STATUSES = ['READY', 'DELIVERED', 'CANCELLED']

export function orderPrice({ status, estimatedPrice, total }) {
  const priced = Number(total) > 0 || FINAL_STATUSES.includes(status)
  return priced
    ? { value: Number(total), priced: true, label: 'Precio final' }
    : { value: Number(estimatedPrice ?? 0), priced: false, label: 'Presupuesto' }
}
