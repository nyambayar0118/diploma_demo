import { useEffect } from 'react'
import { api, type MyLocation } from './api'

const SEND_EVERY_MS = 2 * 60 * 1000
export const SHARING_CHANGED = 'routeplanner:sharing-changed'

/**
 * While the user shares their location (switch on the friends page), the open app sends the position
 * at most every 2 minutes. Closing the app stops the updates; the server hides positions older than 60 min.
 */
export function useLocationSharing(enabled: boolean) {
  useEffect(() => {
    if (!enabled || !navigator.geolocation) return
    let watch: number | null = null
    let lastSent = 0
    let stopped = false

    const start = async () => {
      const me = await api<MyLocation>('/api/locations/me').catch(() => null)
      if (stopped || !me?.shared) return
      watch = navigator.geolocation.watchPosition((p) => {
        if (Date.now() - lastSent < SEND_EVERY_MS) return
        lastSent = Date.now()
        api('/api/locations/me', {
          method: 'PUT', body: { lat: p.coords.latitude, lng: p.coords.longitude, shared: true },
        }).catch(() => undefined)
      }, () => undefined, { enableHighAccuracy: true, maximumAge: 60000 })
    }
    const restart = () => {
      if (watch != null) navigator.geolocation.clearWatch(watch)
      watch = null
      lastSent = 0
      start()
    }
    start()
    window.addEventListener(SHARING_CHANGED, restart)
    return () => {
      stopped = true
      window.removeEventListener(SHARING_CHANGED, restart)
      if (watch != null) navigator.geolocation.clearWatch(watch)
    }
  }, [enabled])
}
