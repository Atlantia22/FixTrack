import { useEffect, useLayoutEffect, useRef } from 'react'
import { subscribeRealtime } from '../config/realtimeClient'
import { isFrontDesk } from '../utils/permissions'
import { useAuth } from './useAuth'

// Escucha un topic mientras la vista está montada; onEvent recibe { type, entity, id, payload, timestamp }
// enabled evita suscribirse a topics que el rol no puede ver (el servidor rechazaría la suscripción)
export function useRealtime(destination, onEvent, { enabled = true, headers } = {}) {
  const handlerRef = useRef(onEvent)
  // Siempre la última versión del manejador, sin volver a suscribirse en cada render
  useLayoutEffect(() => {
    handlerRef.current = onEvent
  })
  const headersKey = JSON.stringify(headers ?? {})

  useEffect(() => {
    if (!enabled || !destination) return undefined
    return subscribeRealtime(destination, (event) => handlerRef.current(event), JSON.parse(headersKey))
  }, [destination, enabled, headersKey])
}

// Topics del servidor (mismos nombres que RealtimeTopics en el backend)
export const TOPICS = {
  SPARE_PARTS: '/topic/spare-parts',
  STOCK_ALERTS: '/topic/alerts/stock',
  INTERVENTIONS: '/topic/interventions',
  PART_REQUESTS: '/topic/part-requests',
  DEVICES: '/topic/devices',
  PERSONS: '/topic/persons',
  // Colas privadas: el técnico recibe aquí solo sus órdenes y sus solicitudes
  MY_INTERVENTIONS: '/user/queue/interventions',
  MY_PART_REQUESTS: '/user/queue/part-requests',
  order: (code) => `/topic/orders/${code}`,
}

// Recepción escucha todas las órdenes; el técnico solo las suyas, por su cola privada (el servidor rechaza lo demás)
export function useInterventionsTopic() {
  const { user } = useAuth()
  return isFrontDesk(user) ? TOPICS.INTERVENTIONS : TOPICS.MY_INTERVENTIONS
}

export function usePartRequestsTopic() {
  const { user } = useAuth()
  return isFrontDesk(user) ? TOPICS.PART_REQUESTS : TOPICS.MY_PART_REQUESTS
}
