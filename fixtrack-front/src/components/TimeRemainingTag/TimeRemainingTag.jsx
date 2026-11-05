import { formatTimeRemaining } from '../../utils/timeRemaining'
import './TimeRemainingTag.css'

// Etiqueta del tiempo restante hasta la entrega; el color indica la urgencia y el texto siempre dice cuánto falta
export default function TimeRemainingTag({ order, now }) {
  const remaining = formatTimeRemaining(order, now)
  return <span className={`time-remaining time-remaining--${remaining.level}`}>{remaining.text}</span>
}
