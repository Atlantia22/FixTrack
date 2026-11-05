import { useState } from 'react'
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import ChartCard from '../../components/ChartCard/ChartCard'
import DataTable from '../../components/DataTable/DataTable'
import FormField from '../../components/FormField/FormField'
import StatCard from '../../components/StatCard/StatCard'
import { useRemoteData } from '../../hooks/useRemoteData'
import * as catalogService from '../../services/catalogService'
import * as reportService from '../../services/reportService'
import { formatCurrency } from '../../utils/format'
import { STOCK_STATUS_LABELS } from '../../utils/labels'
import { MdInventory, MdAttachMoney, MdWarningAmber, MdRemoveShoppingCart } from 'react-icons/md'
import SearchSelect from '../../components/SearchSelect/SearchSelect'
import './InventoryReportTab.css'

const STATUS_COLORS = { OK: 'var(--color-success)', LOW: 'var(--color-warning)', OUT: 'var(--color-danger)' }

// Existencias, valor a costo y alertas, con filtros; gráficos del estado de stock y del valor por tipo
export default function InventoryReportTab() {
  const [filters, setFilters] = useState({ partTypeId: '', brandId: '', alertsOnly: false })
  const report = useRemoteData(() => reportService.inventoryReport(filters), [filters.partTypeId, filters.brandId, filters.alertsOnly])
  const partTypes = useRemoteData(catalogService.listPartTypes, [])
  const brands = useRemoteData(catalogService.listBrands, [])
  const items = report.data?.items ?? []
  const totals = report.data?.totals

  const byStatus = ['OK', 'LOW', 'OUT']
    .map((status) => ({ status, name: STOCK_STATUS_LABELS[status], value: items.filter((item) => item.stockStatus === status).length }))
    .filter((entry) => entry.value > 0)
  const byType = Object.values(items.reduce((groups, item) => {
    const group = groups[item.partTypeName] ?? { name: item.partTypeName, value: 0 }
    group.value += Number(item.costValue)
    return { ...groups, [item.partTypeName]: group }
  }, {})).sort((a, b) => b.value - a.value)

  const columns = [
    { key: 'name', header: 'Repuesto', render: (item) => <span className="inventory-report__part">{item.name}<span>{item.sku}</span></span> },
    { key: 'partTypeName', header: 'Tipo' },
    { key: 'deviceModel', header: 'Modelo' },
    { key: 'stock', header: 'Stock', align: 'right', render: (item) => `${item.stock} (mín. ${item.minStock})` },
    { key: 'stockStatus', header: 'Estado', render: (item) => <span className={`tag ${item.stockStatus === 'OUT' ? 'tag--danger' : item.stockStatus === 'LOW' ? 'tag--warning' : 'tag--success'}`}>{STOCK_STATUS_LABELS[item.stockStatus]}</span> },
    { key: 'costPrice', header: 'Costo', align: 'right', render: (item) => formatCurrency(item.costPrice) },
    { key: 'costValue', header: 'Valor a costo', align: 'right', render: (item) => formatCurrency(item.costValue) },
  ]

  return (
    <div className="report-tab">
      <div className="filters">
        <FormField id="inventory-report-type" label="Tipo">
          {(props) => (
            <SearchSelect
              {...props}
              options={(partTypes.data ?? []).map((type) => ({ value: type.id, label: type.name }))}
              value={filters.partTypeId}
              placeholder="Todos"
              emptyMessage="No hay tipos con ese nombre"
              onChange={(value) => setFilters((current) => ({ ...current, partTypeId: value }))}
            />
          )}
        </FormField>
        <FormField id="inventory-report-brand" label="Marca">
          {(props) => (
            <SearchSelect
              {...props}
              options={(brands.data ?? []).map((brand) => ({ value: brand.id, label: brand.name }))}
              value={filters.brandId}
              placeholder="Todas"
              emptyMessage="No hay marcas con ese nombre"
              onChange={(value) => setFilters((current) => ({ ...current, brandId: value }))}
            />
          )}
        </FormField>
        <label className="inventory-report__check">
          <input type="checkbox" checked={filters.alertsOnly} onChange={(event) => setFilters((current) => ({ ...current, alertsOnly: event.target.checked }))} />
          Solo en alerta
        </label>
      </div>

      <section className="grid-cards" aria-label="Totales del inventario">
        <StatCard icon={MdInventory} title="Repuestos" value={totals?.parts ?? null} hint={totals ? `${totals.units} unidades` : undefined} />
        <StatCard icon={MdAttachMoney} title="Valor a costo" tone="success" value={totals ? formatCurrency(totals.costValue) : null} />
        <StatCard icon={MdWarningAmber} title="En alerta" tone="warning" value={totals?.inAlert ?? null} />
        <StatCard icon={MdRemoveShoppingCart} title="Agotados" tone="danger" value={totals?.outOfStock ?? null} />
      </section>

      {items.length > 0 && (
        <div className="report-tab__charts">
          <ChartCard title="Repuestos por estado de stock" summary={byStatus.map((entry) => `${entry.name}: ${entry.value}`).join(', ')}>
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={byStatus} dataKey="value" nameKey="name" innerRadius="55%" outerRadius="85%" paddingAngle={2} label={({ name, value }) => `${name}: ${value}`}>
                  {byStatus.map((entry) => <Cell key={entry.status} fill={STATUS_COLORS[entry.status]} />)}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </ChartCard>
          <ChartCard title="Valor a costo por tipo" summary={byType.map((entry) => `${entry.name}: ${formatCurrency(entry.value)}`).join(', ')}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={byType} margin={{ left: 24 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                <YAxis tickFormatter={(value) => formatCurrency(value)} tick={{ fontSize: 11 }} width={90} />
                <Tooltip formatter={(value) => formatCurrency(value)} />
                <Bar dataKey="value" name="Valor a costo" fill="var(--color-primary)" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </ChartCard>
        </div>
      )}

      <DataTable caption="Existencias por repuesto" columns={columns} rows={items} loading={report.loading} error={report.error} onRetry={report.reload} emptyMessage="No hay repuestos con esos filtros." />
    </div>
  )
}
