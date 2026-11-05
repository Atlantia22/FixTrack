import { useId, useState } from 'react'
import FormField from '../../components/FormField/FormField'
import Modal from '../../components/Modal/Modal'
import { useToast } from '../../hooks/useToast'
import * as sparePartService from '../../services/sparePartService'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { QUALITY_LABELS, toOptions } from '../../utils/labels'
import SearchSelect from '../../components/SearchSelect/SearchSelect'
import './SparePartFormModal.css'

const MONEY_PATTERN = /^\d{1,10}(\.\d{1,2})?$/

function initialForm(part, models) {
  const model = models.find((item) => item.id === part?.deviceModelId)
  return {
    name: part?.name ?? '',
    sku: part?.sku ?? '',
    description: part?.description ?? '',
    partTypeId: part ? String(part.partTypeId) : '',
    brandId: model ? String(model.brandId) : '',
    deviceModelId: part ? String(part.deviceModelId) : '',
    quality: part?.quality ?? '',
    minStock: part ? String(part.minStock) : '',
    costPrice: part ? String(part.costPrice) : '',
    salePrice: part ? String(part.salePrice) : '',
  }
}

function validate(form) {
  const errors = {}
  if (!form.name.trim()) errors.name = 'Escriba el nombre del repuesto.'
  if (!form.sku.trim()) errors.sku = 'Escriba el SKU.'
  if (!form.partTypeId) errors.partTypeId = 'Elija el tipo de repuesto.'
  if (!form.deviceModelId) errors.deviceModelId = 'Elija el modelo principal.'
  if (form.minStock !== '' && !/^\d+$/.test(form.minStock)) errors.minStock = 'Debe ser un número entero de 0 en adelante.'
  if (!MONEY_PATTERN.test(form.costPrice)) errors.costPrice = 'Escriba un valor de 0 en adelante, con máximo 2 decimales.'
  if (!MONEY_PATTERN.test(form.salePrice)) errors.salePrice = 'Escriba un valor de 0 en adelante, con máximo 2 decimales.'
  return errors
}

// Registro y edición de un repuesto; el stock no se edita aquí, cambia con entradas y consumos
export default function SparePartFormModal({ part, partTypes, brands, models, onClose, onSaved }) {
  const formId = useId()
  const { showError, showSuccess } = useToast()
  const isNew = !part
  const [initial] = useState(() => initialForm(part, models))
  const [form, setForm] = useState(initial)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  const brandModels = models.filter((model) => String(model.brandId) === form.brandId)

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => (current[field] ? { ...current, [field]: undefined } : current))
  }

  function changeBrand(brandId) {
    setForm((current) => ({ ...current, brandId, deviceModelId: '' }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const validation = validate(form)
    setErrors(validation)
    if (Object.keys(validation).length > 0) {
      return
    }
    const request = {
      name: form.name.trim(),
      sku: form.sku.trim(),
      description: form.description.trim(),
      partTypeId: Number(form.partTypeId),
      deviceModelId: Number(form.deviceModelId),
      quality: form.quality || null,
      minStock: form.minStock === '' ? null : Number(form.minStock),
      costPrice: Number(form.costPrice),
      salePrice: Number(form.salePrice),
    }
    setSaving(true)
    try {
      const saved = isNew
        ? await sparePartService.createSparePart(request)
        : await sparePartService.updateSparePart(part.id, request)
      showSuccess(isNew ? `Se registró «${saved.name}» con stock mínimo ${saved.minStock}.` : `Se guardaron los cambios de «${saved.name}».`)
      onSaved()
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
      title={isNew ? 'Nuevo repuesto' : `Editar «${part.name}»`}
      onClose={onClose}
      isDirty={JSON.stringify(form) !== JSON.stringify(initial)}
      footer={
        <>
          <button type="button" className="btn btn--secondary" onClick={onClose} disabled={saving}>Cancelar</button>
          <button type="submit" form={formId} className="btn btn--primary" disabled={saving}>
            {saving ? 'Guardando…' : 'Guardar'}
          </button>
        </>
      }
    >
      <form id={formId} className="form-grid" onSubmit={handleSubmit} noValidate>
        <FormField id={`${formId}-name`} label="Nombre" error={errors.name} required className="form-grid__full">
          {(props) => <input {...props} className="form-input" maxLength={150} value={form.name} onChange={(event) => update('name', event.target.value)} />}
        </FormField>
        <FormField id={`${formId}-sku`} label="SKU" error={errors.sku} required>
          {(props) => <input {...props} className="form-input" maxLength={50} autoComplete="off" value={form.sku} onChange={(event) => update('sku', event.target.value)} />}
        </FormField>
        <FormField id={`${formId}-type`} label="Tipo de repuesto" error={errors.partTypeId} required>
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
        <FormField id={`${formId}-brand`} label="Marca" required>
          {(props) => (
            <SearchSelect
              {...props}
              options={brands.map((brand) => ({ value: brand.id, label: brand.name }))}
              value={form.brandId}
              placeholder="Escriba la marca"
              emptyMessage="No hay marcas con ese nombre"
              onChange={changeBrand}
            />
          )}
        </FormField>
        <FormField id={`${formId}-model`} label="Modelo principal" error={errors.deviceModelId} required>
          {(props) => (
            <SearchSelect
              {...props}
              options={brandModels.map((model) => ({ value: model.id, label: model.name }))}
              value={form.deviceModelId}
              disabled={!form.brandId}
              placeholder={form.brandId ? 'Escriba el modelo' : 'Primero elija la marca'}
              emptyMessage="No hay modelos con ese nombre"
              onChange={(value) => update('deviceModelId', value)}
            />
          )}
        </FormField>
        <FormField
          id={`${formId}-quality`}
          label="Calidad"
          hint={!isNew && part.quality ? 'La calidad asignada no se puede quitar.' : 'Opcional. Cada calidad es un repuesto distinto.'}
        >
          {(props) => (
            <select {...props} className="form-input" value={form.quality} onChange={(event) => update('quality', event.target.value)}>
              {(isNew || !part.quality) && <option value="">Sin calidad</option>}
              {toOptions(QUALITY_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
            </select>
          )}
        </FormField>
        <FormField
          id={`${formId}-minStock`}
          label="Stock mínimo"
          error={errors.minStock}
          hint={isNew ? 'Si lo deja vacío se usa el stock mínimo por defecto.' : undefined}
        >
          {(props) => <input {...props} className="form-input" inputMode="numeric" value={form.minStock} onChange={(event) => update('minStock', event.target.value.replace(/\D/g, ''))} />}
        </FormField>
        <FormField id={`${formId}-cost`} label="Costo" error={errors.costPrice} required>
          {(props) => <input {...props} className="form-input" inputMode="decimal" value={form.costPrice} onChange={(event) => update('costPrice', event.target.value.replace(',', '.'))} />}
        </FormField>
        <FormField id={`${formId}-sale`} label="Precio de venta" error={errors.salePrice} hint="Con instalación incluida: es lo que paga el cliente por el repuesto puesto." required>
          {(props) => <input {...props} className="form-input" inputMode="decimal" value={form.salePrice} onChange={(event) => update('salePrice', event.target.value.replace(',', '.'))} />}
        </FormField>
        <FormField id={`${formId}-description`} label="Descripción" error={errors.description} className="form-grid__full">
          {(props) => <textarea {...props} className="form-input" maxLength={500} value={form.description} onChange={(event) => update('description', event.target.value)} />}
        </FormField>
        {!isNew && (
          <p className="spare-part-form__stock form-grid__full">
            Stock actual: <strong>{part.stock}</strong>. El stock cambia con las entradas de mercancía y los consumos en órdenes.
          </p>
        )}
      </form>
    </Modal>
  )
}
