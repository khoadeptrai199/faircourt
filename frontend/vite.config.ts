import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Vite proxies /api to the backend so browser cookies and calls share one origin (blueprint section 0).
// In Docker the target is the Compose service (API_PROXY_TARGET=http://backend:8080).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
      },
    },
  },
})
