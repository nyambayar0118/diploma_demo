import { useEffect, useState } from 'react'
import { api, ApiError, logInteraction, type Preferences, type RecommendationBatch, type RecommendationItem,
  type TransportMode } from '../api'
import { TRANSPORT_LABELS } from '../categoryLabels'
import { MAIN_CATEGORY_COLORS } from '../places'

type LatLng = { lat: number; lng: number }

type Props = {
  mapCenter: () => LatLng
  /** Shows the start point on the map as a draggable marker; calls back when the user moves it. */
  showOrigin: (p: LatLng | null, onMove: (p: LatLng) => void) => void
  /** Resolves with the next point the user taps on the map. */
  pickOnMap: () => Promise<LatLng>
  onResults: (items: RecommendationItem[]) => void
  onOpen: (item: RecommendationItem) => void
  onClose: () => void
}

const DURATIONS = [60, 120, 180, 240, 360]

type Origin = LatLng & { label: string; warning?: string }

/**
 * Device location (needs https or localhost and the browser's permission). Laptops without GPS get a
 * Wi-Fi / IP-based position, which can be kilometres off; then the user can move the marker or pick a point.
 */
function devicePosition() {
  return new Promise<{ lat: number; lng: number; accuracy: number } | null>((resolve) => {
    if (!navigator.geolocation) return resolve(null)
    navigator.geolocation.getCurrentPosition(
      (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude, accuracy: p.coords.accuracy }),
      () => resolve(null),
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 30000 },
    )
  })
}

function distanceText(m: number) {
  return m < 1000 ? `${m} м` : `${(m / 1000).toFixed(1)} км`
}

export default function RecommendationPanel({ mapCenter, showOrigin, pickOnMap, onResults, onOpen, onClose }: Props) {
  const [minutes, setMinutes] = useState(180)
  const [mode, setMode] = useState<TransportMode>('WALKING')
  const [batch, setBatch] = useState<RecommendationBatch | null>(null)
  const [items, setItems] = useState<RecommendationItem[]>([])
  const [origin, setOrigin] = useState<Origin | null>(null)
  const [picking, setPicking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api<Preferences>('/api/users/me/preferences').then((p) => {
      setMinutes(p.timeBudgetMin)
      setMode([...p.transportModes].sort()[0] ?? 'WALKING')
    }).catch(() => undefined)
  }, [])

  const placeOrigin = (o: Origin) => {
    setOrigin(o)
    showOrigin(o, (p) => setOrigin({ ...p, label: 'Таны сонгосон цэг' }))
  }

  const locate = async () => {
    const pos = await devicePosition()
    if (!pos) {
      const c = mapCenter()
      placeOrigin({ ...c, label: 'Газрын зургийн төв', warning: 'Байршил тодорхойлогдсонгүй. Тэмдэгтийг чирж эсвэл газрын зураг дээр сонгоно уу.' })
      return
    }
    placeOrigin({
      lat: pos.lat, lng: pos.lng, label: `Таны байршил (±${Math.round(pos.accuracy)} м)`,
      warning: pos.accuracy > 1000
        ? 'Байршил ойролцоо байна (компьютер GPS-гүй үед Wi-Fi / IP хаягаар тогтоодог). Тэмдэгтийг чирж засна уу.'
        : undefined,
    })
  }

  // Locate once when the panel opens; remove the marker when it closes
  useEffect(() => {
    locate()
    return () => showOrigin(null, () => undefined)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const pick = async () => {
    setPicking(true)
    const p = await pickOnMap()
    setPicking(false)
    placeOrigin({ ...p, label: 'Таны сонгосон цэг' })
  }

  const run = async () => {
    if (!origin) return
    setBusy(true)
    setError(null)
    try {
      const b = await api<RecommendationBatch>('/api/recommendations', {
        method: 'POST', body: { lat: origin.lat, lng: origin.lng, availableMinutes: minutes, transportMode: mode },
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
        <label>Эхлэх цэг</label>
        <div className="origin">
          <span>{origin ? origin.label : 'Байршил тогтоож байна…'}</span>
          <span className="origin-actions">
            <button type="button" className="link" onClick={locate}>Миний байршил</button>
            <button type="button" className="link" onClick={pick} disabled={picking}>
              {picking ? 'Газрын зураг дээр дарна уу…' : 'Газрын зураг дээр сонгох'}
            </button>
          </span>
        </div>
        {origin?.warning && <span className="hint warn">{origin.warning}</span>}
      </div>
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
      <button className="btn block" onClick={run} disabled={busy || !origin}>
        {busy ? 'Хайж байна…' : batch ? 'Дахин санал болгох' : 'Санал авах'}
      </button>
      {error && <div className="error" style={{ marginTop: 12 }}>{error}</div>}

      {batch && (
        <>
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
