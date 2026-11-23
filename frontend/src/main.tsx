// Аппликейшний эхлэл: React-ийг хуудсанд холбож, PWA-ийн service worker-ийг бүртгэнэ.
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './ui.css'
import { applyTheme, currentTheme } from './theme'
import './pwa'
import App from './App.tsx'

applyTheme(currentTheme())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
