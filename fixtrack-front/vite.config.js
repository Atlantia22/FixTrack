import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// Política de contenido del build: solo scripts propios y conexiones a la API y a su canal en tiempo real.
// Va solo en el build porque en desarrollo Vite inyecta scripts en línea (recarga en caliente) que la política bloquearía
function contentSecurityPolicy(env) {
  const api = new URL(env.VITE_API_URL=https://fixtrack-djc5csbfapfhfyay.eastus-01.azurewebsites.net/api')
  const realtime = new URL(env.VITE_WS_URL ?? `${api.origin}/ws`)
  const socketOrigin = realtime.origin.replace(/^http/, 'ws')
  const policy = [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: blob:",
    "font-src 'self'",
    `connect-src 'self' ${[...new Set([api.origin, realtime.origin, socketOrigin])].join(' ')}`,
    // Los comprobantes PDF se muestran e imprimen desde un blob en un marco
    'frame-src blob:',
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
  ].join('; ')

  return {
    name: 'fixtrack-content-security-policy',
    apply: 'build',
    transformIndexHtml(html) {
      return html.replace('<meta charset="UTF-8" />', `<meta charset="UTF-8" />\n    <meta http-equiv="Content-Security-Policy" content="${policy}" />`)
    },
  }
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [react(), contentSecurityPolicy(loadEnv(mode, process.cwd()))],
  // sockjs-client usa la variable global de Node; en el navegador equivale a globalThis
  define: {
    global: 'globalThis',
  },
  // Puerto fijo: el CORS del backend solo acepta http://localhost:5173
  server: {
    port: 5173,
    strictPort: true,
  },
}))
