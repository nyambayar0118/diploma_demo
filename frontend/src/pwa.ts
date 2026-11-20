// Installing the web app on the phone's home screen (PWA).
// Chrome / Edge / Samsung Internet fire `beforeinstallprompt`, which we keep to show their install dialog
// from our own button. Safari (iPhone / iPad) has no such dialog: the user adds it through the Share menu.

type InstallPromptEvent = Event & { prompt: () => Promise<void>; userChoice: Promise<{ outcome: string }> }

let deferred: InstallPromptEvent | null = null
export const INSTALL_CHANGED = 'routeplanner:install-changed'

window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault() // we show our own button instead of the browser's mini bar
  deferred = e as InstallPromptEvent
  window.dispatchEvent(new Event(INSTALL_CHANGED))
})
window.addEventListener('appinstalled', () => {
  deferred = null
  window.dispatchEvent(new Event(INSTALL_CHANGED))
})

export type Platform = 'installed' | 'in-app' | 'ios' | 'android' | 'desktop'

/** Which instructions fit this device and browser. */
export function platform(): Platform {
  const nav = navigator as Navigator & { standalone?: boolean }
  if (window.matchMedia('(display-mode: standalone)').matches || nav.standalone) return 'installed'
  const ua = navigator.userAgent
  // Messenger, Facebook, Instagram, etc. open links in their own browser, which cannot install apps
  if (/FBAN|FBAV|FB_IAB|Instagram|Line\/|KAKAOTALK|Snapchat|TikTok|MicroMessenger/i.test(ua)) return 'in-app'
  const iPad = /Macintosh/.test(ua) && navigator.maxTouchPoints > 1
  if (/iPhone|iPad|iPod/.test(ua) || iPad) return 'ios'
  if (/Android/i.test(ua)) return 'android'
  return 'desktop'
}

export function canPromptInstall() {
  return deferred != null
}

/** Shows the browser's own install dialog; true if the user accepted. */
export async function promptInstall(): Promise<boolean> {
  if (!deferred) return false
  const e = deferred
  deferred = null
  await e.prompt()
  const choice = await e.userChoice
  window.dispatchEvent(new Event(INSTALL_CHANGED))
  return choice.outcome === 'accepted'
}

const DISMISSED = 'routeplanner.install-dismissed'
export function bannerDismissed() {
  try {
    return localStorage.getItem(DISMISSED) === '1'
  } catch {
    return true
  }
}
export function dismissBanner() {
  try {
    localStorage.setItem(DISMISSED, '1')
  } catch {
    /* ignore */
  }
}
