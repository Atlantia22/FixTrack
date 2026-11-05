import { useId, useState } from 'react'
import { MdAddBox, MdSend } from 'react-icons/md'
import FormField from '../../../components/FormField/FormField'
import { useToast } from '../../../hooks/useToast'
import * as interventionService from '../../../services/interventionService'
import * as partRequestService from '../../../services/partRequestService'
import { getErrorMessage, getFieldErrors } from '../../../utils/errors'
import { formatCurrency } from '../../../utils/format'
import { QUALITY_LABELS, toOptions } from '../../../utils/labels'
import SearchSelect from '../../../components/SearchSelect/SearchSelect'
import './NewSparePartForm.css'

const MONEY_PATTERN = /^\d{1,10}(\.\d{1,2})?$/
const QUANTITY_PATTERN = /^\d+$/

// Repuesto que no existe en el inventario: si el técnico ya lo tiene, entra al inventario y se usa en la orden;
// si no, se crea sin stock y se solicita al coordinador, que al recibirlo lo carga a la orden
export default function NewSparePartForm({ order, partTypes, initialPartTypeId, onCancel, onCreated }) {
  const formId = useId()
  const { showError, showSuccess } = useToast()
  const deviceName = `${order.device.brandName} ${order.device.modelName}`
  const [form, setForm] = useState({
    source: 'have', partTypeId: initialPartTypeId, name: '', quality: '', costPrice: '', salePrice: '', quantity: '1', quantityUsed: '1',
  })
  const [nameEdited, setNameEdited] = useState(false)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const haveIt = form.source === 'have'

  // Mientras el técnico no escriba su propio nombre, se arma con el tipo y el equipo («Pantalla Samsung Galaxy A15»)
  const typeName = partTypes.find((type) => String(type.id) === form.partTypeId)?.name
  const name = nameEdited ? form.name : (typeName ? `${typeName} ${deviceName}` : '')

  function update(field, value) {
    setForm((current) => ({
      ...current,
      [field]: value,
      // Lo usado sigue a lo conseguido hasta que el técnico lo cambie a mano
      ...(field === 'quantity' && current.quantityUsed === current.quantity ? { quantityUsed: value } : {}),
    }))
    setErrors((current) => (current[field] ? { ...current, [field]: undefined } : current))
  }

  function validate() {
    const validation = {}
    if (!form.partTypeId) validation.partTypeId = 'Elija el tipo de repuesto.'
    if (!name.trim()) validation.name = 'Escriba el nombre del repuesto.'
    if (!MONEY_PATTERN.test(form.costPrice)) validation.costPrice = 'Escriba el costo, con máximo 2 decimales.'
    if (!MONEY_PATTERN.test(form.salePrice)) validation.salePrice = 'Escriba el precio de venta, con máximo 2 decimales.'
    else if (Number(form.salePrice) <= 0) validation.salePrice = 'El precio de venta debe ser mayor que cero.'
    if (!QUANTITY_PATTERN.test(form.quantity) || Number(form.quantity) < 1) validation.quantity = 'Debe ser un entero mayor que cero.'
    if (haveIt) {
      if (!QUANTITY_PATTERN.test(form.quantityUsed) || Number(form.quantityUsed) < 1) validation.quantityUsed = 'Debe ser un entero mayor que cero.'
      else if (Number(form.quantityUsed) > Number(form.quantity)) validation.quantityUsed = 'No puede ser mayor que lo conseguido.'
    }
    return validation
  }

  function newPart() {
    return {
      partTypeId: Number(form.partTypeId),
      name: name.trim(),
      quality: form.quality || null,
      costPrice: Number(form.costPrice),
      salePrice: Number(form.salePrice),
    }
  }

  // Con «Ya lo tengo» entra al inventario y se usa en la orden; con «Pedirlo» queda como solicitud al coordinador
  async function save() {
    if (haveIt) {
      const parts = await interventionService.addNewPart(order.id, {
        part: newPart(),
        quantityObtained: Number(form.quantity),
        quantityUsed: Number(form.quantityUsed),
      })
      const left = Number(form.quantity) - Number(form.quantityUsed)
      showSuccess(`«${name.trim()}» entró al inventario y se usó en la orden (${form.quantityUsed}).${left > 0 ? ` Quedan ${left} en inventario.` : ''} Total ${formatCurrency(parts.total)}.`)
      return
    }
    const created = await partRequestService.createPartRequest(order.id, {
      sparePartId: null,
      description: null,
      quantity: Number(form.quantity),
      estimatedCost: null,
      newPart: newPart(),
    })
    showSuccess(`«${created.description}» quedó en el inventario y se solicitó al coordinador (#${created.id}); la orden pasó a «Esperando repuestos».`)
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const validation = validate()
    if (Object.keys(validation).length > 0) {
      setErrors(validation)
      return
    }
    setSaving(true)
    try {
      await save()
      onCreated()
    } catch (requestError) {
      const fieldErrors = getFieldErrors(requestError)
      setErrors(Object.fromEntries(Object.entries(fieldErrors).map(([field, message]) => [field.split('.').at(-1), message])))
      showError(getErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="new-part" onSubmit={handleSubmit} noValidate aria-labelledby={`${formId}-title`}>
      <h4 id={`${formId}-title`} className="new-part__title">Otro repuesto: agregarlo al inventario</h4>
      <div className="new-part__sources" role="radiogroup" aria-label="¿Tiene el repuesto?">
        <label><input type="radio" name={`${formId}-source`} checked={haveIt} onChange={() => update('source', 'have')} /> Ya lo tengo (lo conseguí por otro lado)</label>
        <label><input type="radio" name={`${formId}-source`} checked={!haveIt} onChange={() => update('source', 'request')} /> Pedirlo al coordinador</label>
      </div>
      <p className="text-sm text-muted">
        {haveIt
          ? `Se crea para el ${deviceName}, entra al inventario con la cantidad conseguida y se carga a esta orden lo que use.`
          : `Se crea para el ${deviceName} sin stock y se envía como solicitud al coordinador. Cuando lo reciba, entra al inventario y se carga a esta orden.`}
      </p>
      <div className="new-part__grid">
        <FormField id={`${formId}-type`} label="Tipo" error={errors.partTypeId} required>
          {(props) => (
            <SearchSelect
              {...props}
              options={partTypes.map((type) => ({ value: type.id, label: type.name }))}
              value={form.partTypeId}
              placeholder="Escriba el tipo"
              emptyMessage="No hay tipos con ese nombre"
              onChange={(value) => update('partTypeId', value)}
            />
          )}
        </FormField>
        <FormField id={`${formId}-quality`} label="Calidad" hint="Opcional.">
          {(props) => (
            <select {...props} className="form-input" value={form.quality} onChange={(event) => update('quality', event.target.value)}>
              <option value="">Sin calidad</option>
              {toOptions(QUALITY_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
            </select>
          )}
        </FormField>
        <FormField id={`${formId}-name`} label="Nombre" error={errors.name} required className="new-part__full">
          {(props) => (
            <input
              {...props}
              className="form-input"
              maxLength={150}
              value={name}
              onChange={(event) => {
                setNameEdited(true)
                update('name', event.target.value)
              }}
            />
          )}
        </FormField>
        <FormField id={`${formId}-cost`} label="Costo por unidad" error={errors.costPrice} required>
          {(props) => <input {...props} className="form-input" inputMode="decimal" value={form.costPrice} onChange={(event) => update('costPrice', event.target.value.replace(/[^\d.]/g, ''))} />}
        </FormField>
        <FormField id={`${formId}-sale`} label="Precio de venta" error={errors.salePrice} hint="Con instalación incluida." required>
          {(props) => <input {...props} className="form-input" inputMode="decimal" value={form.salePrice} onChange={(event) => update('salePrice', event.target.value.replace(/[^\d.]/g, ''))} />}
        </FormField>
        <FormField id={`${formId}-quantity`} label={haveIt ? 'Cantidad conseguida' : 'Cantidad'} error={errors.quantity ?? errors.quantityObtained} required>
          {(props) => <input {...props} className="form-input" inputMode="numeric" value={form.quantity} onChange={(event) => update('quantity', event.target.value.replace(/\D/g, ''))} />}
        </FormField>
        {haveIt && (
          <FormField id={`${formId}-used`} label="Usar en esta orden" error={errors.quantityUsed} hint="Lo que sobre queda en el inventario." required>
            {(props) => <input {...props} className="form-input" inputMode="numeric" value={form.quantityUsed} onChange={(event) => update('quantityUsed', event.target.value.replace(/\D/g, ''))} />}
          </FormField>
        )}
      </div>
      <div className="new-part__actions">
        <button type="button" className="btn btn--secondary" onClick={onCancel} disabled={saving}>Cancelar</button>
        <button type="submit" className="btn btn--primary" disabled={saving}>
          {haveIt ? <MdAddBox aria-hidden="true" /> : <MdSend aria-hidden="true" />}
          {saving ? 'Guardando…' : (haveIt ? 'Agregar al inventario y usar' : 'Agregar al inventario y solicitar')}
        </button>
      </div>
    </form>
  )
}
