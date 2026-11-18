import { useCallback, useEffect, useRef, useState } from 'react'
import { api, type PendingVisit, type VisitResult } from '../api'

/** Position without asking: only when the user has already allowed location access. */
async function knownPosition(): Promise<{ lat: number; lng: number } | null> {
  try {
    const status = await navigator.permissions?.query({ name: 'geolocation' })
    if (status?.state !== 'granted') return null
  } catch {
    return null
  }
  return new Promise((resolve) => navigator.geolocation.getCurrentPosition(
    (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude }),
    () => resolve(null),
    { enableHighAccuracy: true, timeout: 5000, maximumAge: 60000 },
  ))
}

/**
 * "Очсон уу?" (АШ23). Google Maps does the navigation, so when the user comes back to the app
 * (page opened or brought to the front) the server is asked whether a route stop needs confirming.
 */
export default function VisitPrompt({ onAnswered }: { onAnswered: (result: VisitResult) => void }) {
  const [question, setQuestion] = useState<PendingVisit | null>(null)
  const [busy, setBusy] = useState(false)
  const position = useRef<{ lat: number; lng: number } | null>(null)
  const later = useRef(new Set<number>()) // "Дараа": not asked again in this session

  const check = useCallback(async () => {
    position.current = await knownPosition()
    const p = position.current
    const query = p ? `?lat=${p.lat.toFixed(6)}&lng=${p.lng.toFixed(6)}` : ''
    try {
      const q = await api<PendingVisit | undefined>(`/api/visits/pending${query}`)
      setQuestion(q && !later.current.has(q.stopId) ? q : null)
    } catch {
      setQuestion(null)
    }
  }, [])

  useEffect(() => {
    check()
    const onVisible = () => document.visibilityState === 'visible' && check()
    document.addEventListener('visibilitychange', onVisible)
    const timer = window.setInterval(check, 5 * 60 * 1000)
    return () => {
      document.removeEventListener('visibilitychange', onVisible)
      window.clearInterval(timer)
    }
  }, [check])

  if (!question) return null

  const answer = async (visited: boolean) => {
    setBusy(true)
    try {
      const result = visited
        ? await api<VisitResult>(`/api/visits/${question.stopId}/confirm`,
          position.current ? { method: 'POST', body: position.current } : { method: 'POST' })
        : await api<VisitResult>(`/api/visits/${question.stopId}/skip`, { method: 'POST' })
      onAnswered(result)
    } finally {
      setBusy(false)
      check()
    }
  }

  const askLater = () => {
    later.current.add(question.stopId)
    setQuestion(null)
  }

  return (
    <div className="visit-card" role="dialog" aria-label="Очсон уу?">
      <div className="visit-title">Очсон уу?</div>
      <div className="visit-place">{question.name}</div>
      <div className="muted" style={{ fontSize: 13 }}>
        Маршрутын {question.stopOrder}/{question.stopCount}-р зогсоол
        {question.nearby && ' · Та энэ газрын ойролцоо байна'}
      </div>
      <div className="visit-actions">
        <button className="btn" disabled={busy} onClick={() => answer(true)}>Тийм, очсон</button>
        <button className="btn secondary" disabled={busy} onClick={() => answer(false)}>Үгүй</button>
        <button type="button" className="link" onClick={askLater}>Дараа</button>
      </div>
    </div>
  )
}
