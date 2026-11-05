import { useState } from 'react'
import { MdAdd, MdDeleteOutline } from 'react-icons/md'
import { useToast } from '../../hooks/useToast'
import * as sparePartService from '../../services/sparePartService'
import { getErrorMessage } from '../../utils/errors'
import { formatCurrency } from '../../utils/format'
import SearchSelect from '../../components/SearchSelect/SearchSelect'
import './PartSuppliersEditor.css'

const EMPTY_ROW = { supplierId: '', supplierSku: '', lastCost: '', leadTimeDays: '', preferred: false }

function toRow(link) {
  return {
    supplierId: String(link.supplierId),
    supplierName: link.supplierName,
    supplierActive: link.supplierActive,
    supplierSku: link.supplierSku ?? '',
    lastCost: link.lastCost != null ? String(Number(link.lastCost)) : '',
    leadTimeDays: link.leadTimeDays != null ? String(link.leadTimeDays) : '',
    preferred: link.preferred,
  }
}

// Proveedores del repuesto: código del proveedor, último costo, días de entrega y cuál es el preferido
export default function PartSuppliersEditor({ part, links, suppliers, onSaved }) {
  const { showError, showSuccess } = useToast()
  const [rows, setRows] = useState(() => links.map(toRow))
  const [saving, setSaving] = useState(false)
  const usedIds = rows.map((row) => row.supplierId)

  function update(index, field, value) {
    setRows((current) => current.map((row, i) => {
      if (field === 'preferred') {
        return { ...row, preferred: i === index ? value : false }
      }
      return i === index ? { ...row, [field]: value } : row
    }))
  }

  async function handleSave() {
    if (rows.some((row) => !row.supplierId)) {
      showError('Elija el proveedor de cada fila o quítela.')
      return
    }
    setSaving(true)
    try {
      const saved = await sparePartService.replacePartSuppliers(part.id, rows.map((row) => ({
        supplierId: Number(row.supplierId),
        supplierSku: row.supplierSku.trim() || null,
        lastCost: row.lastCost === '' ? null : Number(row.lastCost),
        leadTimeDays: row.leadTimeDays === '' ? null : Number(row.leadTimeDays),
        preferred: row.preferred,
      })))
      showSuccess(`Se guardaron los proveedores de «${part.name}».`)
      onSaved(saved)
    } catch (requestError) {
      showError(getErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="part-suppliers">
      {rows.length === 0 && <p className="text-sm text-muted">El repuesto no tiene proveedores asociados.</p>}
      {rows.map((row, index) => (
        <fieldset key={`${row.supplierId}-${index}`} className="part-suppliers__row">
          <legend className="visually-hidden">Proveedor {index + 1}</legend>
          <label className="part-suppliers__field part-suppliers__field--wide">
            <span className="form-label">Proveedor</span>
            <SearchSelect
              id={`part-supplier-${index}`}
              options={[
                ...(row.supplierActive === false ? [{ value: row.supplierId, label: `${row.supplierName} (dado de baja)` }] : []),
                ...suppliers
                  .filter((supplier) => !usedIds.includes(String(supplier.id)) || String(supplier.id) === row.supplierId)
                  .map((supplier) => ({ value: supplier.id, label: supplier.name })),
              ]}
              value={row.supplierId}
              placeholder="Escriba el proveedor"
              emptyMessage="No hay proveedores con ese nombre"
              onChange={(value) => update(index, 'supplierId', value)}
            />
          </label>
          <label className="part-suppliers__field">
            <span className="form-label">Código</span>
            <input className="form-input" maxLength={50} value={row.supplierSku} onChange={(event) => update(index, 'supplierSku', event.target.value)} />
          </label>
          <label className="part-suppliers__field">
            <span className="form-label">Último costo</span>
            <input className="form-input" inputMode="decimal" value={row.lastCost} placeholder={formatCurrency(part.costPrice)} onChange={(event) => update(index, 'lastCost', event.target.value.replace(/[^\d.]/g, ''))} />
          </label>
          <label className="part-suppliers__field">
            <span className="form-label">Días de entrega</span>
            <input className="form-input" inputMode="numeric" value={row.leadTimeDays} onChange={(event) => update(index, 'leadTimeDays', event.target.value.replace(/\D/g, ''))} />
          </label>
          <label className="part-suppliers__preferred">
            <input type="radio" name={`preferred-${part.id}`} checked={row.preferred} onChange={() => update(index, 'preferred', true)} />
            Preferido
          </label>
          <button type="button" className="action-btn action-btn--delete" aria-label={`Quitar el proveedor ${index + 1}`} onClick={() => setRows((current) => current.filter((_, i) => i !== index))}>
            <MdDeleteOutline aria-hidden="true" />
          </button>
        </fieldset>
      ))}
      <div className="part-suppliers__actions">
        <button type="button" className="btn btn--secondary" onClick={() => setRows((current) => [...current, EMPTY_ROW])} disabled={rows.length >= suppliers.length + rows.filter((row) => row.supplierActive === false).length}>
          <MdAdd aria-hidden="true" />
          Agregar proveedor
        </button>
        <button type="button" className="btn btn--primary" disabled={saving} onClick={handleSave}>
          {saving ? 'Guardando…' : 'Guardar proveedores'}
        </button>
      </div>
    </div>
  )
}
