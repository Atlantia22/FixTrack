import { useState } from 'react'
import { MdArrowBack } from 'react-icons/md'
import { Link } from 'react-router'
import PublicLayout from '../../components/PublicLayout/PublicLayout'
import { TOPICS, useRealtime } from '../../hooks/useRealtime'
import { useToast } from '../../hooks/useToast'
import * as portalService from '../../services/portalService'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import PortalOrderResult from './PortalOrderResult'
import './PortalPage.css'

const CODE_PATTERN = /^FT-\d{5,}(-G\d+)?$/
const KEY_PATTERN = /^\d{4}$/
const EMPTY_FORM = { code: '', accessKey: '' }

function validate({ code, accessKey }) {
  const errors = {}
  if (!CODE_PATTERN.test(code)) {
    errors.code = 'Escriba el código como aparece en su comprobante, por ejemplo FT-00001.'
  }
  if (!KEY_PATTERN.test(accessKey)) {
    errors.accessKey = 'La clave tiene 4 dígitos.'
  }
  return errors
}

// Portal público: el cliente consulta su orden con el código y la clave de 4 dígitos del comprobante
export default function PortalPage() {
  const { showError, showInfo } = useToast()
  const [form, setForm] = useState(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [order, setOrder] = useState(null)
  // Mensaje visible junto al formulario cuando el código y la clave no coinciden (o hay demasiados intentos)
  const [lookupError, setLookupError] = useState(null)
  // Código y clave de la consulta vigente: con ellos se sigue la orden en tiempo real
  const [access, setAccess] = useState(null)

  // Cuando el taller cambia el estado, la orden se vuelve a consultar con la misma clave; el aviso no trae datos
  useRealtime(access ? TOPICS.order(order?.code) : null, async () => {
    try {
      setOrder(await portalService.lookupOrder(access))
      showInfo('Su orden se actualizó.')
    } catch {
      // Si la consulta falla, se queda la información que ya tenía
    }
  }, { enabled: Boolean(access && order), headers: { accessKey: access?.accessKey } })

  function handleChange(event) {
    const { name, value } = event.target
    const normalized = name === 'code' ? value.toUpperCase().trim() : value.replace(/\D/g, '')
    setForm((current) => ({ ...current, [name]: normalized }))
    setLookupError(null)
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const errors = validate(form)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      return
    }

    setSubmitting(true)
    setLookupError(null)
    try {
      setOrder(await portalService.lookupOrder(form))
      setAccess({ ...form })
    } catch (error) {
      setFieldErrors(getFieldErrors(error))
      if (error.response?.status === 404 || error.response?.status === 429) {
        setLookupError(getErrorMessage(error))
      } else {
        showError(getErrorMessage(error, 'No pudimos consultar su orden. Intente de nuevo.'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  function handleNewLookup() {
    setOrder(null)
    setAccess(null)
    setForm(EMPTY_FORM)
    setFieldErrors({})
  }

  const footer = (
    <span>
      ¿Trabaja en el taller? <Link to="/login">Inicie sesión</Link>
    </span>
  )

  if (order) {
    return (
      <PublicLayout title={`Orden ${order.code}`} description="Estado actual de su equipo." footer={footer}>
        <PortalOrderResult order={order} accessKey={form.accessKey} />
        <button type="button" className="btn btn--secondary btn--block" onClick={handleNewLookup}>
          <MdArrowBack aria-hidden="true" />
          Nueva consulta
        </button>
      </PublicLayout>
    )
  }

  return (
    <PublicLayout
      title="Consulta tu equipo"
      description="Ingrese el código de la orden y la clave de 4 dígitos que aparecen en su comprobante de ingreso."
      footer={footer}
    >
      <form className="portal-form" onSubmit={handleSubmit} noValidate>
        <div className="form-field">
          <label className="form-label" htmlFor="portal-code">Código de la orden</label>
          <input
            id="portal-code"
            name="code"
            className="form-input"
            placeholder="FT-00001"
            autoComplete="off"
            autoCapitalize="characters"
            value={form.code}
            onChange={handleChange}
            aria-invalid={Boolean(fieldErrors.code)}
            aria-describedby={fieldErrors.code ? 'portal-code-error' : undefined}
          />
          {fieldErrors.code && <span id="portal-code-error" className="form-error">{fieldErrors.code}</span>}
        </div>

        <div className="form-field">
          <label className="form-label" htmlFor="portal-key">Clave de consulta</label>
          <input
            id="portal-key"
            name="accessKey"
            type="password"
            className="form-input"
            inputMode="numeric"
            maxLength={4}
            autoComplete="off"
            value={form.accessKey}
            onChange={handleChange}
            aria-invalid={Boolean(fieldErrors.accessKey)}
            aria-describedby={fieldErrors.accessKey ? 'portal-key-error' : 'portal-key-hint'}
          />
          {fieldErrors.accessKey ? (
            <span id="portal-key-error" className="form-error">{fieldErrors.accessKey}</span>
          ) : (
            <span id="portal-key-hint" className="form-hint">
              Son los últimos 4 dígitos de su celular o la clave impresa en el comprobante.
            </span>
          )}
        </div>

        {lookupError && (
          <p className="portal-page__error" role="alert">{lookupError}</p>
        )}

        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          {submitting ? 'Consultando…' : 'Consultar'}
        </button>
      </form>
    </PublicLayout>
  )
}
