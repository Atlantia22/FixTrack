import { useCallback, useEffect, useRef, useState } from 'react'
import { getErrorMessage } from '../utils/errors'

// Carga datos del backend con estado de carga y error; vuelve a cargar cuando cambian deps o se llama reload
// reload({ silent: true }) recarga sin mostrar el estado de carga (eventos en tiempo real); mutate cambia los datos en sitio
export function useRemoteData(loader, deps) {
  const [state, setState] = useState({ data: null, loading: true, error: null })
  const [version, setVersion] = useState(0)
  const silentRef = useRef(false)

  useEffect(() => {
    let active = true
    const silent = silentRef.current
    silentRef.current = false
    if (!silent) {
      setState((current) => ({ ...current, loading: true, error: null }))
    }
    loader()
      .then((data) => active && setState({ data, loading: false, error: null }))
      .catch((error) => active && setState((current) => ({ ...current, loading: false, error: getErrorMessage(error) })))
    return () => {
      active = false
    }
    // loader cambia en cada render; las dependencias reales llegan en deps
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version])

  const reload = useCallback((options) => {
    silentRef.current = Boolean(options?.silent)
    setVersion((current) => current + 1)
  }, [])

  const mutate = useCallback((updater) => {
    setState((current) => (current.data == null ? current : { ...current, data: updater(current.data) }))
  }, [])

  return { ...state, reload, mutate }
}
