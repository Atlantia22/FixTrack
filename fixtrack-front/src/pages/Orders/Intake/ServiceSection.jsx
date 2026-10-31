import FormField from '../../../components/FormField/FormField'
import UnlockCodeField from '../../../components/UnlockCodeField/UnlockCodeField'
import { AVAILABILITY_LABELS } from '../../../utils/labels'
import DamagePicker from './DamagePicker'
import './ServiceSection.css'

// Sección del servicio: daños reportados, desbloqueo, problema, estado físico, técnico, presupuesto y entrega
export default function ServiceSection({ value, errors, technicians, minDate, onChange }) {
  return (
    <fieldset className="intake-section">
      <legend className="intake-section__title">3. Servicio</legend>
      <div className="form-grid">
        <FormField id="intake-service-types" label="Daños reportados" error={errors.serviceTypes} hint="Agregue todos los que reporte el cliente." required>
          {(props) => <DamagePicker {...props} value={value.serviceTypes} onChange={(serviceTypes) => onChange('serviceTypes', serviceTypes)} />}
        </FormField>
        <FormField id="intake-unlock" label="Contraseña o patrón de desbloqueo" hint="Opcional. Se guarda cifrada y se borra al entregar.">
          {(props) => <UnlockCodeField {...props} value={value.unlockCode} onChange={(unlockCode) => onChange('unlockCode', unlockCode)} />}
        </FormField>
        <FormField id="intake-problem" label="Descripción del problema" error={errors.problemDescription} required className="form-grid__full">
          {(props) => <textarea {...props} className="form-input" maxLength={1000} value={value.problemDescription} onChange={(event) => onChange('problemDescription', event.target.value)} />}
        </FormField>
        <FormField id="intake-physical" label="Estado físico al recibirlo" error={errors.physicalCondition} required className="form-grid__full" hint="Rayones, golpes, piezas faltantes…">
          {(props) => <textarea {...props} className="form-input" maxLength={1000} value={value.physicalCondition} onChange={(event) => onChange('physicalCondition', event.target.value)} />}
        </FormField>
        <FormField id="intake-technician" label="Técnico" hint="Opcional; se puede asignar después.">
          {(props) => (
            <select {...props} className="form-input" value={value.technicianId} onChange={(event) => onChange('technicianId', event.target.value)}>
              <option value="">Sin asignar</option>
              {technicians.map((technician) => (
                <option key={technician.id} value={technician.id} disabled={technician.availability === 'ABSENT'}>
                  {technician.fullName} — {AVAILABILITY_LABELS[technician.availability] ?? 'Sin disponibilidad'}
                </option>
              ))}
            </select>
          )}
        </FormField>
        <FormField id="intake-price" label="Presupuesto inicial" error={errors.estimatedPrice} required>
          {(props) => <input {...props} className="form-input" inputMode="decimal" value={value.estimatedPrice} onChange={(event) => onChange('estimatedPrice', event.target.value.replace(',', '.'))} />}
        </FormField>
        <FormField id="intake-delivery" label="Fecha estimada de entrega" error={errors.estimatedDeliveryDate} required>
          {(props) => <input {...props} type="date" className="form-input" min={minDate} value={value.estimatedDeliveryDate} onChange={(event) => onChange('estimatedDeliveryDate', event.target.value)} />}
        </FormField>
      </div>
    </fieldset>
  )
}
