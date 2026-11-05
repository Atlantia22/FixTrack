import { useState } from 'react'
import { MdAdd, MdDeleteOutline, MdOutlineEdit } from 'react-icons/md'
import ConfirmDialog from '../../components/ConfirmDialog/ConfirmDialog'
import DataTable from '../../components/DataTable/DataTable'
import FormField from '../../components/FormField/FormField'
import { useRemoteData } from '../../hooks/useRemoteData'
import { useToast } from '../../hooks/useToast'
import * as catalogService from '../../services/catalogService'
import { getErrorMessage } from '../../utils/errors'
import { DEVICE_TYPE_LABELS, toOptions } from '../../utils/labels'
import SearchSelect from '../../components/SearchSelect/SearchSelect'
import DeviceModelFormModal from './DeviceModelFormModal'
import './DeviceModelsTab.css'

// Modelos de equipo con su marca y tipo, filtrables por ambos
export default function DeviceModelsTab() {
  const { showError, showSuccess } = useToast()
  const [brandId, setBrandId] = useState('')
  const [deviceType, setDeviceType] = useState('')
  const [editing, setEditing] = useState(null)
  const [toDelete, setToDelete] = useState(null)

  const brands = useRemoteData(catalogService.listBrands, [])
  const models = useRemoteData(() => catalogService.listDeviceModels({ brandId, deviceType }), [brandId, deviceType])

  async function handleDelete() {
    try {
      await catalogService.deleteDeviceModel(toDelete.id)
      showSuccess(`Se eliminó el modelo «${toDelete.brandName} ${toDelete.name}».`)
      models.reload()
    } catch (requestError) {
      showError(getErrorMessage(requestError))
    } finally {
      setToDelete(null)
    }
  }

  const columns = [
    { key: 'brandName', header: 'Marca' },
    { key: 'name', header: 'Modelo' },
    { key: 'deviceType', header: 'Tipo de equipo', render: (model) => DEVICE_TYPE_LABELS[model.deviceType] },
    {
      key: 'actions',
      header: 'Acciones',
      align: 'right',
      render: (model) => (
        <span className="row-actions">
          <button type="button" className="action-btn action-btn--edit" aria-label={`Editar ${model.brandName} ${model.name}`} onClick={() => setEditing(model)}>
            <MdOutlineEdit aria-hidden="true" />
          </button>
          <button type="button" className="action-btn action-btn--delete" aria-label={`Eliminar ${model.brandName} ${model.name}`} onClick={() => setToDelete(model)}>
            <MdDeleteOutline aria-hidden="true" />
          </button>
        </span>
      ),
    },
  ]

  return (
    <section className="device-models-tab" aria-label="Modelos">
      <div className="device-models-tab__toolbar">
        <div className="filters">
          <FormField id="models-brand" label="Marca">
            {(props) => (
              <SearchSelect
                {...props}
                options={(brands.data ?? []).map((brand) => ({ value: brand.id, label: brand.name }))}
                value={brandId}
                placeholder="Todas"
                emptyMessage="No hay marcas con ese nombre"
                onChange={setBrandId}
              />
            )}
          </FormField>
          <FormField id="models-type" label="Tipo de equipo">
            {(props) => (
              <select {...props} className="form-input" value={deviceType} onChange={(event) => setDeviceType(event.target.value)}>
                <option value="">Todos</option>
                {toOptions(DEVICE_TYPE_LABELS).map(({ value, label }) => <option key={value} value={value}>{label}</option>)}
              </select>
            )}
          </FormField>
        </div>
        <button type="button" className="btn btn--primary" onClick={() => setEditing({})} disabled={!brands.data?.length}>
          <MdAdd aria-hidden="true" />
          Nuevo modelo
        </button>
      </div>

      <DataTable
        caption="Modelos de equipo"
        columns={columns}
        rows={models.data ?? []}
        loading={models.loading}
        error={models.error}
        onRetry={models.reload}
        emptyMessage="No hay modelos con esos filtros."
      />

      {editing && (
        <DeviceModelFormModal
          key={editing.id ?? 'new'}
          model={editing.id ? editing : null}
          brands={brands.data ?? []}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            models.reload()
          }}
        />
      )}

      <ConfirmDialog
        open={toDelete !== null}
        title="Eliminar modelo"
        message={`Se eliminará «${toDelete?.brandName} ${toDelete?.name}». Si tiene equipos o repuestos asociados no se podrá eliminar.`}
        confirmLabel="Eliminar"
        onConfirm={handleDelete}
        onCancel={() => setToDelete(null)}
      />
    </section>
  )
}
