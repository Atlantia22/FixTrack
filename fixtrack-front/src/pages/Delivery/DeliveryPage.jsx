import { useId, useState } from 'react'
import { MdOutlineVisibility, MdPayments, MdTaskAlt, MdUndo } from 'react-icons/md'
import ConfirmDialog from '../../components/ConfirmDialog/ConfirmDialog'
import DataTable from '../../components/DataTable/DataTable'
import FormField from '../../components/FormField/FormField'
import PageHeader from '../../components/PageHeader/PageHeader'
import { TOPICS, useRealtime } from '../../hooks/useRealtime'
import { useRemoteData } from '../../hooks/useRemoteData'
import { useToast } from '../../hooks/useToast'
import * as interventionService from '../../services/interventionService'
import { getErrorMessage } from '../../utils/errors'
import { formatCurrency } from '../../utils/format'
import OrderDetailPanel from '../Orders/Detail/OrderDetailPanel'
import DeliveredList from './DeliveredList'
import PaymentModal from './PaymentModal'
import UnclaimedList from './UnclaimedList'
import './DeliveryPage.css'

// Salida: órdenes listas con total, abonado y saldo; cobro o devolución, entrega con saldo 0 y comprobante de entrega
export default function DeliveryPage() {
  const notesId = useId()
  const { showError, showSuccess } = useToast()
  const ready = useRemoteData(interventionService.listReadyForDelivery, [])
  const unclaimed = useRemoteData(interventionService.listUnclaimed, [])
  const delivered = useRemoteData(interventionService.listDeliveredToday, [])
  // Una orden que pasa a lista, recibe un pago o se entrega en otra pantalla se refleja aquí al instante
  useRealtime(TOPICS.INTERVENTIONS, () => {
    ready.reload({ silent: true })
    unclaimed.reload({ silent: true })
    delivered.reload({ silent: true })
  })
  const unclaimedById = new Map((unclaimed.data ?? []).map((order) => [order.id, order]))
  const [payment, setPayment] = useState(null)
  const [toDeliver, setToDeliver] = useState(null)
  const [notes, setNotes] = useState('')
  const [selectedId, setSelectedId] = useState(null)

  async function handleDeliver() {
    const { order } = toDeliver
    try {
      await interventionService.deliver(order.id, { notes: notes.trim() || null })
      showSuccess(`La orden ${order.code} se entregó. Descargue el comprobante de entrega para el cliente.`)
      setToDeliver(null)
      setNotes('')
      ready.reload()
      unclaimed.reload()
      delivered.reload()
    } catch (requestError) {
      showError(getErrorMessage(requestError))
    }
  }

  const columns = [
    {
      key: 'code',
      header: 'Orden',
      render: (row) => (
        <span className="delivery-page__order">
          <strong>{row.order.code}</strong>
          <span>{row.order.customerName}</span>
          {unclaimedById.has(row.order.id) && (
            <span className="tag tag--warning">Sin reclamar · {unclaimedById.get(row.order.id).daysWaiting} días</span>
          )}
        </span>
      ),
    },
    { key: 'device', header: 'Equipo', render: (row) => row.order.device },
    { key: 'total', header: 'Total', align: 'right', render: (row) => formatCurrency(row.total) },
    { key: 'paid', header: 'Abonado', align: 'right', render: (row) => formatCurrency(row.paid) },
    {
      key: 'balance',
      header: 'Saldo',
      align: 'right',
      render: (row) => (
        <span className={row.balance > 0 ? 'delivery-page__due' : 'delivery-page__settled'}>
          {formatCurrency(row.balance)}
          {row.refundDue > 0 && <span className="delivery-page__refund">A favor: {formatCurrency(row.refundDue)}</span>}
        </span>
      ),
    },
    {
      key: 'actions',
      header: 'Acciones',
      align: 'right',
      render: (row) => (
        <span className="row-actions delivery-page__actions">
          {row.balance > 0 && (
            <button type="button" className="btn btn--secondary" onClick={() => setPayment({ row, type: 'FINAL' })}>
              <MdPayments aria-hidden="true" />
              Cobrar
            </button>
          )}
          {row.refundDue > 0 && (
            <button type="button" className="btn btn--secondary" onClick={() => setPayment({ row, type: 'REFUND' })}>
              <MdUndo aria-hidden="true" />
              Devolver
            </button>
          )}
          <button
            type="button"
            className="btn btn--primary"
            disabled={row.balance > 0}
            title={row.balance > 0 ? 'Cobre el saldo antes de entregar' : undefined}
            onClick={() => setToDeliver(row)}
          >
            <MdTaskAlt aria-hidden="true" />
            Entregar
          </button>
          <button type="button" className="action-btn action-btn--view" aria-label={`Ver la orden ${row.order.code}`} onClick={() => setSelectedId(row.order.id)}>
            <MdOutlineVisibility aria-hidden="true" />
          </button>
        </span>
      ),
    },
  ]

  return (
    <div className="page">
      <PageHeader title="Salida" description="Órdenes listas para entregar: cobre el saldo, entregue el equipo y descargue el comprobante." />

      <DataTable
        caption="Órdenes listas para entregar"
        columns={columns}
        rows={(ready.data ?? []).map((row) => ({ ...row, id: row.order.id }))}
        rowClassName={(row) => (unclaimedById.has(row.order.id) ? 'data-table__row--warning' : undefined)}
        loading={ready.loading}
        error={ready.error}
        onRetry={ready.reload}
        emptyMessage="No hay órdenes listas para entregar."
      />
      <p className="form-hint">«Entregar» se habilita cuando el saldo es 0.</p>

      {unclaimed.data?.length > 0 && <UnclaimedList orders={unclaimed.data} onOpen={setSelectedId} />}

      <DeliveredList state={delivered} />

      {payment && (
        <PaymentModal
          key={`${payment.row.order.id}-${payment.type}`}
          row={payment.row}
          type={payment.type}
          onClose={() => setPayment(null)}
          onSaved={() => {
            setPayment(null)
            ready.reload()
          }}
        />
      )}

      <ConfirmDialog
        open={toDeliver !== null}
        title="Entregar equipo"
        message={`Se entregará la orden ${toDeliver?.order.code} a ${toDeliver?.order.customerName}: se guarda el total cobrado, se borra la contraseña de desbloqueo y empieza la garantía. No se puede deshacer.`}
        confirmLabel="Entregar"
        danger={false}
        onConfirm={handleDeliver}
        onCancel={() => setToDeliver(null)}
      >
        <FormField id={notesId} label="Notas de la entrega" hint="Opcional; quedan en el historial.">
          {(props) => <textarea {...props} className="form-input" maxLength={1000} value={notes} onChange={(event) => setNotes(event.target.value)} />}
        </FormField>
      </ConfirmDialog>

      {selectedId && (
        <OrderDetailPanel
          key={selectedId}
          orderId={selectedId}
          technicians={[]}
          onClose={() => setSelectedId(null)}
          onOpenOrder={setSelectedId}
          onChanged={ready.reload}
        />
      )}
    </div>
  )
}
