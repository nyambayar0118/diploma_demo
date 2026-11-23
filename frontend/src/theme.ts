// Цайвар / бараан горим: хэрэглэгчийн сонголтыг хадгалж, хуудасны өнгөний хувьсагчийг сэлгэнэ.
import { useEffect, useState } from 'react'

export type Theme = 'light' | 'dark'
const KEY = 'routeplanner.theme'
export const THEME_CHANGED = 'routeplanner:theme-changed'

/** Хадгалсан сонголт, үгүй бол утас / компьютерийн тохиргоо. */
export function currentTheme(): Theme {
  try {
    const saved = localStorage.getItem(KEY)
    if (saved === 'light' || saved === 'dark') return saved
  } catch {
    /* хадгалах сан ашиглах боломжгүй */
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
    /* хадгалах сан ашиглах боломжгүй: энэ удаад л сэлгэнэ */
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
