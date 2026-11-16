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
    // File-change events do not cross a Windows-drive bind mount (/mnt/d/...) into the container
    // (observed on WSL, 2026-11-12). VITE_USE_POLLING=true in .env turns on polling there; off by default.
    watch:
      process.env.VITE_USE_POLLING === 'true'
        ? { usePolling: true, interval: 300 }
        : undefined,
    proxy: {
      '/api': {
        target: process.env.API_PROXY_TARGET ?? 'http://localhost:8080',
      },
    },
  },
})
