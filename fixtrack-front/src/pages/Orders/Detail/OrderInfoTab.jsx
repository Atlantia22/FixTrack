import { useState } from 'react'
import PatternLock from '../../../components/PatternLock/PatternLock'
import { MdEditCalendar, MdVisibility, MdVisibilityOff } from 'react-icons/md'
import { useAuth } from '../../../hooks/useAuth'
import { useToast } from '../../../hooks/useToast'
import * as interventionService from '../../../services/interventionService'
import { getErrorMessage } from '../../../utils/errors'
import { displayOrNT, formatCurrency, formatDate } from '../../../utils/format'
import { AVAILABILITY_LABELS, DEVICE_TYPE_LABELS } from '../../../utils/labels'
import { isAssignedTechnician, isFrontDesk } from '../../../utils/permissions'
import { isSavedPattern, parsePattern } from '../../../utils/unlockPattern'
import OrderFrontDeskActions from './OrderFrontDeskActions'
import OrderRescheduleForm from './OrderRescheduleForm'
import './OrderInfoTab.css'

// Pestaña de información: cliente, equipo, recepción, técnico y fecha de entrega con su reprogramación
export default function OrderInfoTab({ order, technicians, onUpdated }) {
  const { user } = useAuth()
  const { showError, showSuccess } = useToast()
  const [showUnlock, setShowUnlock] = useState(false)
  const [rescheduling, setRescheduling] = useState(false)
  const [technicianId, setTechnicianId] = useState(order.technician ? String(order.technician.id) : '')
  const [assigning, setAssigning] = useState(false)

  const open = order.nextStatuses.length > 0
  const unlockIsPattern = isSavedPattern(order.unlockCode)
  const canReschedule = open && (isFrontDesk(user) || isAssignedTechnician(order, user))
  const canAssign = open && isFrontDesk(user)

  async function handleAssign() {
    setAssigning(true)
    try {
      const updated = await interventionService.assignTechnician(order.id, Number(technicianId))
      showSuccess(`Orden asignada a ${updated.technician.fullName}.`)
      onUpdated()
    } catch (requestError) {
      showError(getErrorMessage(requestError))
    } finally {
      setAssigning(false)
    }
  }

  return (
    <div className="order-info">
      <section className="order-info__section" aria-labelledby="order-info-delivery">
        <h3 id="order-info-delivery" className="order-info__title">Entrega</h3>
        <div className="order-info__delivery">
          <p>
            Fecha estimada: <strong>{formatDate(order.estimatedDeliveryDate)}</strong>
            {order.overdue && <span className="order-info__overdue">Atrasada</span>}
          </p>
          {canReschedule && !rescheduling && (
            <button type="button" className="btn btn--secondary" onClick={() => setRescheduling(true)}>
              <MdEditCalendar aria-hidden="true" />
              Reprogramar
            </button>
          )}
        </div>
        {rescheduling && (
          <OrderRescheduleForm
            order={order}
            onCancel={() => setRescheduling(false)}
            onDone={() => {
              setRescheduling(false)
              onUpdated()
            }}
          />
        )}
      </section>

      <section className="order-info__section" aria-labelledby="order-info-technician">
        <h3 id="order-info-technician" className="order-info__title">Técnico</h3>
        {canAssign ? (
          <div className="order-info__assign">
            <label className="visually-hidden" htmlFor="order-technician">Técnico asignado</label>
            <select id="order-technician" className="form-input" value={technicianId} onChange={(event) => setTechnicianId(event.target.value)}>
              <option value="">Sin asignar</option>
              {technicians.map((technician) => (
                <option key={technician.id} value={technician.id} disabled={technician.availability === 'ABSENT'}>
                  {technician.fullName} — {AVAILABILITY_LABELS[technician.availability] ?? 'Sin disponibilidad'}
                </option>
              ))}
            </select>
            <button
              type="button"
              className="btn btn--primary"
              disabled={assigning || !technicianId || technicianId === String(order.technician?.id ?? '')}
              onClick={handleAssign}
            >
              {assigning ? 'Asignando…' : 'Asignar'}
            </button>
          </div>
        ) : (
          <p>{order.technician?.fullName ?? 'Sin asignar'}</p>
        )}
      </section>

      <section className="order-info__section" aria-labelledby="order-info-customer">
        <h3 id="order-info-customer" className="order-info__title">Cliente</h3>
        <dl className="order-info__data">
          <div><dt>Nombre</dt><dd>{order.customer.fullName}</dd></div>
          <div><dt>Documento</dt><dd>{order.customer.documentType} {order.customer.documentNumber}</dd></div>
          <div><dt>Celular</dt><dd>{displayOrNT(order.customer.phone)}</dd></div>
          <div><dt>Correo</dt><dd>{displayOrNT(order.customer.email)}</dd></div>
        </dl>
      </section>

      <section className="order-info__section" aria-labelledby="order-info-device">
        <h3 id="order-info-device" className="order-info__title">Equipo</h3>
        <dl className="order-info__data">
          <div><dt>Equipo</dt><dd>{order.device.brandName} {order.device.modelName}</dd></div>
          <div><dt>Tipo</dt><dd>{DEVICE_TYPE_LABELS[order.device.deviceType]}</dd></div>
          <div><dt>IMEI</dt><dd>{order.device.imei ?? '—'}</dd></div>
          <div><dt>Número de serie</dt><dd>{order.device.serialNumber ?? '—'}</dd></div>
          <div><dt>Color</dt><dd>{order.device.color ?? '—'}</dd></div>
          {order.device.observations && <div><dt>Observaciones</dt><dd>{order.device.observations}</dd></div>}
        </dl>
      </section>

      <section className="order-info__section" aria-labelledby="order-info-reception">
        <h3 id="order-info-reception" className="order-info__title">Recepción</h3>
        <dl className="order-info__data order-info__data--wide">
          <div><dt>Problema reportado</dt><dd>{order.problemDescription}</dd></div>
          <div><dt>Estado físico</dt><dd>{order.physicalCondition}</dd></div>
          <div>
            <dt>Contraseña o patrón de desbloqueo</dt>
            <dd>
              {order.unlockCode ? (
                <span className="order-info__unlock">
                  {unlockIsPattern && <span>Patrón</span>}
                  <code>{showUnlock ? order.unlockCode : '•'.repeat(Math.min(order.unlockCode.length, 8))}</code>
                  <button
                    type="button"
                    className="btn btn--ghost btn--icon"
                    aria-label={showUnlock ? 'Ocultar contraseña de desbloqueo' : 'Mostrar contraseña de desbloqueo'}
                    onClick={() => setShowUnlock((current) => !current)}
                  >
                    {showUnlock ? <MdVisibilityOff aria-hidden="true" /> : <MdVisibility aria-hidden="true" />}
                  </button>
                </span>
              ) : 'No registrada o no disponible para usted'}
              {unlockIsPattern && showUnlock && (
                <div className="order-info__pattern">
                  <PatternLock points={parsePattern(order.unlockCode)} readOnly label="Patrón de desbloqueo del cliente" />
                </div>
              )}
            </dd>
          </div>
        </dl>
      </section>

      <section className="order-info__section" aria-labelledby="order-info-money">
        <h3 id="order-info-money" className="order-info__title">Valores</h3>
        <dl className="order-info__data">
          <div><dt>Presupuesto inicial</dt><dd>{formatCurrency(order.estimatedPrice)}</dd></div>
          <div><dt>{order.status === 'DELIVERED' ? 'Total cobrado' : 'Total actual'}</dt><dd>{formatCurrency(order.charges.total)}</dd></div>
          <div><dt>Abonado</dt><dd>{formatCurrency(order.charges.paid)}</dd></div>
          <div><dt>Saldo</dt><dd className={order.charges.balance > 0 ? 'order-info__due' : undefined}>{formatCurrency(order.charges.balance)}</dd></div>
          {order.charges.refundDue > 0 && <div><dt>Saldo a favor del cliente</dt><dd className="order-info__due">{formatCurrency(order.charges.refundDue)}</dd></div>}
          {order.warrantyUntil && <div><dt>Garantía</dt><dd>{order.warrantyDays} días, hasta {formatDate(order.warrantyUntil)}</dd></div>}
        </dl>
      </section>

      {isFrontDesk(user) && <OrderFrontDeskActions order={order} onUpdated={onUpdated} />}
    </div>
  )
}
