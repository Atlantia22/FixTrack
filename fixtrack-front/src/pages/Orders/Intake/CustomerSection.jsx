import { MdPersonAdd, MdPersonSearch, MdSearch } from 'react-icons/md'
import FormField from '../../../components/FormField/FormField'
import { cleanDocumentNumber, documentNumberHint, DOCUMENT_RULES } from '../../../utils/document'
import { DOCUMENT_TYPE_LABELS, toOptions } from '../../../utils/labels'
import ContactField from '../../../components/ContactField/ContactField'
import './CustomerSection.css'

// Sección del cliente: se busca por documento; si existe se reutiliza, si no se registra con su nombre
export default function CustomerSection({ value, errors, lookup, onChange, onLookup }) {
  const found = lookup.person
  const showContact = lookup.done

  function handleDocumentKeyDown(event) {
    if (event.key === 'Enter') {
      event.preventDefault()
      onLookup()
    }
  }

  return (
    <fieldset className="intake-section">
      <legend className="intake-section__title">1. Cliente</legend>

      <div className="customer-section__lookup">
        <FormField id="intake-doc-type" label="Tipo de documento" required>
          {(props) => (
            <select {...props} className="form-input" value={value.documentType} onChange={(event) => onChange('documentType', event.target.value)}>
              {toOptions(DOCUMENT_TYPE_LABELS).map(({ value: option, label }) => <option key={option} value={option}>{label}</option>)}
            </select>
          )}
        </FormField>
        <FormField id="intake-doc-number" label="Número de documento" error={errors.documentNumber} hint={documentNumberHint(value.documentType)} required>
          {(props) => (
            <input
              {...props}
              className="form-input"
              inputMode={DOCUMENT_RULES[value.documentType].numeric ? 'numeric' : 'text'}
              maxLength={DOCUMENT_RULES[value.documentType].max}
              autoComplete="off"
              value={value.documentNumber}
              onChange={(event) => onChange('documentNumber', cleanDocumentNumber(value.documentType, event.target.value))}
              onKeyDown={handleDocumentKeyDown}
            />
          )}
        </FormField>
        <button type="button" className="btn btn--secondary customer-section__search" onClick={onLookup} disabled={lookup.loading}>
          <MdSearch aria-hidden="true" />
          {lookup.loading ? 'Buscando…' : 'Buscar'}
        </button>
      </div>

      {showContact && (
        <p className={`customer-section__status ${found ? 'customer-section__status--found' : ''}`} role="status">
          {found ? <MdPersonSearch aria-hidden="true" /> : <MdPersonAdd aria-hidden="true" />}
          {found ? `Cliente registrado: ${found.fullName}. Puede actualizar su celular y correo.` : 'No está registrado: complete sus datos para crearlo.'}
        </p>
      )}

      {showContact && (
        <div className="form-grid">
          {!found && (
            <FormField id="intake-full-name" label="Nombre completo" error={errors.fullName} required className="form-grid__full">
              {(props) => <input {...props} className="form-input" maxLength={150} value={value.fullName} onChange={(event) => onChange('fullName', event.target.value)} />}
            </FormField>
          )}
          <ContactField
            id="intake-phone"
            label="Celular"
            error={errors.phone}
            value={value.phone}
            noValue={value.noPhone}
            noValueLabel="No tiene celular"
            inputProps={{ inputMode: 'numeric', maxLength: 10 }}
            onValueChange={(text) => onChange('phone', text.replace(/\D/g, ''))}
            onNoValueChange={(checked) => onChange('noPhone', checked)}
          />
          <ContactField
            id="intake-email"
            label="Correo"
            error={errors.email}
            value={value.email}
            noValue={value.noEmail}
            noValueLabel="No tiene correo"
            inputProps={{ type: 'email', maxLength: 150 }}
            onValueChange={(text) => onChange('email', text)}
            onNoValueChange={(checked) => onChange('noEmail', checked)}
          />
        </div>
      )}
    </fieldset>
  )
}
