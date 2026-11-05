import { API_CONFIG } from '../config/apiConfig'

// Respuesta: { intervention, accessKey, receiptWarning }; accessKey solo llega aquí, una vez
export async function createIntervention(request) {
  const response = await API_CONFIG.post('/interventions', request)
  return response.data
}

// Filtros combinables; los vacíos no se envían
// sort es una lista como ['estimatedDeliveryDate,asc', ...]; Spring la espera repetida (sort=a&sort=b), sin corchetes
const REPEATED_PARAMS = { indexes: null }

export async function searchInterventions({ page = 0, size = 20, sort, ...filters } = {}) {
  const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '' && value != null && value !== false))
  const response = await API_CONFIG.get('/interventions', { params: { ...params, page, size, sort }, paramsSerializer: REPEATED_PARAMS })
  return response.data
}

export async function listMyInterventions({ status, overdue, inProgress, sort, page = 0, size = 20 } = {}) {
  const response = await API_CONFIG.get('/interventions/mine', {
    params: { status: status || undefined, overdue: overdue || undefined, inProgress: inProgress || undefined, page, size, sort },
    paramsSerializer: REPEATED_PARAMS,
  })
  return response.data
}

// Garantía sobre una orden entregada con garantía vigente; puede actualizar el celular y el correo del cliente
export async function openWarranty(id, request) {
  const response = await API_CONFIG.post(`/interventions/${id}/warranty`, request)
  return response.data
}

// Listas desde hace más de UNCLAIMED_ALERT_DAYS días, con el contacto del cliente (noContact si no tiene)
export async function listUnclaimed() {
  const response = await API_CONFIG.get('/interventions/unclaimed')
  return response.data
}

// Salida: órdenes entregadas hoy (Bogotá) con { id, code, customerName, device, deliveredAt }
export async function listDeliveredToday() {
  const response = await API_CONFIG.get('/interventions/delivered-today')
  return response.data
}

// Salida: órdenes listas con { order, total, paid, balance, refundDue }
export async function listReadyForDelivery() {
  const response = await API_CONFIG.get('/interventions/ready-for-delivery')
  return response.data
}

export async function getIntervention(id) {
  const response = await API_CONFIG.get(`/interventions/${id}`)
  return response.data
}

export async function getStatusHistory(id) {
  const response = await API_CONFIG.get(`/interventions/${id}/history`)
  return response.data
}

export async function getDeliveryChanges(id) {
  const response = await API_CONFIG.get(`/interventions/${id}/delivery-changes`)
  return response.data
}

export async function assignTechnician(id, technicianId) {
  const response = await API_CONFIG.patch(`/interventions/${id}/technician`, { technicianId })
  return response.data
}

export async function rescheduleDelivery(id, { newDate, newTime, reason }) {
  const response = await API_CONFIG.patch(`/interventions/${id}/estimated-delivery`, { newDate, newTime, reason })
  return response.data
}

export async function registerDiagnosis(id, { diagnosis, solution }) {
  const response = await API_CONFIG.patch(`/interventions/${id}/diagnosis`, { diagnosis, solution })
  return response.data
}

export async function changeStatus(id, { status, note }) {
  const response = await API_CONFIG.patch(`/interventions/${id}/status`, { status, note })
  return response.data
}

export async function addFinding(id, { serviceType, description }) {
  const response = await API_CONFIG.post(`/interventions/${id}/findings`, { serviceType, description })
  return response.data
}

// decision: APPROVED o REJECTED, según la respuesta del cliente
export async function answerFinding(id, findingId, decision) {
  const response = await API_CONFIG.patch(`/interventions/${id}/findings/${findingId}/approval`, { decision })
  return response.data
}

export async function setLaborCost(id, laborCost) {
  const response = await API_CONFIG.patch(`/interventions/${id}/labor`, { laborCost })
  return response.data
}

// outcome: REPAIRED, NOT_REPAIRABLE o REJECTED_BY_CUSTOMER; sin reparación exige reason y diagnosisFee
export async function registerOutcome(id, { outcome, reason, warrantyDays, diagnosisFee }) {
  const response = await API_CONFIG.patch(`/interventions/${id}/outcome`, { outcome, reason, warrantyDays, diagnosisFee })
  return response.data
}

// Falla con 409 si los pagos no cubren el total
export async function deliver(id, { notes } = {}) {
  const response = await API_CONFIG.patch(`/interventions/${id}/deliver`, { notes })
  return response.data
}

export async function cancel(id, reason) {
  const response = await API_CONFIG.patch(`/interventions/${id}/cancel`, { reason })
  return response.data
}

// Genera una clave de consulta nueva para reimprimir el comprobante; la anterior deja de servir
export async function regenerateAccessKey(id) {
  const response = await API_CONFIG.post(`/interventions/${id}/access-key`)
  return response.data
}

// PDF del comprobante de ingreso con la clave vigente; devuelve un Blob
export async function getIntakeReceipt(id, accessKey) {
  const response = await API_CONFIG.get(`/interventions/${id}/receipts/intake`, {
    headers: { 'X-Access-Key': accessKey },
    responseType: 'blob',
  })
  return response.data
}

export async function getDeliveryReceipt(id) {
  const response = await API_CONFIG.get(`/interventions/${id}/receipts/delivery`, { responseType: 'blob' })
  return response.data
}

export async function correctUsedPart(id, usageId, quantity) {
  const response = await API_CONFIG.patch(`/interventions/${id}/spare-parts/${usageId}`, { quantity })
  return response.data
}

// Respuesta: { items, partsTotal, laborCost, total }
export async function listUsedParts(id) {
  const response = await API_CONFIG.get(`/interventions/${id}/spare-parts`)
  return response.data
}

export async function listPartCandidates(id, { partTypeId, deviceModelId } = {}) {
  const response = await API_CONFIG.get(`/interventions/${id}/spare-parts/candidates`, {
    params: { partTypeId: partTypeId || undefined, deviceModelId: deviceModelId || undefined },
  })
  return response.data
}

export async function addUsedPart(id, { sparePartId, quantity }) {
  const response = await API_CONFIG.post(`/interventions/${id}/spare-parts`, { sparePartId, quantity })
  return response.data
}

// Repuesto que el técnico consiguió por fuera: se crea en el inventario con lo conseguido y se usa en la orden
export async function addNewPart(id, { part, quantityObtained, quantityUsed }) {
  const response = await API_CONFIG.post(`/interventions/${id}/spare-parts/new-part`, { part, quantityObtained, quantityUsed })
  return response.data
}

export async function removeUsedPart(id, usageId) {
  const response = await API_CONFIG.delete(`/interventions/${id}/spare-parts/${usageId}`)
  return response.data
}
