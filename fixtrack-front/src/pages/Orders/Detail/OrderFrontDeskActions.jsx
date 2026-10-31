import { useId, useState } from 'react'
import { MdBlock, MdDownload, MdPrint } from 'react-icons/md'
import ConfirmDialog from '../../../components/ConfirmDialog/ConfirmDialog'
import FormField from '../../../components/FormField/FormField'
import { useToast } from '../../../hooks/useToast'
import * as interventionService from '../../../services/interventionService'
import { getErrorMessage } from '../../../utils/errors'
import { downloadFile, printPdf } from '../../../utils/files'
import { formatCurrency } from '../../../utils/format'
import { isOpen } from '../../../utils/permissions'
import './OrderFrontDeskActions.css'

// Acciones del asesor sobre la orden: reimprimir el ingreso con clave nueva, cancelar y descargar el comprobante de entrega
export default function OrderFrontDeskActions({ order, onUpdated }) {
  const reasonId = useId()
  const { showError, showSuccess } = useToast()
  const [confirmReprint, setConfirmReprint] = useState(false)
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [reason, setReason] = useState('')
  const [newKey, setNewKey] = useState(null)
  const [downloading, setDownloading] = useState(false)
  const [printingDelivery, setPrintingDelivery] = useState(false)

  // La clave nueva se genera, se imprime en el comprobante y se abre el diálogo de impresión
  async function handleReprint() {
    try {
      await printPdf(async () => {
        const { accessKey } = await interventionService.regenerateAccessKey(order.id)
        setNewKey(accessKey)
        return interventionService.getIntakeReceipt(order.id, accessKey)
      })
      showSuccess('Se generó una clave de consulta nueva; la anterior ya no sirve.')
    } catch (requestError) {
      showError(getErrorMessage(requestError))
    } finally {
      setConfirmReprint(false)
    }
  }

  async function handleCancel() {
    try {
      const updated = await interventionService.cancel(order.id, reason.trim())
      const refund = updated.charges.refundDue > 0 ? ` Abono por devolver: ${formatCurrency(updated.charges.refundDue)}.` : ''
      showSuccess(`La orden ${order.code} se canceló y sus repuestos volvieron al inventario.${refund}`)
      setConfirmCancel(false)
      setReason('')
      onUpdated()
    } catch (requestError) {
      showError(getErrorMessage(requestError))
    }
  }

  async function handleDownload() {
    setDownloading(true)
    try {
      downloadFile(await interventionService.getDeliveryReceipt(order.id), `entrega-${order.code}.pdf`)
    } catch (requestError) {
      showError(getErrorMessage(requestError))
    } finally {
      setDownloading(false)
    }
  }

  async function handlePrintDelivery() {
    setPrintingDelivery(true)
    try {
      await printPdf(() => interventionService.getDeliveryReceipt(order.id))
    } catch (requestError) {
      showError(getErrorMessage(requestError))
    } finally {
      setPrintingDelivery(false)
    }
  }

  return (
    <section className="order-actions" aria-labelledby="order-actions-title">
      <h3 id="order-actions-title" className="order-info__title">Comprobantes y cancelación</h3>
      <div className="order-actions__buttons">
        {order.status !== 'CANCELLED' && (
          <button type="button" className="btn btn--secondary" onClick={() => setConfirmReprint(true)}>
            <MdPrint aria-hidden="true" />
            Reimprimir comprobante de ingreso
          </button>
        )}
        {order.status === 'DELIVERED' && (
          <button type="button" className="btn btn--secondary" disabled={printingDelivery} onClick={handlePrintDelivery}>
            <MdPrint aria-hidden="true" />
            {printingDelivery ? 'Preparando…' : 'Imprimir comprobante de entrega'}
          </button>
        )}
        {order.status === 'DELIVERED' && (
          <button type="button" className="btn btn--secondary" disabled={downloading} onClick={handleDownload}>
            <MdDownload aria-hidden="true" />
            {downloading ? 'Descargando…' : 'Descargar comprobante de entrega'}
          </button>
        )}
        {isOpen(order) && (
          <button type="button" className="btn btn--danger" onClick={() => setConfirmCancel(true)}>
            <MdBlock aria-hidden="true" />
            Cancelar orden
          </button>
        )}
      </div>
      {newKey && (
        <p className="order-actions__key" role="status">
          Nueva clave de consulta: <strong>{newKey}</strong>
        </p>
      )}

      <ConfirmDialog
        open={confirmReprint}
        title="Reimprimir comprobante de ingreso"
        message={`Se generará una clave de consulta nueva para ${order.code} y la anterior dejará de servir en el portal. Entregue al cliente el comprobante nuevo.`}
        confirmLabel="Generar clave e imprimir"
        danger={false}
        onConfirm={handleReprint}
        onCancel={() => setConfirmReprint(false)}
      />
      <ConfirmDialog
        open={confirmCancel}
        title="Cancelar orden"
        message={`La orden ${order.code} se cancelará: los repuestos vuelven al inventario, se borra la contraseña de desbloqueo y el abono queda por devolver. No se puede deshacer.`}
        confirmLabel="Cancelar orden"
        cancelLabel="Volver"
        confirmDisabled={!reason.trim()}
        onConfirm={handleCancel}
        onCancel={() => setConfirmCancel(false)}
      >
        <FormField id={reasonId} label="Motivo de la cancelación" required>
          {(props) => <textarea {...props} className="form-input" maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} />}
        </FormField>
      </ConfirmDialog>
    </section>
  )
}
