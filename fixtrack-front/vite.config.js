import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // sockjs-client usa la variable global de Node; en el navegador equivale a globalThis
  define: {
    global: 'globalThis',
  },
  // Puerto fijo: el CORS del backend solo acepta http://localhost:5173
  server: {
    port: 5173,
    strictPort: true,
  },
})
