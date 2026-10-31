import { useId, useState } from 'react'
import { MdPrint } from 'react-icons/md'
import Modal from '../../../components/Modal/Modal'
import { useToast } from '../../../hooks/useToast'
import * as deviceService from '../../../services/deviceService'
import * as interventionService from '../../../services/interventionService'
import * as paymentService from '../../../services/paymentService'
import * as personService from '../../../services/personService'
import { cleanDocumentNumber, documentNumberError } from '../../../utils/document'
import { getErrorMessage, getFieldErrors } from '../../../utils/errors'
import { printPdf } from '../../../utils/files'
import { todayInBogota } from '../../../utils/format'
import CustomerSection from './CustomerSection'
import DepositSection from './DepositSection'
import DeviceSection from './DeviceSection'
import IntakeResult from './IntakeResult'
import ServiceSection from './ServiceSection'
import './IntakeModal.css'

const PHONE_PATTERN = /^\d{7,10}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const MONEY_PATTERN = /^\d{1,10}(\.\d{1,2})?$/

const EMPTY_CUSTOMER = { documentType: 'CC', documentNumber: '', fullName: '', phone: '', noPhone: false, email: '', noEmail: false }
const EMPTY_DEPOSIT = { amount: '', method: 'CASH' }
const EMPTY_DEVICE = { deviceId: '', brandId: '', deviceModelId: '', imei: '', serialNumber: '', color: '', observations: '' }

function emptyService() {
  return { serviceTypes: [], unlockCode: '', problemDescription: '', physicalCondition: '', technicianId: '', estimatedPrice: '', estimatedDeliveryDate: todayInBogota() }
}

// Reglas del ingreso: documento buscado, «No tiene» explícito, IMEI en teléfonos y entrega desde hoy
function validate({ customer, lookup, device, models, service, deposit, today }) {
  const errors = {}
  if (!customer.documentNumber.trim()) errors.documentNumber = 'Escriba el número de documento.'
  else if (!lookup.done) errors.documentNumber = 'Busque el documento para continuar.'
  if (lookup.done && !lookup.person && !customer.fullName.trim()) errors.fullName = 'Escriba el nombre del cliente.'
  if (lookup.done && !customer.noPhone && !PHONE_PATTERN.test(customer.phone)) {
    errors.phone = customer.phone ? 'Debe tener entre 7 y 10 dígitos.' : 'Escriba el celular o marque «No tiene».'
  }
  if (lookup.done && !customer.noEmail && !EMAIL_PATTERN.test(customer.email.trim())) {
    errors.email = customer.email ? 'Escriba un correo válido.' : 'Escriba el correo o marque «No tiene».'
  }
  if (device.deviceId === '') {
    const model = models.find((item) => String(item.id) === device.deviceModelId)
    if (!model) errors.deviceModelId = 'Elija la marca y el modelo del equipo.'
    if (model?.deviceType === 'PHONE' && !/^\d{15}$/.test(device.imei)) errors.imei = 'El IMEI de los teléfonos tiene 15 dígitos.'
    else if (device.imei && !/^\d{15}$/.test(device.imei)) errors.imei = 'El IMEI tiene 15 dígitos.'
  }
  if (service.serviceTypes.length === 0) errors.serviceTypes = 'Agregue al menos un daño.'
  if (!service.problemDescription.trim()) errors.problemDescription = 'Describa el problema.'
  if (!service.physicalCondition.trim()) errors.physicalCondition = 'Describa el estado físico del equipo.'
  if (!MONEY_PATTERN.test(service.estimatedPrice)) errors.estimatedPrice = 'Escriba el presupuesto, con máximo 2 decimales.'
  if (!service.estimatedDeliveryDate) errors.estimatedDeliveryDate = 'Elija la fecha estimada de entrega.'
  else if (service.estimatedDeliveryDate < today) errors.estimatedDeliveryDate = 'No puede ser anterior a hoy.'
  if (deposit.amount && (!MONEY_PATTERN.test(deposit.amount) || Number(deposit.amount) <= 0)) {
    errors.depositAmount = 'Escriba un valor mayor que cero, con máximo 2 decimales.'
  }
  return errors
}

function toRequest({ customer, lookup, device, service }) {
  return {
    customer: {
      documentType: customer.documentType,
      documentNumber: customer.documentNumber.trim(),
      fullName: lookup.person ? null : customer.fullName.trim(),
      phone: customer.noPhone ? null : customer.phone,
      noPhone: customer.noPhone,
      email: customer.noEmail ? null : customer.email.trim(),
      noEmail: customer.noEmail,
    },
    device: device.deviceId
      ? { deviceId: Number(device.deviceId) }
      : {
        deviceModelId: Number(device.deviceModelId),
        imei: device.imei || null,
        serialNumber: device.serialNumber.trim() || null,
        color: device.color.trim() || null,
        observations: device.observations.trim() || null,
      },
    serviceTypes: service.serviceTypes,
    problemDescription: service.problemDescription.trim(),
    physicalCondition: service.physicalCondition.trim(),
    unlockCode: service.unlockCode || null,
    estimatedPrice: Number(service.estimatedPrice),
    estimatedDeliveryDate: service.estimatedDeliveryDate,
    technicianId: service.technicianId ? Number(service.technicianId) : null,
  }
}

// Ingreso de un equipo en un modal grande por secciones; al guardar muestra el código y la clave de consulta
export default function IntakeModal({ brands, models, technicians, onClose, onCreated, onOpenOrder }) {
  const formId = useId()
  const { showError } = useToast()
  const today = todayInBogota()
  const [customer, setCustomer] = useState(EMPTY_CUSTOMER)
  const [lookup, setLookup] = useState({ done: false, loading: false, person: null })
  const [devices, setDevices] = useState([])
  const [device, setDevice] = useState(EMPTY_DEVICE)
  const [service, setService] = useState(emptyService)
  const [deposit, setDeposit] = useState(EMPTY_DEPOSIT)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)
  const [result, setResult] = useState(null)
  const [printing, setPrinting] = useState(false)

  const isDirty = !result && (customer.documentNumber !== '' || service.problemDescription !== '' || device.deviceModelId !== '')

  function clearError(field) {
    setErrors((current) => (current[field] ? { ...current, [field]: undefined } : current))
  }

  // Cambiar el documento invalida la búsqueda anterior y los equipos que trajo
  function changeCustomer(field, value) {
    setCustomer((current) => ({
      ...current,
      [field]: value,
      // Al cambiar el tipo, el número se ajusta a lo que el nuevo tipo admite
      ...(field === 'documentType' ? { documentNumber: cleanDocumentNumber(value, current.documentNumber) } : {}),
    }))
    clearError({ noPhone: 'phone', noEmail: 'email', documentType: 'documentNumber' }[field] ?? field)
    if (field === 'documentType' || field === 'documentNumber') {
      setLookup({ done: false, loading: false, person: null })
      setDevices([])
      setDevice(EMPTY_DEVICE)
    }
  }

  function changeDevice(field, value) {
    setDevice((current) => ({ ...current, [field]: value, ...(field === 'brandId' ? { deviceModelId: '' } : {}) }))
    clearError(field === 'brandId' ? 'deviceModelId' : field)
  }

  function changeDeposit(field, value) {
    setDeposit((current) => ({ ...current, [field]: value }))
    clearError('depositAmount')
  }

  function changeService(field, value) {
    setService((current) => ({ ...current, [field]: value }))
    clearError(field)
  }

  // Busca al cliente por documento; si existe trae sus datos de contacto y sus equipos
  async function handleLookup() {
    const documentError = documentNumberError(customer.documentType, customer.documentNumber.trim())
    if (documentError) {
      setErrors((current) => ({ ...current, documentNumber: documentError }))
      return
    }
    setLookup({ done: false, loading: true, person: null })
    clearError('documentNumber')
    try {
      const person = await personService.findPersonByDocument(customer.documentType, customer.documentNumber.trim())
      const ownedDevices = await deviceService.listDevicesByOwner(person.id)
      setCustomer((current) => ({
        ...current,
        fullName: person.fullName,
        phone: person.phone ?? '',
        noPhone: !person.phone,
        email: person.email ?? '',
        noEmail: !person.email,
      }))
      setDevices(ownedDevices)
      setDevice({ ...EMPTY_DEVICE, deviceId: ownedDevices[0] ? String(ownedDevices[0].id) : '' })
      setLookup({ done: true, loading: false, person })
    } catch (requestError) {
      if (requestError.response?.status === 404) {
        setCustomer((current) => ({ ...current, fullName: '', phone: '', noPhone: false, email: '', noEmail: false }))
        setDevices([])
        setDevice(EMPTY_DEVICE)
        setLookup({ done: true, loading: false, person: null })
      } else {
        setLookup({ done: false, loading: false, person: null })
        showError(getErrorMessage(requestError))
      }
    }
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const validation = validate({ customer, lookup, device, models, service, deposit, today })
    setErrors(validation)
    if (Object.keys(validation).length > 0) {
      showError('Revise los campos marcados antes de guardar.')
      return
    }
    setSaving(true)
    try {
      const created = await interventionService.createIntervention(toRequest({ customer, lookup, device, service }))
      // La orden ya existe; si el abono falla se avisa sin perder el comprobante
      let depositResult = null
      if (deposit.amount) {
        try {
          await paymentService.registerPayment(created.intervention.id, { type: 'DEPOSIT', method: deposit.method, amount: Number(deposit.amount) })
          depositResult = { amount: Number(deposit.amount), method: deposit.method }
        } catch (depositError) {
          depositResult = { error: getErrorMessage(depositError) }
        }
      }
      setResult({ ...created, deposit: depositResult })
      onCreated()
    } catch (requestError) {
      setErrors(flattenFieldErrors(getFieldErrors(requestError)))
      showError(getErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  // Abre el diálogo de impresión del comprobante con la clave que se acaba de generar
  async function handlePrint() {
    setPrinting(true)
    try {
      await printPdf(() => interventionService.getIntakeReceipt(result.intervention.id, result.accessKey))
    } catch (requestError) {
      showError(getErrorMessage(requestError))
    } finally {
      setPrinting(false)
    }
  }

  const footer = result ? (
    <>
      <button type="button" className="btn btn--secondary" onClick={onClose}>Cerrar</button>
      <button type="button" className="btn btn--secondary" onClick={() => onOpenOrder(result.intervention.id)}>Ver la orden</button>
      <button type="button" className="btn btn--primary" disabled={printing} onClick={handlePrint}>
        <MdPrint aria-hidden="true" />
        {printing ? 'Preparando…' : 'Imprimir comprobante'}
      </button>
    </>
  ) : (
    <>
      <button type="button" className="btn btn--secondary" onClick={onClose} disabled={saving}>Cancelar</button>
      <button type="submit" form={formId} className="btn btn--primary" disabled={saving}>
        {saving ? 'Guardando…' : 'Registrar ingreso'}
      </button>
    </>
  )

  return (
    <Modal open title={result ? `Orden ${result.intervention.code}` : 'Ingreso de equipo'} size="lg" onClose={onClose} isDirty={isDirty} footer={footer}>
      {result ? (
        <IntakeResult result={result} />
      ) : (
        <form id={formId} className="intake-modal__form" onSubmit={handleSubmit} noValidate>
          <CustomerSection value={customer} errors={errors} lookup={lookup} onChange={changeCustomer} onLookup={handleLookup} />
          <DeviceSection value={device} errors={errors} devices={devices} brands={brands} models={models} onChange={changeDevice} />
          <ServiceSection value={service} errors={errors} technicians={technicians} minDate={today} onChange={changeService} />
          <DepositSection value={deposit} errors={errors} onChange={changeDeposit} />
        </form>
      )}
    </Modal>
  )
}

// Los errores de campo del backend llegan como customer.phone o device.imei; el formulario usa el último tramo
function flattenFieldErrors(fieldErrors) {
  return Object.fromEntries(Object.entries(fieldErrors).map(([field, message]) => [field.split('.').at(-1), message]))
}
