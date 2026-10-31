import { API_CONFIG } from '../config/apiConfig'

// Quita los filtros vacíos para no enviarlos
function clean(params) {
  return Object.fromEntries(Object.entries(params).filter(([, value]) => value !== '' && value != null && value !== false))
}

// El técnico asignado solicita un repuesto del catálogo (sparePartId), uno nuevo para el inventario (newPart) o descrito a mano
export async function createPartRequest(interventionId, { sparePartId, description, quantity, estimatedCost, newPart = null }) {
  const response = await API_CONFIG.post(`/interventions/${interventionId}/part-requests`, { sparePartId, description, quantity, estimatedCost, newPart })
  return response.data
}

export async function listByIntervention(interventionId) {
  const response = await API_CONFIG.get(`/interventions/${interventionId}/part-requests`)
  return response.data
}

export async function searchPartRequests({ status, page = 0, size = 20 } = {}) {
  const response = await API_CONFIG.get('/part-requests', { params: clean({ status, page, size }) })
  return response.data
}

export async function listMine({ status, page = 0, size = 20 } = {}) {
  const response = await API_CONFIG.get('/part-requests/mine', { params: clean({ status, page, size }) })
  return response.data
}

export async function approve(id, { sparePartId } = {}) {
  const response = await API_CONFIG.patch(`/part-requests/${id}/approve`, { sparePartId: sparePartId || null })
  return response.data
}

export async function reject(id, reason) {
  const response = await API_CONFIG.patch(`/part-requests/${id}/reject`, { reason })
  return response.data
}

// supplierId vacío usa el proveedor preferido del repuesto
export async function order(id, { supplierId, estimatedCost } = {}) {
  const response = await API_CONFIG.patch(`/part-requests/${id}/order`, { supplierId: supplierId || null, estimatedCost: estimatedCost ?? null })
  return response.data
}

// Respuesta: { request, consumedInOrder, stockAfter }
export async function receive(id, { actualCost, sparePartId }) {
  const response = await API_CONFIG.patch(`/part-requests/${id}/receive`, { actualCost, sparePartId: sparePartId || null })
  return response.data
}
