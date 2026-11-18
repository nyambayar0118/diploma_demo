import { useEffect, useState } from 'react'
import { api, ApiError, logInteraction, type Preferences, type RecommendationBatch, type RecommendationItem,
  type TransportMode } from '../api'
import { TRANSPORT_LABELS } from '../categoryLabels'
import { MAIN_CATEGORY_COLORS } from '../places'

type Props = {
  mapCenter: () => { lat: number; lng: number }
  onResults: (items: RecommendationItem[]) => void
  onOpen: (item: RecommendationItem) => void
  onClose: () => void
}

const DURATIONS = [60, 120, 180, 240, 360]

/** Browser location if allowed (needs https or localhost), otherwise the map centre. */
function currentPosition(fallback: () => { lat: number; lng: number }) {
  return new Promise<{ lat: number; lng: number; fromDevice: boolean }>((resolve) => {
    if (!navigator.geolocation) return resolve({ ...fallback(), fromDevice: false })
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude, fromDevice: true }),
      () => resolve({ ...fallback(), fromDevice: false }),
      { timeout: 5000, maximumAge: 60000 },
    )
  })
}

function distanceText(m: number) {
  return m < 1000 ? `${m} м` : `${(m / 1000).toFixed(1)} км`
}

export default function RecommendationPanel({ mapCenter, onResults, onOpen, onClose }: Props) {
  const [minutes, setMinutes] = useState(180)
  const [mode, setMode] = useState<TransportMode>('WALKING')
  const [batch, setBatch] = useState<RecommendationBatch | null>(null)
  const [items, setItems] = useState<RecommendationItem[]>([])
  const [origin, setOrigin] = useState<string>('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api<Preferences>('/api/users/me/preferences').then((p) => {
      setMinutes(p.timeBudgetMin)
      setMode([...p.transportModes].sort()[0] ?? 'WALKING')
    }).catch(() => undefined)
  }, [])

  const run = async () => {
    setBusy(true)
    setError(null)
    try {
      const pos = await currentPosition(mapCenter)
      setOrigin(pos.fromDevice ? 'Таны байршлаас' : 'Газрын зургийн төвөөс (байршил тодорхойлогдсонгүй)')
      const b = await api<RecommendationBatch>('/api/recommendations', {
        method: 'POST', body: { lat: pos.lat, lng: pos.lng, availableMinutes: minutes, transportMode: mode },
      })
      setBatch(b)
      setItems(b.items)
      onResults(b.items)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    } finally {
      setBusy(false)
    }
  }

  const reject = (item: RecommendationItem) => {
    logInteraction(item.placeId, 'REJECT', item.recommendationId)
    const rest = items.filter((i) => i.recommendationId !== item.recommendationId)
    setItems(rest)
    onResults(rest)
  }

  const open = (item: RecommendationItem) => {
    logInteraction(item.placeId, 'ACCEPT', item.recommendationId)
    onOpen(item)
  }

  return (
    <aside className="panel">
      <button className="sheet-close" onClick={onClose} aria-label="Хаах">×</button>
      <h2 className="sheet-title">Санал болгох</h2>
      <div className="field">
        <label htmlFor="rec-time">Боломжит хугацаа</label>
        <select id="rec-time" value={minutes} onChange={(e) => setMinutes(Number(e.target.value))}>
          {[...new Set([...DURATIONS, minutes])].sort((a, b) => a - b).map((m) => (
            <option key={m} value={m}>{m >= 60 ? `${m / 60} цаг` : `${m} мин`}</option>
          ))}
        </select>
      </div>
      <div className="chips" style={{ marginBottom: 14 }}>
        {(Object.keys(TRANSPORT_LABELS) as TransportMode[]).map((m) => (
          <button key={m} type="button" className={`chip ${mode === m ? 'on' : ''}`} onClick={() => setMode(m)}>
            {TRANSPORT_LABELS[m]}
          </button>
        ))}
      </div>
      <button className="btn block" onClick={run} disabled={busy}>
        {busy ? 'Хайж байна…' : batch ? 'Дахин санал болгох' : 'Санал авах'}
      </button>
      {error && <div className="error" style={{ marginTop: 12 }}>{error}</div>}

      {batch && (
        <>
          <p className="muted small">{origin}</p>
          {items.length === 0 && (
            <p className="muted">Тохирох газар олдсонгүй. Хугацаа, тээврийн хэрэгсэл эсвэл профайл дахь хайх радиусаа өөрчилж үзнэ үү.</p>
          )}
          <ol className="rec-list">
            {items.map((it, i) => (
              <li key={it.recommendationId}>
                <button type="button" className="rec-main" onClick={() => open(it)}>
                  <span className="rec-num" style={{ background: MAIN_CATEGORY_COLORS[it.mainCategory] ?? '#888' }}>{i + 1}</span>
                  <span className="r-main">
                    <span className="r-name">{it.name}</span>
                    <span className="r-sub">{it.categoryName} · {distanceText(it.distanceM)} · ~{it.travelMinutes} мин</span>
                    <span className={`rec-reason ${it.exploration ? 'explore' : ''}`}>{it.reason}</span>
                  </span>
                </button>
                <button type="button" className="rec-x" title="Сонирхохгүй" onClick={() => reject(it)}>✕</button>
              </li>
            ))}
          </ol>
        </>
      )}
    </aside>
  )
}
