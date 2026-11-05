import { useEffect, useState } from 'react'

// Hora actual que se renueva cada intervalo; una sola instancia por vista (no una por fila) y se limpia al salir
export function useNow(intervalMs = 60_000) {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(timer)
  }, [intervalMs])

  return now
}
