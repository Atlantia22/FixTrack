import { ROLES } from '../utils/labels'
import { useAuth } from './useAuth'
import { TOPICS, useRealtime } from './useRealtime'
import { useToast } from './useToast'

const STOCK_ROLES = [ROLES.COORDINATOR, ROLES.ADMIN]

// Aviso emergente al coordinador y al administrador, en cualquier pantalla, cuando un repuesto baja del mínimo o se agota
export function useStockAlertToasts() {
  const { user } = useAuth()
  const { showError, showInfo } = useToast()

  useRealtime(TOPICS.STOCK_ALERTS, ({ type, payload }) => {
    if (type === 'OUT_OF_STOCK') {
      showError(`Se agotó ${payload.name} (${payload.sku}).`)
    } else if (type === 'LOW_STOCK') {
      showInfo(`Stock bajo: ${payload.name} (${payload.sku}) quedó en ${payload.stock}; el mínimo es ${payload.minStock}.`)
    }
  }, { enabled: STOCK_ROLES.includes(user?.role) })
}
