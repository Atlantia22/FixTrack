import { useState } from 'react'
import { MdBackspace } from 'react-icons/md'
import { formatPattern, isSavedPattern, parsePattern, PATTERN_MIN_POINTS } from '../../utils/unlockPattern'
import PasswordInput from '../PasswordInput/PasswordInput'
import PatternLock from '../PatternLock/PatternLock'
import './UnlockCodeField.css'

// Contraseña o PIN escritos, o patrón dibujado que se guarda como «1-5-9-6»; recibe las props de FormField
export default function UnlockCodeField({ id, value, onChange, ...fieldProps }) {
  const [mode, setMode] = useState(() => (isSavedPattern(value) ? 'pattern' : 'text'))
  // Los puntos se guardan aquí mientras se dibuja; el formulario solo recibe patrones completos
  const [points, setPoints] = useState(() => (isSavedPattern(value) ? parsePattern(value) : []))
  const incomplete = points.length > 0 && points.length < PATTERN_MIN_POINTS

  function changePoints(next) {
    setPoints(next)
    onChange(next.length >= PATTERN_MIN_POINTS ? formatPattern(next) : '')
  }

  // Cambiar de modo borra lo anterior para no guardar un patrón como contraseña ni al revés
  function changeMode(nextMode) {
    if (nextMode === mode) return
    setMode(nextMode)
    setPoints([])
    onChange('')
  }

  return (
    <div className="unlock-field">
      <div className="unlock-field__modes" role="radiogroup" aria-label="Tipo de desbloqueo">
        <label><input type="radio" name={`${id}-mode`} checked={mode === 'text'} onChange={() => changeMode('text')} /> Contraseña o PIN</label>
        <label><input type="radio" name={`${id}-mode`} checked={mode === 'pattern'} onChange={() => changeMode('pattern')} /> Patrón</label>
      </div>
      {mode === 'text' ? (
        <PasswordInput {...fieldProps} id={id} maxLength={100} autoComplete="off" value={value} onChange={(event) => onChange(event.target.value)} />
      ) : (
        <div className="unlock-field__pattern">
          <PatternLock {...fieldProps} id={id} points={points} onChange={changePoints} />
          <div className="unlock-field__summary" aria-live="polite">
            <p className="text-sm">
              {points.length === 0
                ? 'Dibuje el patrón uniendo los puntos o márquelos en orden con el teclado.'
                : <>Secuencia: <strong>{formatPattern(points)}</strong></>}
            </p>
            {incomplete && (
              <p className="form-error">Faltan puntos: el patrón debe tener al menos {PATTERN_MIN_POINTS}; así no se guarda.</p>
            )}
            <button type="button" className="btn btn--secondary" disabled={points.length === 0} onClick={() => changePoints([])}>
              <MdBackspace aria-hidden="true" /> Borrar patrón
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
