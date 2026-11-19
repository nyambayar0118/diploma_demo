import { useEffect, useState } from 'react'

export type Theme = 'light' | 'dark'
const KEY = 'routeplanner.theme'
export const THEME_CHANGED = 'routeplanner:theme-changed'

/** Saved choice, otherwise the phone / computer setting. */
export function currentTheme(): Theme {
  try {
    const saved = localStorage.getItem(KEY)
    if (saved === 'light' || saved === 'dark') return saved
  } catch {
    /* storage unavailable */
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#111418' : '#1f6feb')
}

export function setTheme(theme: Theme) {
  try {
    localStorage.setItem(KEY, theme)
  } catch {
    /* storage unavailable: still switch for this visit */
  }
  applyTheme(theme)
  window.dispatchEvent(new Event(THEME_CHANGED))
}

export function useTheme(): [Theme, (t: Theme) => void] {
  const [theme, set] = useState<Theme>(currentTheme)
  useEffect(() => {
    const on = () => set(currentTheme())
    window.addEventListener(THEME_CHANGED, on)
    return () => window.removeEventListener(THEME_CHANGED, on)
  }, [])
  return [theme, setTheme]
}
