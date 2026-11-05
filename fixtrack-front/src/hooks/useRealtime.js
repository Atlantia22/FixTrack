import { useEffect, useLayoutEffect, useRef } from 'react'
import { subscribeRealtime } from '../config/realtimeClient'

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
  order: (code) => `/topic/orders/${code}`,
}
