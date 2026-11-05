import { useId, useState } from 'react'
import FormField from '../../../components/FormField/FormField'
import { useToast } from '../../../hooks/useToast'
import * as interventionService from '../../../services/interventionService'
import { getErrorMessage, getFieldErrors } from '../../../utils/errors'
import { currentTimeInBogota, formatDelivery, todayInBogota } from '../../../utils/format'
import { isFutureDelivery } from '../../../utils/timeRemaining'
import './OrderRescheduleForm.css'

// Reprogramación de la fecha y hora estimada de entrega con motivo obligatorio; queda en el historial
export default function OrderRescheduleForm({ order, onDone, onCancel }) {
  const formId = useId()
  const { showError, showSuccess } = useToast()
  const today = todayInBogota()
  const [newDate, setNewDate] = useState(order.estimatedDeliveryDate < today ? today : order.estimatedDeliveryDate)
  const [newTime, setNewTime] = useState(order.estimatedDeliveryTime?.slice(0, 5) ?? '')
  const [reason, setReason] = useState('')
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    const validation = {}
    if (!newDate || newDate < today) validation.newDate = 'Elija una fecha desde hoy.'
    if (!newTime) validation.newTime = 'Elija la hora acordada.'
    else if (!validation.newDate && !isFutureDelivery(newDate, newTime)) validation.newTime = 'Debe ser posterior a este momento.'
    else if (newDate === order.estimatedDeliveryDate && newTime === order.estimatedDeliveryTime?.slice(0, 5)) {
      validation.newTime = 'Es la misma fecha y hora estimada actual.'
    }
    if (!reason.trim()) validation.reason = 'Escriba el motivo de la reprogramación.'
    setErrors(validation)
    if (Object.keys(validation).length > 0) {
      return
    }
    setSaving(true)
    try {
      await interventionService.rescheduleDelivery(order.id, { newDate, newTime, reason: reason.trim() })
      showSuccess(`Entrega reprogramada para el ${formatDelivery(newDate, newTime)}.`)
      onDone()
    } catch (requestError) {
      setErrors(getFieldErrors(requestError))
      showError(getErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form id={formId} className="order-reschedule" onSubmit={handleSubmit} noValidate>
      <FormField id={`${formId}-date`} label="Nueva fecha de entrega" error={errors.newDate} required>
        {(props) => <input {...props} type="date" className="form-input" min={today} value={newDate} onChange={(event) => setNewDate(event.target.value)} />}
      </FormField>
      <FormField id={`${formId}-time`} label="Nueva hora de entrega" error={errors.newTime} required>
        {(props) => (
          <input
            {...props}
            type="time"
            className="form-input"
            min={newDate === today ? currentTimeInBogota() : undefined}
            value={newTime}
            onChange={(event) => setNewTime(event.target.value)}
          />
        )}
      </FormField>
      <FormField id={`${formId}-reason`} label="Motivo" error={errors.reason} required>
        {(props) => <textarea {...props} className="form-input" maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} />}
      </FormField>
      <div className="order-reschedule__actions">
        <button type="button" className="btn btn--secondary" onClick={onCancel} disabled={saving}>Cancelar</button>
        <button type="submit" className="btn btn--primary" disabled={saving}>{saving ? 'Guardando…' : 'Reprogramar'}</button>
      </div>
    </form>
  )
}
