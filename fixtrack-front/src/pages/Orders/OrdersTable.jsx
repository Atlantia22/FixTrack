import DataTable from '../../components/DataTable/DataTable'
import StatusBadge from '../../components/StatusBadge/StatusBadge'
import TimeRemainingTag from '../../components/TimeRemainingTag/TimeRemainingTag'
import { useNow } from '../../hooks/useNow'
import { formatDateTime, formatDelivery } from '../../utils/format'
import { isOverdue } from '../../utils/timeRemaining'
import './OrdersTable.css'

function columns(now) {
  return [
    { key: 'code', header: 'Orden', render: (order) => <span className="orders-table__code">{order.code}</span> },
    {
      key: 'customer',
      header: 'Cliente',
      render: (order) => (
        <span className="orders-table__customer">
          {order.customerName}
          <span className="orders-table__document">{order.customerDocument}</span>
        </span>
      ),
    },
    { key: 'device', header: 'Equipo' },
    { key: 'technicianName', header: 'Técnico', render: (order) => order.technicianName ?? 'Sin asignar' },
    { key: 'status', header: 'Estado', render: (order) => <StatusBadge status={order.status} /> },
    {
      key: 'estimatedDeliveryDate',
      header: 'Entrega estimada',
      render: (order) => (
        <span className="orders-table__delivery">
          {formatDelivery(order.estimatedDeliveryDate, order.estimatedDeliveryTime)}
          {isOverdue(order, now) && <span className="orders-table__overdue">Atrasada</span>}
        </span>
      ),
    },
    {
      key: 'timeRemaining',
      header: 'Tiempo restante',
      render: (order) => <TimeRemainingTag order={order} now={now} />,
    },
    { key: 'receivedAt', header: 'Ingreso', render: (order) => formatDateTime(order.receivedAt) },
  ]
}

// Tabla de órdenes: el tiempo restante y «Atrasada» se recalculan cada minuto con un solo reloj para toda la tabla
export default function OrdersTable({ page, loading, error, onRetry, onPageChange, onSelect, emptyMessage, caption = 'Órdenes' }) {
  const now = useNow()
  return (
    <DataTable
      caption={caption}
      columns={columns(now)}
      rows={page?.content ?? []}
      loading={loading}
      error={error}
      onRetry={onRetry}
      emptyMessage={emptyMessage}
      page={page?.page ?? 0}
      totalPages={page?.totalPages ?? 1}
      totalElements={page?.totalElements}
      onPageChange={onPageChange}
      onRowClick={onSelect ? (order) => onSelect(order.id) : undefined}
      rowClassName={(order) => (isOverdue(order, now) ? 'data-table__row--danger' : undefined)}
    />
  )
}
