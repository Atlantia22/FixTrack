import { useState } from 'react'
import { MdCheckCircle, MdPictureAsPdf, MdWarningAmber } from 'react-icons/md'
import { useToast } from '../../../hooks/useToast'
import * as interventionService from '../../../services/interventionService'
import { getErrorMessage } from '../../../utils/errors'
import { openPdf } from '../../../utils/files'
import { formatCurrency, formatDate } from '../../../utils/format'
import { PAYMENT_METHOD_LABELS } from '../../../utils/labels'
import './IntakeResult.css'

// Datos para el comprobante: código y clave de consulta (la clave solo se muestra aquí, una vez) y abono; se imprime desde el pie del modal
export default function IntakeResult({ result }) {
  const { intervention, accessKey, receiptWarning, deposit } = result
  const { showError } = useToast()
  const [opening, setOpening] = useState(false)

  async function handleView() {
    setOpening(true)
    try {
      await openPdf(() => interventionService.getIntakeReceipt(intervention.id, accessKey))
    } catch (requestError) {
      showError(getErrorMessage(requestError))
    } finally {
      setOpening(false)
    }
  }

  return (
    <div className="intake-result">
      <p className="intake-result__title">
        <MdCheckCircle aria-hidden="true" />
        Orden registrada
      </p>
      <div className="intake-result__codes">
        <div>
          <span className="intake-result__label">Código de la orden</span>
          <span className="intake-result__value">{intervention.code}</span>
        </div>
        <div>
          <span className="intake-result__label">Clave de consulta</span>
          <span className="intake-result__value intake-result__value--key">{accessKey}</span>
        </div>
      </div>
      <div className="intake-result__print">
        <button type="button" className="btn btn--secondary" disabled={opening} onClick={handleView}>
          <MdPictureAsPdf aria-hidden="true" />
          {opening ? 'Abriendo…' : 'Ver PDF'}
        </button>
        <p className="intake-result__hint">
          Imprímalo con «Imprimir comprobante», abajo. Lleva el código y la clave. La clave no se vuelve a mostrar; si el cliente la pierde, reimprima el comprobante desde la orden y se generará una nueva.
        </p>
      </div>
      {receiptWarning && (
        <p className="intake-result__warning" role="alert">
          <MdWarningAmber aria-hidden="true" />
          {receiptWarning}
        </p>
      )}
      {deposit?.error && (
        <p className="intake-result__warning" role="alert">
          <MdWarningAmber aria-hidden="true" />
          La orden se creó, pero el abono no se registró: {deposit.error}
        </p>
      )}
      <dl className="intake-result__summary">
        <div><dt>Cliente</dt><dd>{intervention.customer.fullName}</dd></div>
        <div><dt>Equipo</dt><dd>{intervention.device.brandName} {intervention.device.modelName}</dd></div>
        <div><dt>Técnico</dt><dd>{intervention.technician?.fullName ?? 'Sin asignar'}</dd></div>
        <div><dt>Entrega estimada</dt><dd>{formatDate(intervention.estimatedDeliveryDate)}</dd></div>
        <div><dt>Presupuesto</dt><dd>{formatCurrency(intervention.estimatedPrice)}</dd></div>
        <div><dt>Abono</dt><dd>{deposit?.amount ? `${formatCurrency(deposit.amount)} (${PAYMENT_METHOD_LABELS[deposit.method]})` : 'Sin abono'}</dd></div>
      </dl>
    </div>
  )
}
