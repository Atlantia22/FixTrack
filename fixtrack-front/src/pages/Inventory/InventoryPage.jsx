import { useState } from 'react'
import { MdAdd, MdAddBox, MdDeleteOutline, MdHistory, MdLink, MdOutlineEdit, MdTune } from 'react-icons/md'
import { useLocation, useSearchParams } from 'react-router'
import ConfirmDialog from '../../components/ConfirmDialog/ConfirmDialog'
import DataTable from '../../components/DataTable/DataTable'
import PageHeader from '../../components/PageHeader/PageHeader'
import Tabs from '../../components/Tabs/Tabs'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { TOPICS, useRealtime } from '../../hooks/useRealtime'
import { useRemoteData } from '../../hooks/useRemoteData'
import { useToast } from '../../hooks/useToast'
import * as catalogService from '../../services/catalogService'
import * as sparePartService from '../../services/sparePartService'
import { getErrorMessage } from '../../utils/errors'
import { formatCurrency } from '../../utils/format'
import { QUALITY_LABELS, STOCK_STATUS_LABELS } from '../../utils/labels'
import InventoryFilters from './InventoryFilters'
import MinStockModal from './MinStockModal'
import SparePartFormModal from './SparePartFormModal'
import SparePartLinksModal from './SparePartLinksModal'
import StockAlertsPanel from './StockAlertsPanel'
import StockEntryModal from './StockEntryModal'
import StockMovementsPanel from './StockMovementsPanel'
import './InventoryPage.css'

const TABS = [
  { id: 'parts', label: 'Repuestos' },
  { id: 'movements', label: 'Movimientos' },
]

const EMPTY_FILTERS = { search: '', partTypeId: '', brandId: '', deviceModelId: '', quality: '', stockStatus: '', alertsOnly: false, sort: 'name,asc' }

// ?alertas=1 llega desde la tarjeta de alertas del Inicio
function filtersFromParams(params) {
  return { ...EMPTY_FILTERS, alertsOnly: params.get('alertas') === '1' }
}

// Inventario: búsqueda, filtros, orden y paginación en el servidor; entradas, stock mínimo, edición y baja lógica
export default function InventoryPage() {
  const { showError, showSuccess } = useToast()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const [filters, setFilters] = useState(() => filtersFromParams(searchParams))
  const [appliedKey, setAppliedKey] = useState(location.key)
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState(null)
  const [entryPart, setEntryPart] = useState(null)
  const [minStockPart, setMinStockPart] = useState(null)
  const [toDeactivate, setToDeactivate] = useState(null)
  const [alertsKey, setAlertsKey] = useState(0)
  const [tab, setTab] = useState('parts')
  const [linksPart, setLinksPart] = useState(null)
  const [movementsPartId, setMovementsPartId] = useState('')
  const allParts = useRemoteData(() => sparePartService.searchSpareParts({ size: 500 }), [alertsKey])
  const debouncedSearch = useDebouncedValue(filters.search)

  // Cada navegación a la página (tarjeta del Inicio, menú) aplica los filtros de su URL; escribir no navega
  if (location.key !== appliedKey) {
    setAppliedKey(location.key)
    setFilters(filtersFromParams(searchParams))
    setPage(0)
  }

  const query = { ...filters, search: debouncedSearch, stockStatus: filters.alertsOnly ? '' : filters.stockStatus }
  const parts = useRemoteData(() => sparePartService.searchSpareParts({ ...query, page }), [page, ...Object.values(query)])
  // Un movimiento de stock solo cambia esa fila; altas, ediciones y bajas recargan el listado sin parpadeo
  useRealtime(TOPICS.SPARE_PARTS, ({ type, id, payload }) => {
    if (type === 'STOCK_CHANGED') {
      parts.mutate((current) => ({
        ...current,
        content: current.content.map((part) => (part.id === id
          ? { ...part, stock: payload.stock, minStock: payload.minStock, stockStatus: payload.stockStatus }
          : part)),
      }))
      if (filters.alertsOnly || filters.stockStatus) parts.reload({ silent: true })
      return
    }
    parts.reload({ silent: true })
    allParts.reload({ silent: true })
  })
  const partTypes = useRemoteData(catalogService.listPartTypes, [])
  const brands = useRemoteData(catalogService.listBrands, [])
  const models = useRemoteData(() => catalogService.listDeviceModels(), [])
  const catalogsReady = Boolean(partTypes.data && brands.data && models.data)

  function changeFilter(field, value) {
    setFilters((current) => ({ ...current, [field]: value, ...(field === 'brandId' ? { deviceModelId: '' } : {}) }))
    setPage(0)
  }

  function clearFilters() {
    setFilters(EMPTY_FILTERS)
    setPage(0)
  }

  // Cualquier cambio del stock o del mínimo recarga la tabla y las alertas
  function refresh() {
    parts.reload()
    setAlertsKey((current) => current + 1)
  }

  async function handleDeactivate() {
    try {
      await sparePartService.deactivateSparePart(toDeactivate.id)
      showSuccess(`Se dio de baja «${toDeactivate.name}». Sus movimientos se conservan.`)
      refresh()
    } catch (requestError) {
      showError(getErrorMessage(requestError))
    } finally {
      setToDeactivate(null)
    }
  }

  const columns = [
    { key: 'sku', header: 'SKU' },
    { key: 'name', header: 'Repuesto' },
    { key: 'partTypeName', header: 'Tipo' },
    { key: 'model', header: 'Modelo', render: (part) => `${part.brandName} ${part.deviceModelName}` },
    { key: 'quality', header: 'Calidad', render: (part) => (part.quality ? <span className="tag">{QUALITY_LABELS[part.quality]}</span> : '—') },
    {
      key: 'stock',
      header: 'Stock',
      align: 'right',
      render: (part) => (
        <span className="inventory-page__stock">
          {part.stock}
          <span className="inventory-page__min">mín. {part.minStock}</span>
          {part.stockStatus !== 'OK' && (
            <span className={`tag ${part.stockStatus === 'OUT' ? 'tag--danger' : 'tag--warning'}`}>{STOCK_STATUS_LABELS[part.stockStatus]}</span>
          )}
        </span>
      ),
    },
    { key: 'costPrice', header: 'Costo', align: 'right', render: (part) => formatCurrency(part.costPrice) },
    { key: 'salePrice', header: 'Precio de venta', align: 'right', render: (part) => formatCurrency(part.salePrice) },
    {
      key: 'actions',
      header: 'Acciones',
      align: 'right',
      render: (part) => (
        <span className="row-actions">
          <button type="button" className="action-btn action-btn--add" aria-label={`Registrar entrada de ${part.name}`} title="Registrar entrada" onClick={() => setEntryPart(part)}>
            <MdAddBox aria-hidden="true" />
          </button>
          <button type="button" className="action-btn action-btn--settings" aria-label={`Stock mínimo de ${part.name}`} title="Stock mínimo" onClick={() => setMinStockPart(part)}>
            <MdTune aria-hidden="true" />
          </button>
          <button type="button" className="action-btn action-btn--settings" aria-label={`Proveedores y modelos compatibles de ${part.name}`} title="Proveedores y compatibles" disabled={!catalogsReady} onClick={() => setLinksPart(part)}>
            <MdLink aria-hidden="true" />
          </button>
          <button type="button" className="action-btn action-btn--view" aria-label={`Movimientos de ${part.name}`} title="Movimientos" onClick={() => { setMovementsPartId(String(part.id)); setTab('movements') }}>
            <MdHistory aria-hidden="true" />
          </button>
          <button type="button" className="action-btn action-btn--edit" aria-label={`Editar ${part.name}`} title="Editar" disabled={!catalogsReady} onClick={() => setEditing(part)}>
            <MdOutlineEdit aria-hidden="true" />
          </button>
          <button type="button" className="action-btn action-btn--delete" aria-label={`Dar de baja ${part.name}`} title="Dar de baja" onClick={() => setToDeactivate(part)}>
            <MdDeleteOutline aria-hidden="true" />
          </button>
        </span>
      ),
    },
  ]

  return (
    <div className="page">
      <PageHeader
        title="Inventario"
        description="Repuestos activos, su stock y sus precios."
        actions={
          <button type="button" className="btn btn--primary" disabled={!catalogsReady} onClick={() => setEditing({})}>
            <MdAdd aria-hidden="true" />
            Nuevo repuesto
          </button>
        }
      />

      <Tabs tabs={TABS} activeTab={tab} onChange={(next) => { setTab(next); if (next === 'parts') setMovementsPartId('') }} label="Inventario">
      {tab === 'movements' ? (
        <StockMovementsPanel key={movementsPartId} parts={allParts.data?.content ?? []} initialPartId={movementsPartId} />
      ) : (
      <div className="inventory-page__parts">
      <StockAlertsPanel refreshKey={alertsKey} onEntry={setEntryPart} />

      <InventoryFilters
        value={filters}
        partTypes={partTypes.data ?? []}
        brands={brands.data ?? []}
        models={models.data ?? []}
        onChange={changeFilter}
        onClear={JSON.stringify(filters) !== JSON.stringify(EMPTY_FILTERS) ? clearFilters : null}
      />

      <DataTable
        caption="Repuestos"
        columns={columns}
        rows={parts.data?.content ?? []}
        loading={parts.loading}
        error={parts.error}
        onRetry={parts.reload}
        emptyMessage="No hay repuestos con esos filtros."
        page={parts.data?.page ?? 0}
        totalPages={parts.data?.totalPages ?? 1}
        totalElements={parts.data?.totalElements}
        onPageChange={setPage}
        rowClassName={(part) => (part.stockStatus === 'OUT' ? 'data-table__row--danger' : undefined)}
      />
      </div>
      )}
      </Tabs>

      {linksPart && models.data && (
        <SparePartLinksModal key={linksPart.id} part={linksPart} models={models.data} onClose={() => setLinksPart(null)} />
      )}

      {editing && catalogsReady && (
        <SparePartFormModal
          key={editing.id ?? 'new'}
          part={editing.id ? editing : null}
          partTypes={partTypes.data}
          brands={brands.data}
          models={models.data}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            refresh()
          }}
        />
      )}

      {entryPart && (
        <StockEntryModal
          key={entryPart.id}
          part={entryPart}
          onClose={() => setEntryPart(null)}
          onSaved={() => {
            setEntryPart(null)
            refresh()
          }}
        />
      )}

      {minStockPart && (
        <MinStockModal
          key={minStockPart.id}
          part={minStockPart}
          onClose={() => setMinStockPart(null)}
          onSaved={() => {
            setMinStockPart(null)
            refresh()
          }}
        />
      )}

      <ConfirmDialog
        open={toDeactivate !== null}
        title="Dar de baja repuesto"
        message={`«${toDeactivate?.name}» saldrá del inventario. Sus movimientos y su uso en órdenes se conservan.`}
        confirmLabel="Dar de baja"
        onConfirm={handleDeactivate}
        onCancel={() => setToDeactivate(null)}
      />
    </div>
  )
}
