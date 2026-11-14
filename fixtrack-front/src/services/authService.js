import { API_CONFIG } from '../config/apiConfig'

// Respuesta: { token, expiresAt, user: { id, fullName, email, role } }
export async function login(credentials) {
  const response = await API_CONFIG.post('/api/auth/login', credentials, { skipAuth: true })
  return response.data
}

// Cambia la contraseña propia; el backend exige la actual, rechaza que la nueva sea igual y devuelve un token nuevo
export async function changePassword({ currentPassword, newPassword }) {
  const response = await API_CONFIG.patch('api/auth/password', { currentPassword, newPassword })
  return response.data
}
