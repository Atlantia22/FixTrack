import { useId, useState } from 'react'
import { MdTrendingUp } from 'react-icons/md'
import FormField from '../../../components/FormField/FormField'
import { useToast } from '../../../hooks/useToast'
import * as interventionService from '../../../services/interventionService'
import { getErrorMessage } from '../../../utils/errors'
import { formatCurrency } from '../../../utils/format'
import './OrderLaborForm.css'

const MONEY_PATTERN = /^\d{1,10}(\.\d{1,2})?$/

// Mano de obra adicional que define el técnico: el precio de venta de los repuestos ya incluye la instalación,
// así que aquí solo va el trabajo extra; muestra cuánto puede subir sin pasar el presupuesto dado al cliente
export default function OrderLaborForm({ order, partsTotal, onSaved }) {
  const formId = useId()
  const { showError, showSuccess } = useToast()
  const isWarranty = Boolean(order.warrantyOfCode)
  const current = String(Number(order.laborCost ?? 0))
  const [laborCost, setLaborCost] = useState(current)
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  // Mano de obra con la que el total queda justo en el presupuesto; por encima, los daños adicionales piden respuesta del cliente
  const budget = Number(order.estimatedPrice ?? 0)
  const maxWithinBudget = Math.max(budget - Number(partsTotal), 0)
  const typed = MONEY_PATTERN.test(laborCost) ? Number(laborCost) : null
  const room = typed === null ? null : maxWithinBudget - typed

  async function handleSubmit(event) {
    event.preventDefault()
    if (!MONEY_PATTERN.test(laborCost)) {
      setError('Escriba el valor, con máximo 2 decimales.')
      return
    }
    setSaving(true)
    try {
      const updated = await interventionService.setLaborCost(order.id, Number(laborCost))
      showSuccess(`Mano de obra guardada: ${formatCurrency(updated.laborCost)}. Total actual ${formatCurrency(updated.charges.total)}.`)
      onSaved()
    } catch (requestError) {
      showError(getErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  function budgetHint() {
    if (isWarranty) return 'En una garantía la mano de obra es 0.'
    const base = `Los repuestos ya incluyen la instalación en su precio de venta. Presupuesto dado al cliente: ${formatCurrency(budget)}.`
    if (room === null) return base
    if (room > 0) return `${base} Puede subir la mano de obra ${formatCurrency(room)} más sin superar el presupuesto.`
    if (room < 0) return `${base} Con este valor el total supera el presupuesto en ${formatCurrency(-room)}.`
    return `${base} Con este valor el total queda justo en el presupuesto.`
  }

  return (
    <form id={formId} className="order-labor" onSubmit={handleSubmit} noValidate>
      <FormField id={`${formId}-labor`} label="Mano de obra adicional" error={error} hint={budgetHint()}>
        {(props) => <input {...props} className="form-input" inputMode="decimal" disabled={isWarranty} value={laborCost} onChange={(event) => { setLaborCost(event.target.value.replace(/[^\d.]/g, '')); setError(null) }} />}
      </FormField>
      <div className="order-labor__actions">
        {!isWarranty && room !== null && room > 0 && (
          <button type="button" className="btn btn--secondary" onClick={() => setLaborCost(String(maxWithinBudget))}>
            <MdTrendingUp aria-hidden="true" />
            Completar hasta el presupuesto ({formatCurrency(maxWithinBudget)})
          </button>
        )}
        <button type="submit" className="btn btn--primary" disabled={saving || isWarranty || laborCost === current}>
          {saving ? 'Guardando…' : 'Guardar mano de obra'}
        </button>
      </div>
    </form>
  )
}
