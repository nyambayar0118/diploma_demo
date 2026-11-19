import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './ui.css'
import { applyTheme, currentTheme } from './theme'

applyTheme(currentTheme())
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
