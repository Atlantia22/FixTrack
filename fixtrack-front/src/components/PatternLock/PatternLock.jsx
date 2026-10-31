import { useRef, useState } from 'react'
import { formatPattern, middlePoint } from '../../utils/unlockPattern'
import './PatternLock.css'

const POINTS = [1, 2, 3, 4, 5, 6, 7, 8, 9]
// Fracción de la celda en la que el trazo «toca» el punto; fuera de ella pasa sin marcarlo
const HIT_RADIUS = 0.35

function center(point) {
  return { x: ((point - 1) % 3) + 0.5, y: Math.floor((point - 1) / 3) + 0.5 }
}

// Agrega un punto al patrón, con el de en medio si el trazo lo salta; ningún punto se repite
function addPoint(points, point) {
  if (points.includes(point)) return points
  const last = points.at(-1)
  const middle = last ? middlePoint(last, point) : null
  return [...points, ...(middle && !points.includes(middle) ? [middle] : []), point]
}

// Cuadrícula 3×3 para dibujar el patrón de desbloqueo con el mouse o el dedo; con teclado se marcan los puntos en orden
export default function PatternLock({ id, points, onChange, readOnly = false, label = 'Patrón de desbloqueo', ...groupProps }) {
  const boardRef = useRef(null)
  const [drawing, setDrawing] = useState(false)
  const [cursor, setCursor] = useState(null)

  // Punto bajo el puntero (en coordenadas de la cuadrícula de 0 a 3) o null si está entre puntos
  function locate(event) {
    const rect = boardRef.current.getBoundingClientRect()
    const x = ((event.clientX - rect.left) / rect.width) * 3
    const y = ((event.clientY - rect.top) / rect.height) * 3
    const col = Math.floor(x)
    const row = Math.floor(y)
    const inside = col >= 0 && col < 3 && row >= 0 && row < 3
      && Math.hypot(x - (col + 0.5), y - (row + 0.5)) <= HIT_RADIUS
    return { position: { x, y }, point: inside ? row * 3 + col + 1 : null }
  }

  function handlePointerDown(event) {
    const { point, position } = locate(event)
    if (!point) return
    event.preventDefault()
    boardRef.current.setPointerCapture(event.pointerId)
    setDrawing(true)
    setCursor(position)
    onChange([point])
  }

  function handlePointerMove(event) {
    if (!drawing) return
    const { point, position } = locate(event)
    setCursor(position)
    if (point && !points.includes(point)) onChange(addPoint(points, point))
  }

  function stopDrawing() {
    setDrawing(false)
    setCursor(null)
  }

  const last = points.at(-1)
  const segments = points.slice(1).map((point, index) => [center(points[index]), center(point)])
  if (drawing && last && cursor) segments.push([center(last), cursor])

  return (
    <div
      {...groupProps}
      id={id}
      ref={boardRef}
      className={`pattern-lock ${readOnly ? 'pattern-lock--readonly' : ''}`}
      role={readOnly ? 'img' : 'group'}
      aria-label={points.length > 0 ? `${label}: ${formatPattern(points)}` : label}
      onPointerDown={readOnly ? undefined : handlePointerDown}
      onPointerMove={readOnly ? undefined : handlePointerMove}
      onPointerUp={readOnly ? undefined : stopDrawing}
      onPointerCancel={readOnly ? undefined : stopDrawing}
    >
      <svg className="pattern-lock__lines" viewBox="0 0 3 3" aria-hidden="true">
        {segments.map(([from, to], index) => (
          <line key={index} x1={from.x} y1={from.y} x2={to.x} y2={to.y} />
        ))}
      </svg>
      <div className="pattern-lock__grid">
        {POINTS.map((point) => {
          const order = points.indexOf(point) + 1
          const className = `pattern-lock__point ${order ? 'pattern-lock__point--on' : ''} ${order === 1 ? 'pattern-lock__point--start' : ''}`
          return readOnly ? (
            <span key={point} className={className} aria-hidden="true">{order || ''}</span>
          ) : (
            <button
              key={point}
              type="button"
              className={className}
              aria-label={order ? `Punto ${point}, paso ${order}` : `Punto ${point}`}
              aria-pressed={order > 0}
              onClick={() => onChange(addPoint(points, point))}
            >
              {order || ''}
            </button>
          )
        })}
      </div>
    </div>
  )
}
