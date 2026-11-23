import { useEffect, useRef, useState } from 'react'
import { api } from '../api'

type GoogleId = {
  initialize: (o: { client_id: string; callback: (r: { credential: string }) => void; ux_mode?: string }) => void
  renderButton: (el: HTMLElement, o: Record<string, unknown>) => void
}
declare global {
  interface Window { google?: { accounts: { id: GoogleId } } }
}

let script: Promise<void> | null = null
function loadGoogleScript() {
  script ??= new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = 'https://accounts.google.com/gsi/client'
    s.async = true
    s.onload = () => resolve()
    s.onerror = () => {
      script = null
      reject(new Error('Google script failed to load'))
    }
    document.head.appendChild(s)
  })
  return script
}

/**
 * "Google-ээр нэвтрэх" (Google Identity Services). Google хөтөчид гарын үсэгтэй ID токен өгч, сервер түүнийг
 * шалгаад өөрийн токеноор солино. Client id тохируулаагүй бол товч харагдахгүй.
 */
export default function GoogleButton({ onCredential }: { onCredential: (credential: string) => void }) {
  const ref = useRef<HTMLDivElement>(null)
  const callback = useRef(onCredential)
  const [clientId, setClientId] = useState<string | null>(null)

  useEffect(() => {
    callback.current = onCredential
  }, [onCredential])

  useEffect(() => {
    api<{ googleClientId: string | null }>('/api/auth/config')
      .then((c) => setClientId(c.googleClientId))
      .catch(() => setClientId(null))
  }, [])

  useEffect(() => {
    if (!clientId) return
    loadGoogleScript().then(() => {
      const id = window.google?.accounts.id
      if (!id || !ref.current) return
      id.initialize({ client_id: clientId, callback: (r) => callback.current(r.credential) })
      id.renderButton(ref.current, {
        theme: 'outline', size: 'large', text: 'continue_with', shape: 'pill', locale: 'mn',
        width: Math.min(320, ref.current.clientWidth || 320),
      })
    }).catch(() => undefined)
  }, [clientId])

  if (!clientId) return null
  return (
    <>
      <div className="or"><span>эсвэл</span></div>
      <div ref={ref} className="google-btn" />
    </>
  )
}
