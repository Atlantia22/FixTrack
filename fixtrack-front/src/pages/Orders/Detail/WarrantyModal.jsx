import { useId, useState } from 'react'
import FormField from '../../../components/FormField/FormField'
import Modal from '../../../components/Modal/Modal'
import UnlockCodeField from '../../../components/UnlockCodeField/UnlockCodeField'
import { useToast } from '../../../hooks/useToast'
import * as interventionService from '../../../services/interventionService'
import { getErrorMessage, getFieldErrors } from '../../../utils/errors'
import { currentTimeInBogota, formatDate, todayInBogota } from '../../../utils/format'
import { isFutureDelivery } from '../../../utils/timeRemaining'
import { AVAILABILITY_LABELS, SERVICE_TYPE_LABELS, toOptions } from '../../../utils/labels'
import ContactField from '../../../components/ContactField/ContactField'
import './WarrantyModal.css'

const PHONE_PATTERN = /^\d{7,10}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function initialForm(order) {
  return {
    serviceType: '',
    problemDescription: '',
    physicalCondition: '',
    unlockCode: '',
    estimatedDeliveryDate: todayInBogota(),
    estimatedDeliveryTime: '',
    technicianId: order.technician ? String(order.technician.id) : '',
    phone: order.customer.phone ?? '',
    noPhone: !order.customer.phone,
    email: order.customer.email ?? '',
    noEmail: !order.customer.email,
  }
}

function validate(form, today) {
  const errors = {}
  if (!form.serviceType) errors.serviceType = 'Elija el servicio.'
  if (!form.problemDescription.trim()) errors.problemDescription = 'Describa la falla que reporta el cliente.'
  if (!form.physicalCondition.trim()) errors.physicalCondition = 'Describa el estado físico.'
  if (!form.estimatedDeliveryDate || form.estimatedDeliveryDate < today) errors.estimatedDeliveryDate = 'Desde hoy en adelante.'
  if (!form.estimatedDeliveryTime) errors.estimatedDeliveryTime = 'Elija la hora acordada.'
  else if (!errors.estimatedDeliveryDate && !isFutureDelivery(form.estimatedDeliveryDate, form.estimatedDeliveryTime)) {
    errors.estimatedDeliveryTime = 'Debe ser posterior a este momento.'
  }
  if (!form.noPhone && !PHONE_PATTERN.test(form.phone)) errors.phone = 'Entre 7 y 10 dígitos, o marque «No tiene».'
  if (!form.noEmail && !EMAIL_PATTERN.test(form.email.trim())) errors.email = 'Escriba un correo válido o marque «No tiene».'
  return errors
}

// Garantía sobre la orden entregada: falla reportada, estado físico, entrega, técnico y, si cambió, el contacto
export default function WarrantyModal({ order, technicians, onClose, onCreated }) {
  const formId = useId()
  const { showError, showSuccess } = useToast()
  const today = todayInBogota()
  const [initial] = useState(() => initialForm(order))
  const [form, setForm] = useState(initial)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [{ noPhone: 'phone', noEmail: 'email' }[field] ?? field]: undefined }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const validation = validate(form, today)
    setErrors(validation)
    if (Object.keys(validation).length > 0) {
      return
    }
    setSaving(true)
    try {
      const warranty = await interventionService.openWarranty(order.id, {
        serviceType: form.serviceType,
        problemDescription: form.problemDescription.trim(),
        physicalCondition: form.physicalCondition.trim(),
        unlockCode: form.unlockCode || null,
        estimatedDeliveryDate: form.estimatedDeliveryDate,
        estimatedDeliveryTime: form.estimatedDeliveryTime,
        technicianId: form.technicianId ? Number(form.technicianId) : null,
        phone: form.noPhone ? null : form.phone,
        noPhone: form.noPhone,
        email: form.noEmail ? null : form.email.trim(),
        noEmail: form.noEmail,
      })
      showSuccess(`Se abrió la garantía ${warranty.code}. El cliente la consulta con el código y la clave de ${order.code}.`)
      onCreated(warranty)
    } catch (requestError) {
      setErrors(getFieldErrors(requestError))
      showError(getErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open
      size="lg"
      title={`Abrir garantía de ${order.code}`}
      onClose={onClose}
      isDirty={JSON.stringify(form) !== JSON.stringify(initial)}
      footer={
        <>
          <button type="button" className="btn btn--secondary" onClick={onClose} disabled={saving}>Cancelar</button>
          <button type="submit" form={formId} className="btn btn--primary" disabled={saving}>{saving ? 'Abriendo…' : 'Abrir garantía'}</button>
        </>
      }
    >
      <form id={formId} className="warranty-modal" onSubmit={handleSubmit} noValidate>
        <p className="warranty-modal__info">
          Garantía vigente hasta el <strong>{formatDate(order.warrantyUntil)}</strong>. Mano de obra y repuestos van a precio 0 y no tienen cobro; la garantía no extiende la fecha.
        </p>
        <fieldset className="intake-section">
          <legend className="intake-section__title">Contacto del cliente</legend>
          <div className="form-grid">
            <ContactField
              id="warranty-phone"
              label="Celular"
              error={errors.phone}
              value={form.phone}
              noValue={form.noPhone}
              noValueLabel="No tiene celular"
              inputProps={{ inputMode: 'numeric', maxLength: 10 }}
              onValueChange={(value) => update('phone', value.replace(/\D/g, ''))}
              onNoValueChange={(value) => update('noPhone', value)}
            />
            <ContactField
              id="warranty-email"
              label="Correo"
              error={errors.email}
              value={form.email}
              noValue={form.noEmail}
              noValueLabel="No tiene correo"
              inputProps={{ type: 'email', maxLength: 150 }}
              onValueChange={(value) => update('email', value)}
              onNoValueChange={(value) => update('noEmail', value)}
            />
          </div>
        </fieldset>
        <fieldset className="intake-section">
          <legend className="intake-section__title">Falla reportada</legend>
          <div className="form-grid">
            <FormField id={`${formId}-service`} label="Servicio" error={errors.serviceType} required>
              {(props) => (
                <select {...props} className="form-input" value={form.serviceType} onChange={(event) => update('serviceType', event.target.value)}>
                  <option value="">Elija el servicio</option>
                  {toOptions(SERVICE_TYPE_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
                </select>
              )}
            </FormField>
            <FormField id={`${formId}-unlock`} label="Contraseña o patrón de desbloqueo" hint="Opcional. Se guarda cifrada.">
              {(props) => <UnlockCodeField {...props} value={form.unlockCode} onChange={(unlockCode) => update('unlockCode', unlockCode)} />}
            </FormField>
            <FormField id={`${formId}-problem`} label="Problema reportado" error={errors.problemDescription} required className="form-grid__full">
              {(props) => <textarea {...props} className="form-input" maxLength={1000} value={form.problemDescription} onChange={(event) => update('problemDescription', event.target.value)} />}
            </FormField>
            <FormField id={`${formId}-physical`} label="Estado físico" error={errors.physicalCondition} required className="form-grid__full">
              {(props) => <textarea {...props} className="form-input" maxLength={1000} value={form.physicalCondition} onChange={(event) => update('physicalCondition', event.target.value)} />}
            </FormField>
            <FormField id={`${formId}-technician`} label="Técnico">
              {(props) => (
                <select {...props} className="form-input" value={form.technicianId} onChange={(event) => update('technicianId', event.target.value)}>
                  <option value="">Sin asignar</option>
                  {technicians.map((technician) => (
                    <option key={technician.id} value={technician.id} disabled={technician.availability === 'ABSENT'}>
                      {technician.fullName} — {AVAILABILITY_LABELS[technician.availability] ?? 'Sin disponibilidad'}
                    </option>
                  ))}
                </select>
              )}
            </FormField>
            <FormField id={`${formId}-delivery`} label="Fecha estimada de entrega" error={errors.estimatedDeliveryDate} required>
              {(props) => <input {...props} type="date" className="form-input" min={today} value={form.estimatedDeliveryDate} onChange={(event) => update('estimatedDeliveryDate', event.target.value)} />}
            </FormField>
            <FormField id={`${formId}-delivery-time`} label="Hora estimada de entrega" error={errors.estimatedDeliveryTime} required>
              {(props) => (
                <input
                  {...props}
                  type="time"
                  className="form-input"
                  min={form.estimatedDeliveryDate === today ? currentTimeInBogota() : undefined}
                  value={form.estimatedDeliveryTime}
                  onChange={(event) => update('estimatedDeliveryTime', event.target.value)}
                />
              )}
            </FormField>
          </div>
        </fieldset>
      </form>
    </Modal>
  )
}
