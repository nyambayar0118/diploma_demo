// Веб аппликейшнийг утасны нүүр дэлгэцэнд суулгах (PWA).
// Chrome / Edge / Samsung Internet `beforeinstallprompt` үйл явдлыг өгдөг; түүнийг хадгалж суулгах цонхыг өөрийн
// товчоор нээнэ. Safari (iPhone / iPad)-д ийм цонх байхгүй: хэрэглэгч Share цэсээр нэмнэ.

type InstallPromptEvent = Event & { prompt: () => Promise<void>; userChoice: Promise<{ outcome: string }> }

let deferred: InstallPromptEvent | null = null
export const INSTALL_CHANGED = 'routeplanner:install-changed'

window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault() // хөтчийн жижиг мөрийн оронд өөрийн товчийг харуулна
  deferred = e as InstallPromptEvent
  window.dispatchEvent(new Event(INSTALL_CHANGED))
})
window.addEventListener('appinstalled', () => {
  deferred = null
  window.dispatchEvent(new Event(INSTALL_CHANGED))
})

export type Platform = 'installed' | 'in-app' | 'ios' | 'android' | 'desktop'

/** Энэ төхөөрөмж, хөтөчид аль заавар тохирох. */
export function platform(): Platform {
  const nav = navigator as Navigator & { standalone?: boolean }
  if (window.matchMedia('(display-mode: standalone)').matches || nav.standalone) return 'installed'
  const ua = navigator.userAgent
  // Messenger, Facebook, Instagram зэрэг холбоосыг өөрийн дотоод хөтчөөр нээдэг бөгөөд тэндээс апп суулгах боломжгүй
  if (/FBAN|FBAV|FB_IAB|Instagram|Line\/|KAKAOTALK|Snapchat|TikTok|MicroMessenger/i.test(ua)) return 'in-app'
  const iPad = /Macintosh/.test(ua) && navigator.maxTouchPoints > 1
  if (/iPhone|iPad|iPod/.test(ua) || iPad) return 'ios'
  if (/Android/i.test(ua)) return 'android'
  return 'desktop'
}

export function canPromptInstall() {
  return deferred != null
}

/** Хөтчийн суулгах цонхыг харуулна; хэрэглэгч зөвшөөрвөл true. */
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
    /* үл тооно */
  }
}
