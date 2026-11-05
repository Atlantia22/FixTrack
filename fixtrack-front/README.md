# FixTrack — Frontend

Aplicación web de FixTrack con React 19 y Vite 8. Consume la API del backend (`fixtrackback`) y su canal en tiempo real.

## Arrancar

```bash
cp .env.example .env   # VITE_API_URL=http://localhost:8080/api
npm install
npm run dev            # http://localhost:5173
```

El puerto 5173 es fijo (`strictPort`): el backend solo acepta peticiones (CORS) desde ese origen.

## Variables de entorno

| Variable | Por defecto | Uso |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080/api` | Dirección de la API |
| `VITE_WS_URL` | La de la API sin `/api` + `/ws` | Canal en tiempo real (STOMP sobre SockJS); solo si vive en otra dirección |

## Comandos

| Comando | Qué hace |
|---|---|
| `npm run dev` | Servidor de desarrollo |
| `npm run build` | Compila para producción en `dist/` |
| `npm run lint` | Revisa el código con oxlint |
| `npm test` | Pruebas de las utilidades con el ejecutor de Node (`src/**/*.test.js`) |

## Más información

- Estructura, convenciones y reglas de diseño: [`.claude/rules/react.md`](../.claude/rules/react.md).
- Guía del sistema: [`DOCUMENTACION.md`](../DOCUMENTACION.md).
