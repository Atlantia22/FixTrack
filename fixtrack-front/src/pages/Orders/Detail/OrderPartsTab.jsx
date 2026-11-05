import { useState } from 'react'
import { MdAdd, MdDeleteOutline, MdOutlineEdit } from 'react-icons/md'
import ConfirmDialog from '../../../components/ConfirmDialog/ConfirmDialog'
import DataTable from '../../../components/DataTable/DataTable'
import FormField from '../../../components/FormField/FormField'
import { useAuth } from '../../../hooks/useAuth'
import { TOPICS, useInterventionsTopic, useRealtime } from '../../../hooks/useRealtime'
import { useRemoteData } from '../../../hooks/useRemoteData'
import { useToast } from '../../../hooks/useToast'
import * as catalogService from '../../../services/catalogService'
import * as interventionService from '../../../services/interventionService'
import { getErrorMessage } from '../../../utils/errors'
import { formatCurrency } from '../../../utils/format'
import { QUALITY_LABELS, ROLES } from '../../../utils/labels'
import { canEditUsedParts, canWorkOnRepair } from '../../../utils/permissions'
import SearchSelect from '../../../components/SearchSelect/SearchSelect'
import NewSparePartForm from './NewSparePartForm'
import OrderLaborForm from './OrderLaborForm'
import OrderPartRequests from './OrderPartRequests'
import UsageQuantityModal from './UsageQuantityModal'
import './OrderPartsTab.css'

// Valor de la opción «Otro» de la lista: despliega el formulario para agregar el repuesto al inventario
const OTHER_PART = 'other'

function candidateLabel(part) {
  const quality = part.quality ? ` · ${QUALITY_LABELS[part.quality]}` : ''
  const compatible = part.compatible ? ' · compatible' : ` · ${part.brandName} ${part.deviceModelName}`
  return `${part.name}${quality} · stock ${part.stock} · ${formatCurrency(part.salePrice)}${compatible}`
}

// Repuestos usados y mano de obra: el técnico asignado los registra, corrige o quita; el asesor solo ve el total
export default function OrderPartsTab({ order, onUpdated }) {
  const { user } = useAuth()
  const { showError, showSuccess } = useToast()
  const canEdit = canEditUsedParts(order, user)
  const showBreakdown = user.role !== ROLES.ADVISOR
  const [partTypeId, setPartTypeId] = useState('')
  const [deviceModelId, setDeviceModelId] = useState('')
  const [sparePartId, setSparePartId] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [saving, setSaving] = useState(false)
  const [toRemove, setToRemove] = useState(null)
  const [toCorrect, setToCorrect] = useState(null)
  // Cambia al crear un repuesto nuevo para que la lista de solicitudes se vuelva a cargar
  const [requestsVersion, setRequestsVersion] = useState(0)
  const addingNew = sparePartId === OTHER_PART

  const parts = useRemoteData(() => interventionService.listUsedParts(order.id), [order.id])
  useRealtime(useInterventionsTopic(), ({ id }) => {
    if (id === order.id) parts.reload({ silent: true })
  })
  const partTypes = useRemoteData(catalogService.listPartTypes, [])
  const models = useRemoteData(() => catalogService.listDeviceModels(), [])
  const candidates = useRemoteData(
    () => (canEdit ? interventionService.listPartCandidates(order.id, { partTypeId, deviceModelId }) : Promise.resolve([])),
    [order.id, partTypeId, deviceModelId, canEdit],
  )
  // Repuestos que sirven al equipo, con «Otro» al final para el que no está en el inventario
  const partOptions = [
    ...(candidates.data ?? []).map((part) => ({ value: part.id, label: candidateLabel(part) })),
    ...(candidates.loading ? [] : [{ value: OTHER_PART, label: 'Otro: no está en el inventario…' }]),
  ]
  // El stock de los candidatos cambia cuando otra orden usa o devuelve repuestos
  useRealtime(TOPICS.SPARE_PARTS, () => {
    if (canEdit) candidates.reload({ silent: true })
  })

  async function handleAdd(event) {
    event.preventDefault()
    if (!sparePartId || addingNew) {
      showError('Elija el repuesto.')
      return
    }
    if (!/^\d+$/.test(quantity) || Number(quantity) < 1) {
      showError('La cantidad debe ser un número entero mayor que cero.')
      return
    }
    setSaving(true)
    try {
      await interventionService.addUsedPart(order.id, { sparePartId: Number(sparePartId), quantity: Number(quantity) })
      showSuccess('Repuesto registrado y descontado del inventario.')
      setSparePartId('')
      setQuantity('1')
      parts.reload()
      candidates.reload()
      onUpdated()
    } catch (requestError) {
      // Si no alcanza el stock, el backend indica la cantidad disponible
      showError(getErrorMessage(requestError))
      candidates.reload()
    } finally {
      setSaving(false)
    }
  }

  async function handleRemove() {
    try {
      await interventionService.removeUsedPart(order.id, toRemove.id)
      showSuccess(`Se quitó «${toRemove.sparePartName}» y su stock volvió al inventario.`)
      parts.reload()
      candidates.reload()
      onUpdated()
    } catch (requestError) {
      showError(getErrorMessage(requestError))
    } finally {
      setToRemove(null)
    }
  }

  const columns = [
    {
      key: 'sparePartName',
      header: 'Repuesto',
      render: (usage) => (
        <span className="order-parts__name">
          {usage.sparePartName}
          <span className="order-parts__sku">{usage.sku}{usage.quality ? ` · ${QUALITY_LABELS[usage.quality]}` : ''}</span>
        </span>
      ),
    },
    { key: 'quantity', header: 'Cant.', align: 'right' },
    ...(showBreakdown
      ? [
        { key: 'unitPrice', header: 'Precio', align: 'right', render: (usage) => formatCurrency(usage.unitPrice) },
        { key: 'subtotal', header: 'Subtotal', align: 'right', render: (usage) => formatCurrency(usage.subtotal) },
      ]
      : []),
    ...(canEdit
      ? [{
        key: 'actions',
        header: 'Acciones',
        align: 'right',
        render: (usage) => (
          <span className="row-actions">
            <button type="button" className="action-btn action-btn--edit" aria-label={`Corregir cantidad de ${usage.sparePartName}`} title="Corregir cantidad" onClick={() => setToCorrect(usage)}>
              <MdOutlineEdit aria-hidden="true" />
            </button>
            <button type="button" className="action-btn action-btn--delete" aria-label={`Quitar ${usage.sparePartName}`} title="Quitar" onClick={() => setToRemove(usage)}>
              <MdDeleteOutline aria-hidden="true" />
            </button>
          </span>
        ),
      }]
      : []),
  ]

  return (
    <div className="order-parts">
      {canEdit && (
        <form className="order-parts__picker" onSubmit={handleAdd} noValidate>
          <h3 className="order-info__title">Registrar repuesto usado</h3>
          <div className="order-parts__filters">
            <FormField id="parts-type" label="Tipo">
              {(props) => (
                <SearchSelect
                  {...props}
                  options={(partTypes.data ?? []).map((type) => ({ value: type.id, label: type.name }))}
                  value={partTypeId}
                  placeholder="Todos"
                  emptyMessage="No hay tipos con ese nombre"
                  onChange={(value) => { setPartTypeId(value); setSparePartId('') }}
                />
              )}
            </FormField>
            <FormField id="parts-model" label="Modelo">
              {(props) => (
                <SearchSelect
                  {...props}
                  options={(models.data ?? []).map((model) => ({ value: model.id, label: `${model.brandName} ${model.name}` }))}
                  value={deviceModelId}
                  placeholder="Todos (compatibles primero)"
                  emptyMessage="No hay modelos con ese nombre"
                  onChange={(value) => { setDeviceModelId(value); setSparePartId('') }}
                />
              )}
            </FormField>
          </div>
          <FormField id="parts-part" label="Repuesto" hint={`Equipo de la orden: ${order.device.brandName} ${order.device.modelName}.`}>
            {(props) => (
              <SearchSelect
                {...props}
                options={partOptions}
                value={sparePartId}
                disabled={candidates.loading}
                placeholder={candidates.loading ? 'Cargando…' : 'Escriba el nombre, SKU o calidad'}
                emptyMessage="No hay repuestos con ese nombre; use «Agregar otro repuesto»"
                onChange={setSparePartId}
              />
            )}
          </FormField>
          {!addingNew && !candidates.loading && (
            <button type="button" className="btn btn--ghost order-parts__other" onClick={() => setSparePartId(OTHER_PART)}>
              <MdAdd aria-hidden="true" /> ¿No está? Agregar otro repuesto
            </button>
          )}
          {!addingNew && (
            <div className="order-parts__add">
              <FormField id="parts-quantity" label="Cantidad">
                {(props) => <input {...props} className="form-input" inputMode="numeric" value={quantity} onChange={(event) => setQuantity(event.target.value.replace(/\D/g, ''))} />}
              </FormField>
              <button type="submit" className="btn btn--primary" disabled={saving}>
                <MdAdd aria-hidden="true" />
                {saving ? 'Registrando…' : 'Agregar'}
              </button>
            </div>
          )}
        </form>
      )}
      {canEdit && addingNew && (
        <NewSparePartForm
          order={order}
          partTypes={partTypes.data ?? []}
          initialPartTypeId={partTypeId}
          onCancel={() => setSparePartId('')}
          onCreated={() => {
            setSparePartId('')
            setRequestsVersion((current) => current + 1)
            parts.reload()
            candidates.reload()
            onUpdated()
          }}
        />
      )}

      <section className="order-parts__used" aria-labelledby="order-parts-used">
        <h3 id="order-parts-used" className="order-info__title">Repuestos usados</h3>
        <DataTable
          caption="Repuestos usados"
          columns={columns}
          rows={parts.data?.items ?? []}
          loading={parts.loading && !parts.data}
          error={parts.error}
          onRetry={parts.reload}
          emptyMessage="La orden no tiene repuestos registrados."
        />
        {parts.data && (
          <dl className="order-parts__totals">
            {showBreakdown && (
              <>
                <div><dt>Repuestos (venta con instalación)</dt><dd>{formatCurrency(parts.data.partsTotal)}</dd></div>
                <div><dt>Mano de obra adicional</dt><dd>{formatCurrency(parts.data.laborCost)}</dd></div>
              </>
            )}
            <div className="order-parts__total"><dt>Total de la orden</dt><dd>{formatCurrency(parts.data.total)}</dd></div>
            {parts.data.partsCost != null && (
              <>
                <div><dt>Costo de repuestos</dt><dd>{formatCurrency(parts.data.partsCost)}</dd></div>
                {/* El precio de venta del repuesto ya trae la instalación: su ganancia es venta − costo */}
                <div><dt>Ganancia en repuestos e instalación</dt><dd>{formatCurrency(parts.data.partsTotal - parts.data.partsCost)}</dd></div>
                <div><dt>Ganancia por mano de obra adicional</dt><dd>{formatCurrency(parts.data.laborCost)}</dd></div>
                <div className={parts.data.profit < 0 ? 'order-parts__loss' : 'order-parts__profit'}>
                  <dt>Ganancia total</dt><dd>{formatCurrency(parts.data.profit)}</dd>
                </div>
              </>
            )}
          </dl>
        )}
        {canWorkOnRepair(order, user) && (
          <OrderLaborForm
            order={order}
            partsTotal={parts.data?.partsTotal ?? 0}
            onSaved={() => {
              parts.reload()
              onUpdated()
            }}
          />
        )}
      </section>

      <OrderPartRequests
        key={requestsVersion}
        order={order}
        canRequest={canWorkOnRepair(order, user)}
        onRequested={() => {
          parts.reload()
          onUpdated()
        }}
      />

      {toCorrect && (
        <UsageQuantityModal
          key={toCorrect.id}
          order={order}
          usage={toCorrect}
          onClose={() => setToCorrect(null)}
          onSaved={() => {
            setToCorrect(null)
            parts.reload()
            candidates.reload()
            onUpdated()
          }}
        />
      )}

      <ConfirmDialog
        open={toRemove !== null}
        title="Quitar repuesto"
        message={`Se quitará «${toRemove?.sparePartName}» (${toRemove?.quantity}) de la orden y su stock volverá al inventario.`}
        confirmLabel="Quitar repuesto"
        onConfirm={handleRemove}
        onCancel={() => setToRemove(null)}
      />
    </div>
  )
}
