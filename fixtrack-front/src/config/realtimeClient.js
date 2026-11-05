import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

// El canal WebSocket vive junto a la API: http://localhost:8080/api → http://localhost:8080/ws
const WS_URL = import.meta.env.VITE_WS_URL ?? `${import.meta.env.VITE_API_URL.replace(/\/api\/?$/, '')}/ws`
const RECONNECT_DELAY_MS = 5000

let client = null
let currentAuthorization = null
let nextId = 0
// Suscripciones activas de las vistas; sobreviven a la reconexión y se vuelven a pedir al conectar
const subscriptions = new Map()

function subscribeOnServer(entry) {
  entry.stompSubscription = client.subscribe(entry.destination, (frame) => {
    try {
      entry.handler(JSON.parse(frame.body))
    } catch (error) {
      console.warn('Evento en tiempo real no válido', error)
    }
  }, entry.headers)
}

// Un solo cliente STOMP (SockJS de respaldo) para toda la app; con authorization es la sesión del usuario interno,
// sin ella es anónimo y solo sirve para el canal de la orden en el portal
export function connectRealtime(authorization = null) {
  if (client && currentAuthorization === authorization) return
  disconnectRealtime()
  currentAuthorization = authorization
  client = new Client({
    webSocketFactory: () => new SockJS(WS_URL),
    connectHeaders: authorization ? { Authorization: authorization } : {},
    reconnectDelay: RECONNECT_DELAY_MS,
    // Al conectar o reconectar se vuelven a pedir todas las suscripciones de las vistas abiertas
    onConnect: () => subscriptions.forEach(subscribeOnServer),
    onStompError: (frame) => console.warn('El servidor rechazó la conexión en tiempo real:', frame.headers.message),
  })
  client.activate()
}

// Al cerrar sesión se corta la conexión; las vistas que sigan montadas se desuscriben solas al desmontarse
export function disconnectRealtime() {
  if (client) {
    client.deactivate()
    client = null
  }
  currentAuthorization = null
  subscriptions.forEach((entry) => {
    entry.stompSubscription = null
  })
}

// Escucha un topic y devuelve la función para dejar de escucharlo; si no hay conexión, abre una anónima
export function subscribeRealtime(destination, handler, headers = {}) {
  if (!client) connectRealtime(null)
  const id = nextId++
  const entry = { destination, handler, headers, stompSubscription: null }
  subscriptions.set(id, entry)
  if (client.connected) subscribeOnServer(entry)
  return () => {
    subscriptions.delete(id)
    if (entry.stompSubscription && client?.connected) entry.stompSubscription.unsubscribe()
  }
}
