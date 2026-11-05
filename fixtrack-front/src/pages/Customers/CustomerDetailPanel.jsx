import { useState } from 'react'
import { MdDeleteOutline, MdOutlineEdit } from 'react-icons/md'
import { Link } from 'react-router'
import ConfirmDialog from '../../components/ConfirmDialog/ConfirmDialog'
import PersonFormModal from '../../components/PersonFormModal/PersonFormModal'
import SidePanel from '../../components/SidePanel/SidePanel'
import StatusBadge from '../../components/StatusBadge/StatusBadge'
import { useAuth } from '../../hooks/useAuth'
import { useRemoteData } from '../../hooks/useRemoteData'
import { useToast } from '../../hooks/useToast'
import * as deviceService from '../../services/deviceService'
import * as interventionService from '../../services/interventionService'
import * as personService from '../../services/personService'
import { getErrorMessage } from '../../utils/errors'
import { displayOrNT, formatDateTime, formatDelivery } from '../../utils/format'
import { DEVICE_TYPE_LABELS, DOCUMENT_TYPE_LABELS, ROLES } from '../../utils/labels'
import CustomerContactModal from './CustomerContactModal'
import './CustomerDetailPanel.css'

const ORDERS_SHOWN = 50

// Detalle del cliente en el panel lateral: contacto, equipos registrados y órdenes, de la más reciente
// La recepción corrige el contacto; el administrador edita todos los datos (formulario de personas) y da de baja
export default function CustomerDetailPanel({ customer, onClose, onUpdated, onDeactivated }) {
  const { user } = useAuth()
  const { showError, showSuccess } = useToast()
  const isAdmin = user.role === ROLES.ADMIN
  const [editingContact, setEditingContact] = useState(false)
  const [editingData, setEditingData] = useState(false)
  const [confirmingDeactivate, setConfirmingDeactivate] = useState(false)
  const devices = useRemoteData(() => deviceService.listDevicesByOwner(customer.id), [customer.id])
  const orders = useRemoteData(
    () => interventionService.searchInterventions({ customerId: customer.id, size: ORDERS_SHOWN }),
    [customer.id],
  )

  async function handleDeactivate() {
    try {
      await personService.deactivatePerson(customer.id)
      showSuccess(`Se dio de baja a ${customer.fullName}.`)
      setConfirmingDeactivate(false)
      onDeactivated()
    } catch (requestError) {
      showError(getErrorMessage(requestError))
      setConfirmingDeactivate(false)
    }
  }

  return (
    <SidePanel
      open
      title={customer.fullName}
      subtitle={`${DOCUMENT_TYPE_LABELS[customer.documentType]} ${customer.documentNumber}`}
      onClose={onClose}
      footer={isAdmin && customer.active && (
        <button type="button" className="btn btn--danger" onClick={() => setConfirmingDeactivate(true)}>
          <MdDeleteOutline aria-hidden="true" />
          Dar de baja
        </button>
      )}
    >
      <div className="customer-detail">
        <section className="customer-detail__section" aria-labelledby="customer-contact-title">
          <div className="customer-detail__heading">
            <h3 id="customer-contact-title" className="customer-detail__title">Contacto</h3>
            {customer.active ? (
              <button type="button" className="btn btn--secondary" onClick={() => (isAdmin ? setEditingData(true) : setEditingContact(true))}>
                <MdOutlineEdit aria-hidden="true" />
                {isAdmin ? 'Editar datos' : 'Editar contacto'}
              </button>
            ) : <span className="tag">Dado de baja</span>}
          </div>
          <dl className="customer-detail__data">
            <div><dt>Celular</dt><dd>{displayOrNT(customer.phone)}</dd></div>
            <div><dt>Correo</dt><dd>{displayOrNT(customer.email)}</dd></div>
            <div><dt>Cliente desde</dt><dd>{formatDateTime(customer.createdAt)}</dd></div>
          </dl>
          {!customer.phone && !customer.email && (
            <p className="customer-detail__warning">Sin celular ni correo: no podremos contactarlo.</p>
          )}
        </section>

        <section className="customer-detail__section" aria-labelledby="customer-devices-title">
          <h3 id="customer-devices-title" className="customer-detail__title">
            Equipos{devices.data && ` (${devices.data.length})`}
          </h3>
          {renderList(devices, 'No tiene equipos registrados.', (device) => (
              <li key={device.id} className="customer-detail__item">
                <strong>{device.brandName} {device.modelName}</strong>
                <span className="customer-detail__meta">
                  {DEVICE_TYPE_LABELS[device.deviceType]}
                  {device.imei && ` · IMEI ${device.imei}`}
                  {device.serialNumber && ` · Serie ${device.serialNumber}`}
                  {device.color && ` · ${device.color}`}
                </span>
                {device.observations && <span className="customer-detail__meta">{device.observations}</span>}
              </li>
          ))}
        </section>

        <section className="customer-detail__section" aria-labelledby="customer-orders-title">
          <h3 id="customer-orders-title" className="customer-detail__title">
            Órdenes{orders.data && ` (${orders.data.totalElements})`}
          </h3>
          {renderList({ ...orders, data: orders.data?.content }, 'No tiene órdenes.', (order) => (
              <li key={order.id} className="customer-detail__item customer-detail__order">
                <span className="customer-detail__order-head">
                  <Link to={`/ordenes?code=${order.code}`} className="customer-detail__code">{order.code}</Link>
                  <StatusBadge status={order.status} />
                </span>
                <span className="customer-detail__meta">
                  {order.device} · Ingreso {formatDateTime(order.receivedAt)} · Entrega estimada {formatDelivery(order.estimatedDeliveryDate, order.estimatedDeliveryTime)}
                </span>
              </li>
          ))}
          {orders.data?.totalElements > ORDERS_SHOWN && (
            <p className="text-sm text-muted">Se muestran las {ORDERS_SHOWN} más recientes.</p>
          )}
        </section>
      </div>

      {editingData && (
        <PersonFormModal
          person={customer}
          fixedRole={ROLES.CUSTOMER}
          onClose={() => setEditingData(false)}
          onSaved={(updated) => {
            setEditingData(false)
            onUpdated(updated)
          }}
        />
      )}

      <ConfirmDialog
        open={confirmingDeactivate}
        title="Dar de baja al cliente"
        message={`${customer.fullName} saldrá del listado de clientes activos y no se le podrán recibir equipos. Sus órdenes y equipos se conservan.`}
        confirmLabel="Dar de baja"
        onConfirm={handleDeactivate}
        onCancel={() => setConfirmingDeactivate(false)}
      />

      {editingContact && (
        <CustomerContactModal
          customer={customer}
          onClose={() => setEditingContact(false)}
          onSaved={(updated) => {
            setEditingContact(false)
            onUpdated(updated)
          }}
        />
      )}
    </SidePanel>
  )
}

// Lista con sus estados de carga, error con reintento y vacío
function renderList(state, emptyMessage, renderItem) {
  if (state.loading) {
    return <p className="text-sm text-muted">Cargando…</p>
  }
  if (state.error) {
    return (
      <p className="text-sm customer-detail__error">
        {state.error}{' '}
        <button type="button" className="btn btn--ghost" onClick={state.reload}>Reintentar</button>
      </p>
    )
  }
  if (!state.data?.length) {
    return <p className="text-sm text-muted">{emptyMessage}</p>
  }
  return <ul className="customer-detail__list">{state.data.map(renderItem)}</ul>
}
