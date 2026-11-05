import { MdAddBox, MdRefresh, MdWarningAmber } from 'react-icons/md'
import { TOPICS, useRealtime } from '../../hooks/useRealtime'
import { useRemoteData } from '../../hooks/useRemoteData'
import * as sparePartService from '../../services/sparePartService'
import { STOCK_STATUS_LABELS } from '../../utils/labels'
import './StockAlertsPanel.css'

// Alertas de stock bajo: se recargan tras cada cambio del inventario (refreshKey) y cuando el servidor avisa
// que un repuesto bajó del mínimo, se agotó o volvió a tener stock (antes se consultaba cada minuto)
export default function StockAlertsPanel({ refreshKey, onEntry }) {
  const alerts = useRemoteData(sparePartService.listLowStock, [refreshKey])
  const { reload } = alerts
  useRealtime(TOPICS.STOCK_ALERTS, () => reload({ silent: true }))

  const parts = alerts.data ?? []
  return (
    <section className="stock-alerts" aria-labelledby="stock-alerts-title" aria-live="polite">
      <header className="stock-alerts__header">
        <h2 id="stock-alerts-title" className="stock-alerts__title">
          <MdWarningAmber aria-hidden="true" />
          Alertas de stock {alerts.data && <span className="stock-alerts__count">{parts.length}</span>}
        </h2>
        <button type="button" className="btn btn--ghost btn--icon" aria-label="Actualizar alertas" onClick={() => reload()}>
          <MdRefresh aria-hidden="true" />
        </button>
      </header>
      {alerts.error && <p className="form-error">{alerts.error}</p>}
      {alerts.data && parts.length === 0 && <p className="text-sm text-muted">Ningún repuesto está en el mínimo ni agotado.</p>}
      {parts.length > 0 && (
        <ul className="stock-alerts__list">
          {parts.map((part) => (
            <li key={part.id} className="stock-alerts__item">
              <span className="stock-alerts__name">
                {part.name}
                <span className="stock-alerts__meta">{part.sku} · {part.stock} de mín. {part.minStock}</span>
              </span>
              <span className={`tag ${part.stockStatus === 'OUT' ? 'tag--danger' : 'tag--warning'}`}>{STOCK_STATUS_LABELS[part.stockStatus]}</span>
              <button type="button" className="action-btn action-btn--add" aria-label={`Registrar entrada de ${part.name}`} onClick={() => onEntry(part)}>
                <MdAddBox aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
