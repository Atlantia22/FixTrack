import { useState } from 'react'
import SidePanel from '../../../components/SidePanel/SidePanel'
import StatusBadge from '../../../components/StatusBadge/StatusBadge'
import { TOPICS, useRealtime } from '../../../hooks/useRealtime'
import { useRemoteData } from '../../../hooks/useRemoteData'
import * as interventionService from '../../../services/interventionService'
import OrderDiagnosisTab from './OrderDiagnosisTab'
import OrderFindingsTab from './OrderFindingsTab'
import OrderHistoryTab from './OrderHistoryTab'
import OrderInfoTab from './OrderInfoTab'
import OrderPartsTab from './OrderPartsTab'
import OrderWarrantiesTab from './OrderWarrantiesTab'
import './OrderDetailPanel.css'

const TABS = [
  { id: 'info', label: 'Información' },
  { id: 'diagnosis', label: 'Diagnóstico y resultado' },
  { id: 'findings', label: 'Daños' },
  { id: 'parts', label: 'Repuestos y mano de obra' },
  { id: 'history', label: 'Historial' },
]
const WARRANTIES_TAB = { id: 'warranties', label: 'Garantías' }

// Detalle de la orden en un panel lateral, con el listado visible detrás
// onOpenOrder abre otra orden en el panel (por ejemplo, una garantía desde su orden original)
export default function OrderDetailPanel({ orderId, technicians, onClose, onChanged, onOpenOrder }) {
  const [activeTab, setActiveTab] = useState('info')
  const detail = useRemoteData(() => interventionService.getIntervention(orderId), [orderId])
  const order = detail.data

  // Si otra persona cambia esta orden (estado, técnico, repuestos, pagos), el detalle se actualiza sin cerrarse
  useRealtime(TOPICS.INTERVENTIONS, ({ id }) => {
    if (id === orderId) detail.reload({ silent: true })
  })

  // Tras cualquier cambio se recarga el detalle y el listado de fondo
  function handleUpdated() {
    detail.reload()
    onChanged()
  }

  const subtitle = order && (
    <>
      <StatusBadge status={order.status} />
      {order.overdue && <span className="order-detail__overdue">Atrasada</span>}
      <span>{order.device.brandName} {order.device.modelName} · {order.customer.fullName}</span>
    </>
  )

  return (
    <SidePanel
      open
      title={order ? `${order.warrantyOfCode ? 'Garantía' : 'Orden'} ${order.code}` : 'Orden'}
      subtitle={subtitle}
      onClose={onClose}
      tabs={order && !order.warrantyOfCode ? [...TABS.slice(0, 4), WARRANTIES_TAB, TABS[4]] : TABS}
      activeTab={activeTab}
      onTabChange={setActiveTab}
    >
      {!order && detail.loading && <p className="text-muted">Cargando…</p>}
      {detail.error && !order && <p className="form-error">{detail.error}</p>}
      {order && activeTab === 'info' && (
        <OrderInfoTab key={`${order.id}-${order.estimatedDeliveryDate}-${order.estimatedDeliveryTime}-${order.technician?.id}`} order={order} technicians={technicians} onUpdated={handleUpdated} />
      )}
      {order && activeTab === 'diagnosis' && (
        <OrderDiagnosisTab key={`${order.id}-${order.status}-${order.repairOutcome}`} order={order} onUpdated={handleUpdated} />
      )}
      {order && activeTab === 'findings' && <OrderFindingsTab order={order} onUpdated={handleUpdated} />}
      {order && activeTab === 'parts' && <OrderPartsTab key={`${order.id}-${order.laborCost}`} order={order} onUpdated={handleUpdated} />}
      {order && activeTab === 'history' && <OrderHistoryTab order={order} />}
      {order && activeTab === 'warranties' && (
        <OrderWarrantiesTab order={order} technicians={technicians} onOpenOrder={onOpenOrder} onUpdated={handleUpdated} />
      )}
    </SidePanel>
  )
}
