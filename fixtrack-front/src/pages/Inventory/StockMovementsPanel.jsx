import { useState } from 'react'
import DataTable from '../../components/DataTable/DataTable'
import FormField from '../../components/FormField/FormField'
import { useRemoteData } from '../../hooks/useRemoteData'
import * as stockMovementService from '../../services/stockMovementService'
import { formatCurrency, formatDateTime } from '../../utils/format'
import { STOCK_MOVEMENT_LABELS, toOptions } from '../../utils/labels'
import SearchSelect from '../../components/SearchSelect/SearchSelect'
import './StockMovementsPanel.css'

const TONES = { IN_PURCHASE: 'tag--success', RETURN: 'tag--success', OUT_REPAIR: 'tag--warning', ADJUSTMENT: '' }

// Kárdex de solo lectura: fecha, tipo, cantidad, saldo resultante y responsable, con filtros
export default function StockMovementsPanel({ parts, initialPartId }) {
  const [filters, setFilters] = useState({ sparePartId: initialPartId ?? '', type: '', from: '', to: '' })
  const [page, setPage] = useState(0)
  const movements = useRemoteData(() => stockMovementService.searchMovements({ ...filters, page }), [page, ...Object.values(filters)])

  function change(field, value) {
    setFilters((current) => ({ ...current, [field]: value }))
    setPage(0)
  }

  const columns = [
    { key: 'createdAt', header: 'Fecha', render: (movement) => formatDateTime(movement.createdAt) },
    { key: 'part', header: 'Repuesto', render: (movement) => <span className="stock-movements__part">{movement.sparePartName}<span>{movement.sku}</span></span> },
    { key: 'type', header: 'Tipo', render: (movement) => <span className={`tag ${TONES[movement.type] ?? ''}`}>{STOCK_MOVEMENT_LABELS[movement.type]}</span> },
    { key: 'quantity', header: 'Cantidad', align: 'right', render: (movement) => (movement.quantity > 0 ? `+${movement.quantity}` : movement.quantity) },
    { key: 'balanceAfter', header: 'Saldo', align: 'right' },
    { key: 'unitCost', header: 'Costo unitario', align: 'right', render: (movement) => formatCurrency(movement.unitCost) },
    { key: 'reason', header: 'Motivo', render: (movement) => movement.reason ?? movement.interventionCode ?? '—' },
    { key: 'createdByName', header: 'Responsable' },
  ]

  return (
    <div className="stock-movements">
      <div className="filters">
        <FormField id="movements-part" label="Repuesto">
          {(props) => (
            <SearchSelect
              {...props}
              options={parts.map((part) => ({ value: part.id, label: `${part.name} · ${part.sku}` }))}
              value={filters.sparePartId}
              placeholder="Todos"
              emptyMessage="No hay repuestos con ese nombre o SKU"
              onChange={(value) => change('sparePartId', value)}
            />
          )}
        </FormField>
        <FormField id="movements-type" label="Tipo">
          {(props) => (
            <select {...props} className="form-input" value={filters.type} onChange={(event) => change('type', event.target.value)}>
              <option value="">Todos</option>
              {toOptions(STOCK_MOVEMENT_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
            </select>
          )}
        </FormField>
        <FormField id="movements-from" label="Desde">
          {(props) => <input {...props} type="date" className="form-input" max={filters.to || undefined} value={filters.from} onChange={(event) => change('from', event.target.value)} />}
        </FormField>
        <FormField id="movements-to" label="Hasta">
          {(props) => <input {...props} type="date" className="form-input" min={filters.from || undefined} value={filters.to} onChange={(event) => change('to', event.target.value)} />}
        </FormField>
      </div>
      <DataTable
        caption="Movimientos de inventario"
        columns={columns}
        rows={movements.data?.content ?? []}
        loading={movements.loading}
        error={movements.error}
        onRetry={movements.reload}
        emptyMessage="No hay movimientos con esos filtros."
        page={movements.data?.page ?? 0}
        totalPages={movements.data?.totalPages ?? 1}
        totalElements={movements.data?.totalElements}
        onPageChange={setPage}
      />
    </div>
  )
}
