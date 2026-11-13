import axios from 'axios'
import { clearSession, getSession } from '../context/session'

export const UNAUTHORIZED_EVENT = 'fixtrack:unauthorized'

const baseURL = import.meta.env.VITE_API_URL


if (!baseURL) {
  console.error('Falta VITE_API_URL: copie .env.example como .env con la URL del backend')
}

// Cliente Axios central: URL del backend, credenciales de la sesión y cierre de sesión cuando dejan de servir
export const API_CONFIG = axios.create({
  baseURL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

// Lee el token JWT de la sesión en sessionStorage y lo agrega en Authorization, salvo en las públicas con skipAuth
API_CONFIG.interceptors.request.use((config) => {
  const authorization = getSession()?.authorization
  if (authorization && !config.skipAuth) {
    config.headers.Authorization = authorization
  }
  return config
})

// Un 401 con token significa sesión vencida o usuario desactivado: se borra y AuthProvider lleva al login
API_CONFIG.interceptors.response.use(
  (response) => response,
  (error) => {
    const sentToken = Boolean(error.config?.headers?.Authorization)
    if (error.response?.status === 401 && sentToken) {
      clearSession()
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    }
    return Promise.reject(error)
  },
)
