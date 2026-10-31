// Patrón de desbloqueo guardado como los puntos de la cuadrícula 3×3 (1 a 9, por filas) separados por guiones: «1-5-9-6»
export const PATTERN_MIN_POINTS = 4

const PATTERN_CODE = /^[1-9](-[1-9]){0,8}$/

export function isUnlockPattern(code) {
  return typeof code === 'string' && PATTERN_CODE.test(code) && new Set(code.split('-')).size === code.split('-').length
}

export function parsePattern(code) {
  return isUnlockPattern(code) ? code.split('-').map(Number) : []
}

// Un patrón guardado tiene al menos PATTERN_MIN_POINTS puntos; así un PIN de un dígito no se confunde con uno
export function isSavedPattern(code) {
  return parsePattern(code).length >= PATTERN_MIN_POINTS
}
export function formatPattern(points) {
  return points.join('-')
}

// Punto que queda en medio de dos (1 y 3 pasan por 2; 1 y 9 por 5), como en los teléfonos
export function middlePoint(from, to) {
  const [fromRow, fromCol] = [Math.floor((from - 1) / 3), (from - 1) % 3]
  const [toRow, toCol] = [Math.floor((to - 1) / 3), (to - 1) % 3]
  if ((fromRow + toRow) % 2 !== 0 || (fromCol + toCol) % 2 !== 0) return null
  return ((fromRow + toRow) / 2) * 3 + (fromCol + toCol) / 2 + 1
}
