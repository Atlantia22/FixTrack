import FormField from '../../components/FormField/FormField'
import { QUALITY_LABELS, STOCK_STATUS_LABELS, toOptions } from '../../utils/labels'
import SearchSelect from '../../components/SearchSelect/SearchSelect'
import './InventoryFilters.css'

const SORT_OPTIONS = [
  { value: 'name,asc', label: 'Nombre (A-Z)' },
  { value: 'name,desc', label: 'Nombre (Z-A)' },
  { value: 'stock,asc', label: 'Stock (menor primero)' },
  { value: 'stock,desc', label: 'Stock (mayor primero)' },
  { value: 'salePrice,asc', label: 'Precio de venta (menor primero)' },
  { value: 'salePrice,desc', label: 'Precio de venta (mayor primero)' },
  { value: 'brand,asc', label: 'Marca' },
  { value: 'partType,asc', label: 'Tipo' },
  { value: 'sku,asc', label: 'SKU' },
]

// Búsqueda, filtros combinados y orden del inventario; los modelos se limitan a la marca elegida
export default function InventoryFilters({ value, partTypes, brands, models, onChange, onClear }) {
  const brandModels = value.brandId ? models.filter((model) => String(model.brandId) === value.brandId) : models
  return (
    <div className="filters inventory-filters">
      <FormField id="inventory-search" label="Buscar">
        {(props) => <input {...props} type="search" className="form-input" placeholder="Nombre, descripción o SKU" value={value.search} onChange={(event) => onChange('search', event.target.value)} />}
      </FormField>
      <FormField id="inventory-type" label="Tipo">
        {(props) => (
          <SearchSelect
            {...props}
            options={partTypes.map((type) => ({ value: type.id, label: type.name }))}
            value={value.partTypeId}
            placeholder="Todos"
            emptyMessage="No hay tipos con ese nombre"
            onChange={(selected) => onChange('partTypeId', selected)}
          />
        )}
      </FormField>
      <FormField id="inventory-brand" label="Marca">
        {(props) => (
          <SearchSelect
            {...props}
            options={brands.map((brand) => ({ value: brand.id, label: brand.name }))}
            value={value.brandId}
            placeholder="Todas"
            emptyMessage="No hay marcas con ese nombre"
            onChange={(selected) => onChange('brandId', selected)}
          />
        )}
      </FormField>
      <FormField id="inventory-model" label="Modelo">
        {(props) => (
          <SearchSelect
            {...props}
            options={brandModels.map((model) => ({ value: model.id, label: `${model.brandName} ${model.name}` }))}
            value={value.deviceModelId}
            placeholder="Todos"
            emptyMessage="No hay modelos con ese nombre"
            onChange={(selected) => onChange('deviceModelId', selected)}
          />
        )}
      </FormField>
      <FormField id="inventory-quality" label="Calidad">
        {(props) => (
          <select {...props} className="form-input" value={value.quality} onChange={(event) => onChange('quality', event.target.value)}>
            <option value="">Todas</option>
            {toOptions(QUALITY_LABELS).map(({ value: option, label }) => <option key={option} value={option}>{label}</option>)}
          </select>
        )}
      </FormField>
      <FormField id="inventory-stock" label="Stock">
        {(props) => (
          <select {...props} className="form-input" value={value.stockStatus} disabled={value.alertsOnly} onChange={(event) => onChange('stockStatus', event.target.value)}>
            <option value="">Todos</option>
            {toOptions(STOCK_STATUS_LABELS).map(({ value: option, label }) => <option key={option} value={option}>{label}</option>)}
          </select>
        )}
      </FormField>
      <FormField id="inventory-sort" label="Ordenar por">
        {(props) => (
          <select {...props} className="form-input" value={value.sort} onChange={(event) => onChange('sort', event.target.value)}>
            {SORT_OPTIONS.map(({ value: option, label }) => <option key={option} value={option}>{label}</option>)}
          </select>
        )}
      </FormField>
      <label className="inventory-filters__check">
        <input type="checkbox" checked={value.alertsOnly} onChange={(event) => onChange('alertsOnly', event.target.checked)} />
        Solo en alerta (bajos o agotados)
      </label>
      {onClear && (
        <button type="button" className="btn btn--ghost" onClick={onClear}>Limpiar filtros</button>
      )}
    </div>
  )
}
