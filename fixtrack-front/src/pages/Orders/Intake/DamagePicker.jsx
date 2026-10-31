import { MdClose } from 'react-icons/md'
import SearchSelect from '../../../components/SearchSelect/SearchSelect'
import { SERVICE_TYPE_LABELS, toOptions } from '../../../utils/labels'
import './DamagePicker.css'

const DAMAGE_OPTIONS = toOptions(SERVICE_TYPE_LABELS)

// Daños que reporta el cliente al recibir: se buscan escribiendo y se van agregando como etiquetas que se pueden quitar
export default function DamagePicker({ id, value, onChange, ...fieldProps }) {
  const available = DAMAGE_OPTIONS.filter((option) => !value.includes(option.value))

  return (
    <div className="damage-picker">
      <SearchSelect
        {...fieldProps}
        id={id}
        options={available}
        value=""
        placeholder={value.length === 0 ? 'Escriba el daño: pantalla, batería, humedad…' : 'Agregar otro daño'}
        emptyMessage={available.length === 0 ? 'Ya agregó todos los daños' : 'No hay daños con ese nombre'}
        onChange={(serviceType) => serviceType && onChange([...value, serviceType])}
      />
      {value.length > 0 && (
        <ul className="damage-picker__list" aria-label="Daños agregados">
          {value.map((serviceType) => (
            <li key={serviceType} className="damage-picker__item">
              {SERVICE_TYPE_LABELS[serviceType]}
              <button
                type="button"
                className="damage-picker__remove"
                aria-label={`Quitar ${SERVICE_TYPE_LABELS[serviceType]}`}
                onClick={() => onChange(value.filter((item) => item !== serviceType))}
              >
                <MdClose aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
