import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import FormField from '../../components/FormField/FormField'
import PageHeader from '../../components/PageHeader/PageHeader'
import PasswordInput from '../../components/PasswordInput/PasswordInput'
import { useAuth } from '../../hooks/useAuth'
import { useToast } from '../../hooks/useToast'
import { getErrorMessage, getFieldErrors } from '../../utils/errors'
import './ChangePasswordPage.css'

const EMPTY = { currentPassword: '', newPassword: '', confirmation: '' }

function validate(form) {
  const errors = {}
  if (!form.currentPassword) errors.currentPassword = 'Escriba su contraseña actual.'
  if (form.newPassword.length < 8) errors.newPassword = 'Debe tener al menos 8 caracteres.'
  else if (form.newPassword.length > 72) errors.newPassword = 'Debe tener máximo 72 caracteres.'
  else if (form.newPassword === form.currentPassword) errors.newPassword = 'Debe ser diferente de la actual.'
  if (form.confirmation !== form.newPassword) errors.confirmation = 'No coincide con la nueva contraseña.'
  return errors
}

// El backend responde 400 con el motivo; se muestra junto al campo que corresponde
function backendErrors(error) {
  const fields = getFieldErrors(error)
  const detail = error.response?.data?.detail ?? ''
  if (/actual/i.test(detail) && /correcta/i.test(detail)) return { ...fields, currentPassword: detail }
  if (/diferente/i.test(detail)) return { ...fields, newPassword: detail }
  return fields
}

// Cambio de la contraseña propia: exige la actual y que la nueva sea distinta
export default function ChangePasswordPage() {
  const navigate = useNavigate()
  const { showError, showSuccess } = useToast()
  const { changePassword } = useAuth()
  const [form, setForm] = useState(EMPTY)
  const [errors, setErrors] = useState({})
  const [saving, setSaving] = useState(false)

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const validation = validate(form)
    setErrors(validation)
    if (Object.keys(validation).length > 0) {
      return
    }
    setSaving(true)
    try {
      await changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword })
      showSuccess('Su contraseña se cambió. Las sesiones abiertas en otros equipos se cerraron.')
      navigate('/perfil')
    } catch (requestError) {
      setErrors(backendErrors(requestError))
      showError(getErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="page">
      <PageHeader title="Cambiar contraseña" description="Por seguridad se pide su contraseña actual." />
      <form className="change-password" onSubmit={handleSubmit} noValidate>
        <FormField id="password-current" label="Contraseña actual" error={errors.currentPassword} required>
          {(props) => <PasswordInput {...props} autoComplete="current-password" value={form.currentPassword} onChange={(event) => update('currentPassword', event.target.value)} />}
        </FormField>
        <FormField id="password-new" label="Nueva contraseña" error={errors.newPassword} hint="Entre 8 y 72 caracteres, distinta de la actual." required>
          {(props) => <PasswordInput {...props} autoComplete="new-password" maxLength={72} value={form.newPassword} onChange={(event) => update('newPassword', event.target.value)} />}
        </FormField>
        <FormField id="password-confirmation" label="Confirme la nueva contraseña" error={errors.confirmation} required>
          {(props) => <PasswordInput {...props} autoComplete="new-password" maxLength={72} value={form.confirmation} onChange={(event) => update('confirmation', event.target.value)} />}
        </FormField>
        <div className="change-password__actions">
          <Link to="/perfil" className="btn btn--secondary">Volver</Link>
          <button type="submit" className="btn btn--primary" disabled={saving}>
            {saving ? 'Guardando…' : 'Cambiar contraseña'}
          </button>
        </div>
      </form>
    </div>
  )
}
