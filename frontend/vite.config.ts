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
        background_color: '#121722',
        display: 'standalone',
        start_url: '/',
        // Android "суулгах" санал болгоход 192, 512 хэмжээтэй PNG хэрэгтэй; maskable дүрсийг дугуй хэлбэрээр тайрч болно
        icons: [
          { src: '/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: '/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // API болон газрын зургийн тайлыг үргэлж сүлжээнээс авна
        navigateFallbackDenylist: [/^\/api\//, /^\/tiles\//],
      },
    }),
  ],
  server: {
    // `npm run dev` API дуудлагыг docker compose-оор ажилласан сервер рүү (nginx-ээр дамжуулан) шилжүүлнэ
    proxy: {
      '/api': 'http://localhost:8080',
      '/tiles': 'http://localhost:8080',
    },
  },
})
