import { useEffect, useRef, useState } from 'react'
import { api, ApiError, logInteraction, type Category, type Preferences, type RecommendationBatch, type RecommendationItem,
  type RouteView, type TransportMode } from '../api'
import { TRANSPORT_LABELS } from '../categoryLabels'
import { MAIN_CATEGORY_COLORS } from '../places'
import BottomSheet from './BottomSheet'

type LatLng = { lat: number; lng: number }

type Props = {
  mapCenter: () => LatLng
  /** Эхлэх цэгийг чирч болох тэмдэгтээр харуулна; хэрэглэгч зөөхөд мэдэгдэнэ. */
  showOrigin: (p: LatLng | null, onMove: (p: LatLng) => void) => void
  /** Хэрэглэгчийн газрын зураг дээр дарах дараагийн цэгийг буцаана. */
  pickOnMap: () => Promise<LatLng>
  onResults: (items: RecommendationItem[]) => void
  /** Газрын зураг дээр харуулж буй санал (жагсаалт эсвэл тэмдэгт дээр дарсан). */
  focusedId: number | null
  /** Саналыг дарахад газрын зураг тийш шилжинэ. */
  onFocus: (item: RecommendationItem) => void
  /** "Дэлгэрэнгүй": газрын мэдээллийг нээнэ. */
  onOpen: (item: RecommendationItem) => void
  onRoute: (route: RouteView) => void
  onClose: () => void
  /** "Юу хайж байна вэ?" сонголтод зориулсан үндсэн ангиллууд (дэд ангиллын хамт). */
  categories: Category[]
  /** Газрын зургийн ангиллын мөрөнд сонгосон ангилал; анхны сонголт болно. */
  initialCategory?: string
}

const MAX_STOPS = 4

const DURATIONS = [60, 120, 180, 240, 360]

type Origin = LatLng & { label: string; warning?: string }

/**
 * Төхөөрөмжийн байршил (https эсвэл localhost, хөтчийн зөвшөөрөл хэрэгтэй). GPS-гүй зөөврийн компьютер Wi-Fi / IP-д
 * суурилсан, хэдэн км зөрүүтэй байршил авч болно; тэгвэл хэрэглэгч тэмдэгтийг зөөх эсвэл цэг сонгоно.
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

export default function RecommendationPanel({ mapCenter, showOrigin, pickOnMap, onResults, focusedId, onFocus, onOpen,
  onRoute, onClose, categories, initialCategory }: Props) {
  const itemRefs = useRef(new Map<number, HTMLLIElement>())
  // Газрын зураг дээр тэмдэгт дарсан: харгалзах мөрийг харагдуулна
  useEffect(() => {
    if (focusedId != null) itemRefs.current.get(focusedId)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [focusedId])
  const [minutes, setMinutes] = useState(180)
  const [mode, setMode] = useState<TransportMode>('WALKING')
  // Юу хайх: үндсэн ангиллууд, хүсвэл аль нэгнийх нь дэд ангиллаар нарийсгана (хоосон = бүгд)
  const [mains, setMains] = useState<string[]>(initialCategory ? [initialCategory] : [])
  const [subs, setSubs] = useState<string[]>([])
  const toggleMain = (code: string) => {
    setSubs([])
    setMains((m) => (m.includes(code) ? m.filter((x) => x !== code) : [...m, code]))
  }
  const toggleSub = (code: string) => setSubs((s) => (s.includes(code) ? s.filter((x) => x !== code) : [...s, code]))
  const chosenCategories = subs.length > 0 ? subs : mains
  const narrowable = mains.length === 1 ? categories.find((c) => c.code === mains[0]) : undefined
  const subOptions = narrowable?.children?.filter((c) => c.recommendable !== false) ?? []
  const [batch, setBatch] = useState<RecommendationBatch | null>(null)
  const [items, setItems] = useState<RecommendationItem[]>([])
  const [origin, setOrigin] = useState<Origin | null>(null)
  const [picking, setPicking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  // Маршрутад сонгосон газрууд (санал болголтын id, сонгосон дарааллаар)
  const [picked, setPicked] = useState<number[]>([])

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

  // Самбар нээгдэхэд нэг удаа байршил тогтоож, хаагдахад тэмдэгтийг устгана
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
        method: 'POST',
        body: { lat: origin.lat, lng: origin.lng, availableMinutes: minutes, transportMode: mode, categories: chosenCategories },
      })
      setBatch(b)
      setItems(b.items)
      setPicked([])
      onResults(b.items)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    } finally {
      setBusy(false)
    }
  }

  const togglePick = (item: RecommendationItem) => {
    setPicked((p) => p.includes(item.recommendationId)
      ? p.filter((id) => id !== item.recommendationId)
      : p.length < MAX_STOPS ? [...p, item.recommendationId] : p)
  }

  /** Газар сонгоогүй бол сервер 2-3-ыг өөрөө сонгоно (автомат маршрут). */
  const createRoute = async (auto: boolean) => {
    if (!origin) return
    setBusy(true)
    setError(null)
    try {
      const stops = auto ? [] : picked
        .map((id) => items.find((i) => i.recommendationId === id))
        .filter((i): i is RecommendationItem => !!i)
        .map((i) => ({ placeId: i.placeId, recommendationId: i.recommendationId }))
      const route = await api<RouteView>('/api/routes', {
        method: 'POST',
        body: { lat: origin.lat, lng: origin.lng, transportMode: mode, timeBudgetMinutes: minutes, stops,
          categories: chosenCategories },
      })
      onRoute(route)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    } finally {
      setBusy(false)
    }
  }

  const reject = (item: RecommendationItem) => {
    setPicked((p) => p.filter((id) => id !== item.recommendationId))
    logInteraction(item.placeId, 'REJECT', item.recommendationId)
    const rest = items.filter((i) => i.recommendationId !== item.recommendationId)
    setItems(rest)
    onResults(rest)
  }

  const focus = (item: RecommendationItem) => {
    if (focusedId !== item.placeId) logInteraction(item.placeId, 'VIEW', item.recommendationId)
    onFocus(item)
  }

  const open = (item: RecommendationItem) => {
    logInteraction(item.placeId, 'ACCEPT', item.recommendationId)
    onOpen(item)
  }

  return (
    <BottomSheet className="panel">
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
        <label>Юу хайж байна вэ?</label>
        <div className="chips">
          <button type="button" className={`chip ${mains.length === 0 ? 'on' : ''}`}
                  onClick={() => { setMains([]); setSubs([]) }}>Бүгд</button>
          {categories.map((c) => (
            <button key={c.code} type="button" className={`chip ${mains.includes(c.code) ? 'on' : ''}`}
                    onClick={() => toggleMain(c.code)}>
              <span className="dot" style={{ background: MAIN_CATEGORY_COLORS[c.code] ?? '#888' }} /> {c.nameMn}
            </button>
          ))}
        </div>
        {subOptions.length > 1 && (
          <div className="chips sub-chips">
            {subOptions.map((c) => (
              <button key={c.code} type="button" className={`chip small ${subs.includes(c.code) ? 'on' : ''}`}
                      onClick={() => toggleSub(c.code)}>{c.nameMn}</button>
            ))}
          </div>
        )}
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
              <li key={it.recommendationId} className={focusedId === it.placeId ? 'focused' : ''}
                  ref={(el) => { if (el) itemRefs.current.set(it.placeId, el); else itemRefs.current.delete(it.placeId) }}>
                <div className="rec-main" role="button" tabIndex={0} onClick={() => focus(it)}
                     onKeyDown={(e) => { if (e.key === 'Enter') focus(it) }}>
                  <span className="rec-num" style={{ background: MAIN_CATEGORY_COLORS[it.mainCategory] ?? '#888' }}>{i + 1}</span>
                  <span className="r-main">
                    <span className="r-name">{it.name}</span>
                    <span className="r-sub">{it.categoryName} · {distanceText(it.distanceM)} · ~{it.travelMinutes} мин</span>
                    <span className={`rec-reason ${it.exploration ? 'explore' : ''}`}>{it.reason}</span>
                    {focusedId === it.placeId && (
                      <span className="rec-more">
                        <button type="button" className="btn small-btn secondary"
                                onClick={(e) => { e.stopPropagation(); open(it) }}>Дэлгэрэнгүй</button>
                      </span>
                    )}
                  </span>
                </div>
                <button type="button" className={`rec-add ${picked.includes(it.recommendationId) ? 'on' : ''}`}
                        title={picked.includes(it.recommendationId) ? 'Маршрутаас хасах' : 'Маршрутад нэмэх'}
                        disabled={!picked.includes(it.recommendationId) && picked.length >= MAX_STOPS}
                        onClick={() => togglePick(it)}>
                  {picked.includes(it.recommendationId) ? picked.indexOf(it.recommendationId) + 1 : '+'}
                </button>
                <button type="button" className="rec-x" title="Сонирхохгүй" onClick={() => reject(it)}>✕</button>
              </li>
            ))}
          </ol>
          {items.length > 0 && (
            <div className="route-bar">
              <span className="muted" style={{ fontSize: 12 }}>
                {picked.length === 0
                  ? '“+” дарж маршрутад 1–4 газар сонгох, эсвэл автоматаар гаргах'
                  : `${picked.length} газар сонгосон (дээд тал нь ${MAX_STOPS})`}
              </span>
              <div className="row">
                <button className="btn secondary" disabled={busy} onClick={() => createRoute(true)}>Автомат маршрут</button>
                <button className="btn" disabled={busy || picked.length === 0} onClick={() => createRoute(false)}>
                  Маршрут үүсгэх{picked.length > 0 ? ` (${picked.length})` : ''}
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </BottomSheet>
  )
}
