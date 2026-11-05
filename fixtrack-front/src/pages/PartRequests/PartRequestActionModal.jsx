import { useId, useState } from 'react'
import FormField from '../../components/FormField/FormField'
import Modal from '../../components/Modal/Modal'
import { useToast } from '../../hooks/useToast'
import * as partRequestService from '../../services/partRequestService'
import { getErrorMessage } from '../../utils/errors'
import { formatCurrency } from '../../utils/format'
import SearchSelect from '../../components/SearchSelect/SearchSelect'
import './PartRequestActionModal.css'

const MONEY_PATTERN = /^\d{1,10}(\.\d{1,2})?$/
const TITLES = { approve: 'Aprobar solicitud', reject: 'Rechazar solicitud', order: 'Pedir al proveedor', receive: 'Recibir repuesto' }

// Acción del coordinador sobre una solicitud: aprobar (y asociar el repuesto del catálogo), rechazar con motivo,
// pedir al proveedor (sugerido el preferido) o recibir con el costo real
export default function PartRequestActionModal({ action, request, parts, suppliers, onClose, onDone }) {
  const formId = useId()
  const { showError, showSuccess } = useToast()
  const [form, setForm] = useState({
    sparePartId: request.sparePartId ? String(request.sparePartId) : '',
    reason: '',
    supplierId: request.suggestedSupplier ? String(request.suggestedSupplier.id) : '',
    estimatedCost: request.estimatedCost != null ? String(Number(request.estimatedCost)) : '',
    actualCost: request.estimatedCost != null ? String(Number(request.estimatedCost)) : '',
  })
  const [error, setError] = useState(null)
  const [saving, setSaving] = useState(false)

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
    setError(null)
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (action === 'reject' && !form.reason.trim()) return setError('Escriba el motivo del rechazo.')
    if (action === 'order' && !form.supplierId) return setError('Elija el proveedor.')
    if (action === 'order' && form.estimatedCost && !MONEY_PATTERN.test(form.estimatedCost)) return setError('Costo con máximo 2 decimales.')
    if (action === 'receive' && !MONEY_PATTERN.test(form.actualCost)) return setError('Escriba el costo real por unidad.')
    if (action === 'receive' && !form.sparePartId) return setError('Asocie el repuesto del catálogo que llegó.')
    setSaving(true)
    try {
      let message
      if (action === 'approve') {
        await partRequestService.approve(request.id, { sparePartId: form.sparePartId })
        message = `Solicitud #${request.id} aprobada.`
      } else if (action === 'reject') {
        await partRequestService.reject(request.id, form.reason.trim())
        message = `Solicitud #${request.id} rechazada.`
      } else if (action === 'order') {
        const ordered = await partRequestService.order(request.id, { supplierId: form.supplierId, estimatedCost: form.estimatedCost ? Number(form.estimatedCost) : null })
        message = `Solicitud #${request.id} pedida a ${ordered.supplierName}.`
      } else {
        const receipt = await partRequestService.receive(request.id, { actualCost: Number(form.actualCost), sparePartId: form.sparePartId })
        message = receipt.consumedInOrder
          ? `Se recibió y se registró como usado en la orden ${request.interventionCode}.`
          : `Se recibió; la orden ${request.interventionCode} ya no lo usa y quedó en inventario (stock ${receipt.stockAfter}).`
      }
      showSuccess(message)
      onDone()
    } catch (requestError) {
      showError(getErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  const partSelect = (label, hint) => (
    <FormField id={`${formId}-part`} label={label} hint={hint}>
      {(props) => (
        <SearchSelect
          {...props}
          options={parts.map((part) => ({ value: part.id, label: `${part.name} · ${part.sku}` }))}
          value={form.sparePartId}
          placeholder="Sin asociar: escriba el nombre o SKU"
          emptyMessage="No hay repuestos con ese nombre o SKU"
          onChange={(value) => update('sparePartId', value)}
        />
      )}
    </FormField>
  )

  return (
    <Modal
      open
      size="sm"
      title={TITLES[action]}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn--secondary" onClick={onClose} disabled={saving}>Volver</button>
          <button type="submit" form={formId} className={`btn ${action === 'reject' ? 'btn--danger' : 'btn--primary'}`} disabled={saving}>
            {saving ? 'Guardando…' : TITLES[action]}
          </button>
        </>
      }
    >
      <form id={formId} className="part-request-action" onSubmit={handleSubmit} noValidate>
        <p className="part-request-action__summary">
          <strong>#{request.id} · {request.description}</strong> × {request.quantity}
          <span>Orden {request.interventionCode} · pedida por {request.requestedByName}</span>
        </p>
        {action === 'approve' && !request.sparePartId && partSelect('Repuesto del catálogo', 'Opcional: asócielo si ya existe en el catálogo.')}
        {action === 'reject' && (
          <FormField id={`${formId}-reason`} label="Motivo" required>
            {(props) => <textarea {...props} className="form-input" maxLength={500} value={form.reason} onChange={(event) => update('reason', event.target.value)} />}
          </FormField>
        )}
        {action === 'order' && (
          <>
            <FormField id={`${formId}-supplier`} label="Proveedor" hint={request.suggestedSupplier ? `Sugerido: ${request.suggestedSupplier.name}${request.suggestedSupplier.leadTimeDays != null ? ` (${request.suggestedSupplier.leadTimeDays} días)` : ''}.` : 'El repuesto no tiene proveedor preferido.'} required>
              {(props) => (
                <SearchSelect
                  {...props}
                  options={suppliers.map((supplier) => ({ value: supplier.id, label: supplier.name }))}
                  value={form.supplierId}
                  placeholder="Escriba el proveedor"
                  emptyMessage="No hay proveedores con ese nombre"
                  onChange={(value) => update('supplierId', value)}
                />
              )}
            </FormField>
            <FormField id={`${formId}-estimated`} label="Costo estimado por unidad" hint={request.suggestedSupplier?.lastCost != null ? `Último costo del preferido: ${formatCurrency(request.suggestedSupplier.lastCost)}.` : undefined}>
              {(props) => <input {...props} className="form-input" inputMode="decimal" value={form.estimatedCost} onChange={(event) => update('estimatedCost', event.target.value.replace(/[^\d.]/g, ''))} />}
            </FormField>
          </>
        )}
        {action === 'receive' && (
          <>
            {!request.sparePartId && partSelect('Repuesto del catálogo que llegó', 'Obligatorio: entra al inventario y se usa en la orden.')}
            <FormField id={`${formId}-actual`} label="Costo real por unidad" hint="Queda como último costo del proveedor." required>
              {(props) => <input {...props} className="form-input" inputMode="decimal" value={form.actualCost} onChange={(event) => update('actualCost', event.target.value.replace(/[^\d.]/g, ''))} />}
            </FormField>
          </>
        )}
        {error && <p className="form-error" role="alert">{error}</p>}
      </form>
    </Modal>
  )
}
