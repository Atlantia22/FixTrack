import { useId, useState } from 'react'
import FormField from '../../components/FormField/FormField'
import Modal from '../../components/Modal/Modal'
import { useToast } from '../../hooks/useToast'
import * as catalogService from '../../services/catalogService'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import { DEVICE_TYPE_LABELS, toOptions } from '../../utils/labels'
import SearchSelect from '../../components/SearchSelect/SearchSelect'
import './DeviceModelFormModal.css'

// Alta y edición de un modelo de equipo: marca, nombre y tipo
export default function DeviceModelFormModal({ model, brands, onClose, onSaved }) {
  const formId = useId()
  const { showError, showSuccess } = useToast()
  const [initial] = useState(() => ({
    brandId: String(model?.brandId ?? brands[0]?.id ?? ''),
    name: model?.name ?? '',
    deviceType: model?.deviceType ?? 'PHONE',
  }))
  const [form, setForm] = useState(initial)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => (current[field] ? { ...current, [field]: undefined } : current))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (!form.name.trim()) {
      setErrors({ name: 'Escriba el nombre del modelo.' })
      return
    }
    setSaving(true)
    try {
      const request = { brandId: Number(form.brandId), name: form.name.trim(), deviceType: form.deviceType }
      const saved = model
        ? await catalogService.updateDeviceModel(model.id, request)
        : await catalogService.createDeviceModel(request)
      showSuccess(`Se guardó el modelo «${saved.brandName} ${saved.name}».`)
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
      title={model ? `Editar ${model.brandName} ${model.name}` : 'Nuevo modelo'}
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
      <form id={formId} className="device-model-form" onSubmit={handleSubmit} noValidate>
        <FormField id={`${formId}-brand`} label="Marca" required>
          {(props) => (
            <SearchSelect
              {...props}
              options={brands.map((brand) => ({ value: brand.id, label: brand.name }))}
              value={form.brandId}
              placeholder="Escriba la marca"
              emptyMessage="No hay marcas con ese nombre"
              onChange={(value) => update('brandId', value)}
            />
          )}
        </FormField>
        <FormField id={`${formId}-name`} label="Nombre del modelo" error={errors.name} required>
          {(props) => (
            <input {...props} className="form-input" maxLength={100} value={form.name} onChange={(event) => update('name', event.target.value)} />
          )}
        </FormField>
        <FormField id={`${formId}-type`} label="Tipo de equipo" required hint="Los teléfonos exigen IMEI al recibirlos.">
          {(props) => (
            <select {...props} className="form-input" value={form.deviceType} onChange={(event) => update('deviceType', event.target.value)}>
              {toOptions(DEVICE_TYPE_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
            </select>
          )}
        </FormField>
      </form>
    </Modal>
  )
}
