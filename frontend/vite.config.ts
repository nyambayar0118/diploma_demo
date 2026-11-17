import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'Ухаалаг маршрут',
        short_name: 'Маршрут',
        description: 'Сонирхолд суурилсан газар санал болгох, маршрут төлөвлөх систем',
        lang: 'mn',
        theme_color: '#1f6feb',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        icons: [{ src: '/favicon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' }],
      },
      workbox: {
        // API and map tiles must always come from the network
        navigateFallbackDenylist: [/^\/api\//, /^\/tiles\//],
      },
    }),
  ],
  server: {
    // `npm run dev` forwards API calls to the backend started by docker compose (through nginx)
    proxy: {
      '/api': 'http://localhost:8080',
      '/tiles': 'http://localhost:8080',
    },
  },
})
