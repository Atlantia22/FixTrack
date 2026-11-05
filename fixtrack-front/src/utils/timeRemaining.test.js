// Pruebas de formatTimeRemaining con el ejecutor de Node (npm test); la hora base es el 5 de noviembre de 2026, 10:00 en Bogotá
import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { formatTimeRemaining, isOverdue } from './timeRemaining.js'

const NOW = new Date('2026-11-05T10:00:00-05:00').getTime()
const order = (overrides) => ({ status: 'IN_REPAIR', estimatedDeliveryDate: '2026-11-05', estimatedDeliveryTime: '18:00:00', ...overrides })

describe('formatTimeRemaining', () => {
  it('muestra días y horas cuando falta más de un día', () => {
    const result = formatTimeRemaining(order({ estimatedDeliveryDate: '2026-11-07', estimatedDeliveryTime: '15:20:00' }), NOW)
    assert.equal(result.text, '2 d 5 h')
    assert.equal(result.level, 'ok')
  })

  it('muestra horas y minutos cuando falta menos de un día', () => {
    const result = formatTimeRemaining(order({ estimatedDeliveryTime: '15:20:00' }), NOW)
    assert.equal(result.text, '5 h 20 min')
    assert.equal(result.level, 'soon')
  })

  it('muestra solo minutos y urgencia cuando falta menos de una hora', () => {
    const result = formatTimeRemaining(order({ estimatedDeliveryTime: '10:35:00' }), NOW)
    assert.equal(result.text, '35 min')
    assert.equal(result.level, 'urgent')
  })

  it('marca urgente desde las 2 horas', () => {
    assert.equal(formatTimeRemaining(order({ estimatedDeliveryTime: '12:00:00' }), NOW).level, 'urgent')
    assert.equal(formatTimeRemaining(order({ estimatedDeliveryTime: '12:01:00' }), NOW).level, 'soon')
  })

  it('dice hace cuánto venció cuando pasó la hora', () => {
    const result = formatTimeRemaining(order({ estimatedDeliveryTime: '07:00:00' }), NOW)
    assert.equal(result.text, 'Vencido hace 3 h')
    assert.equal(result.level, 'overdue')
  })

  it('usa la fecha y hora con zona del backend cuando viene', () => {
    const result = formatTimeRemaining(order({ estimatedDeliveryAt: '2026-11-05T10:35:00-05:00' }), NOW)
    assert.equal(result.text, '35 min')
  })

  it('dice «Sin fecha» cuando no hay fecha', () => {
    assert.equal(formatTimeRemaining(order({ estimatedDeliveryDate: null, estimatedDeliveryTime: null }), NOW).text, 'Sin fecha')
  })

  it('dice «Sin hora» en las órdenes antiguas que solo tienen fecha', () => {
    const result = formatTimeRemaining(order({ estimatedDeliveryDate: '2026-11-06', estimatedDeliveryTime: null }), NOW)
    assert.equal(result.text, 'Sin hora')
    assert.equal(result.level, 'none')
  })

  it('detiene el contador cuando la orden está entregada', () => {
    const result = formatTimeRemaining(order({ status: 'DELIVERED', estimatedDeliveryTime: '07:00:00' }), NOW)
    assert.equal(result.text, 'Entregado')
    assert.equal(result.level, 'closed')
  })
})

describe('isOverdue', () => {
  it('se atrasa desde el minuto en que vence si no está lista', () => {
    assert.equal(isOverdue(order({ estimatedDeliveryTime: '10:00:00' }), NOW), true)
    assert.equal(isOverdue(order({ estimatedDeliveryTime: '10:01:00' }), NOW), false)
    assert.equal(isOverdue(order({ status: 'READY', estimatedDeliveryTime: '07:00:00' }), NOW), false)
  })

  it('las antiguas sin hora se atrasan desde el día siguiente', () => {
    assert.equal(isOverdue(order({ estimatedDeliveryTime: null }), NOW), false)
    assert.equal(isOverdue(order({ estimatedDeliveryDate: '2026-11-04', estimatedDeliveryTime: null }), NOW), true)
  })
})
