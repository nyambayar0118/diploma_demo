import { useState } from 'react'
import { api, ApiError, clockTime, durationText, type MapsLink, type RouteView } from '../api'
import { TRANSPORT_LABELS } from '../categoryLabels'
import { MAIN_CATEGORY_COLORS } from '../places'

type Props = {
  route: RouteView
  onChange: (route: RouteView | null) => void
  onSelectPlace: (placeId: number) => void
  onClose: () => void
}

const STATUS_LABELS: Record<RouteView['status'], string> = {
  CREATED: 'Төлөвлөсөн',
  OPENED_IN_MAPS: 'Явж байна',
  COMPLETED: 'Дууссан',
  CANCELLED: 'Цуцалсан',
}

/** Route view (ХХШ05): ordered stops with arrival times, Google Maps hand-over, completion and feedback. */
export default function RoutePanel({ route, onChange, onSelectPlace, onClose }: Props) {
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const call = async (fn: () => Promise<RouteView | null>) => {
    setBusy(true)
    setError(null)
    try {
      onChange(await fn())
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    } finally {
      setBusy(false)
    }
  }

  const removeStop = (stopId: number) => call(() => api<RouteView>(`/api/routes/${route.id}/stops`, {
    method: 'PATCH', body: { op: 'REMOVE', stopId },
  }))

  // window.open must run inside the click handler or the browser blocks it as a pop-up
  const openLink = (link: MapsLink) => {
    window.open(link.url, '_blank', 'noopener')
    if (route.status === 'CREATED') {
      call(() => api<RouteView>(`/api/routes/${route.id}/open`, { method: 'POST' }))
    }
  }

  const complete = () => call(() => api<RouteView>(`/api/routes/${route.id}/complete`, { method: 'POST' }))

  const cancel = () => {
    if (!window.confirm('Маршрутыг цуцлах уу?')) return
    call(async () => {
      await api(`/api/routes/${route.id}`, { method: 'DELETE' })
      return { ...route, status: 'CANCELLED' }
    })
  }

  const active = route.status === 'CREATED' || route.status === 'OPENED_IN_MAPS'
  const transit = route.transportMode === 'PUBLIC_TRANSPORT'
  const legName = (i: number) => (i === 0 ? 'Эхлэх цэг' : route.stops[i - 1]?.name)

  return (
    <aside className="panel">
      <button className="sheet-close" onClick={onClose} aria-label="Хаах">×</button>
      <div className="sheet-cat">
        <span className={`badge status-${route.status.toLowerCase()}`}>{STATUS_LABELS[route.status]}</span>
      </div>
      <h2 className="sheet-title">Маршрут</h2>
      <p className="muted" style={{ margin: '0 0 4px' }}>
        {TRANSPORT_LABELS[route.transportMode]} · {clockTime(route.plannedStart)}-с · нийт ~{durationText(route.estimatedDurationMinutes)}
      </p>
      {route.overBudget && (
        <p className="hint warn" style={{ margin: 0 }}>
          Таны боломжит хугацаа ({durationText(route.timeBudgetMinutes)})-аас урт байна. Зогсоол хасаж болно.
        </p>
      )}

      <ol className="route-stops">
        {route.stops.map((s) => (
          <li key={s.stopId} className={s.visited || s.skipped ? 'done' : ''}>
            <div className="route-leg">{s.travelMinutes} мин зам</div>
            <div className="route-stop">
              <span className="rec-num" style={{ background: s.visited || s.skipped ? '#adb5bd' : MAIN_CATEGORY_COLORS[s.mainCategory] ?? '#888' }}>
                {s.visited ? '✓' : s.order}
              </span>
              <button type="button" className="rec-main" onClick={() => onSelectPlace(s.placeId)}>
                <span className="r-main">
                  <span className="r-name">{s.name}</span>
                  <span className="r-sub">
                    {s.categoryName} · {clockTime(s.estimatedArrival)} ирэх · ~{s.stayMinutes} мин байх
                  </span>
                  {s.openAtArrival === 'CLOSED' && active && (
                    <span className="hint warn">Ирэх үед хаалттай байж магадгүй</span>
                  )}
                  {s.visited && <span className="rec-reason">Очсон</span>}
                  {s.skipped && <span className="r-sub">Очоогүй</span>}
                </span>
              </button>
              {route.status === 'CREATED' && route.stops.length > 1 && (
                <button type="button" className="rec-x" title="Хасах" disabled={busy} onClick={() => removeStop(s.stopId)}>✕</button>
              )}
            </div>
          </li>
        ))}
      </ol>

      {route.status === 'CREATED' && route.stops.length < 4 && (
        <p className="hint" style={{ fontSize: 12, color: 'var(--c-muted)' }}>
          Газрын зураг дээрх газрыг сонгоод “Маршрутад нэмэх” дарж зогсоол нэмнэ (дээд тал нь 4).
        </p>
      )}

      {error && <div className="error">{error}</div>}

      {active && (
        <div className="route-actions">
          {transit ? (
            <>
              <p className="muted" style={{ margin: 0 }}>
                Google Maps нийтийн тээврийн чиглэлд дамжих цэг дэмждэггүй тул хэсэг тус бүрийг тусад нь нээнэ:
              </p>
              {route.mapsLinks.map((l) => (
                <button key={l.legIndex} className="btn secondary block" onClick={() => openLink(l)}>
                  {l.legIndex + 1}. {legName(l.legIndex)} → {route.stops[l.legIndex]?.name}
                </button>
              ))}
            </>
          ) : (
            route.mapsLinks[0] && (
              <button className="btn block" onClick={() => openLink(route.mapsLinks[0])}>Google Maps-ээр нээх</button>
            )
          )}
          {route.status === 'OPENED_IN_MAPS' && (
            <button className="btn secondary block" disabled={busy} onClick={complete}>Маршрут дуусгах</button>
          )}
          <button className="btn danger block" disabled={busy} onClick={cancel}>Цуцлах</button>
        </div>
      )}

      {route.status === 'COMPLETED' && (
        route.feedbackGiven
          ? <div className="success" style={{ marginTop: 14 }}>Үнэлгээ өгсөнд баярлалаа!</div>
          : <FeedbackForm routeId={route.id} onDone={() => onChange({ ...route, feedbackGiven: true })} />
      )}
    </aside>
  )
}

const QUESTIONS = [
  { key: 'relevance', text: 'Санал болгосон газрууд таны сонирхолд хэр нийцсэн бэ?' },
  { key: 'timeSuitability', text: 'Маршрут таны боломжит хугацаанд хэр тохирсон бэ?' },
  { key: 'estimateAccuracy', text: 'Зорчих болон ирэх хугацааны тооцоо хэр бодит байсан бэ?' },
] as const

type Answers = Partial<Record<(typeof QUESTIONS)[number]['key'], number>>

function FeedbackForm({ routeId, onDone }: { routeId: number; onDone: () => void }) {
  const [answers, setAnswers] = useState<Answers>({})
  const [error, setError] = useState<string | null>(null)
  const complete = QUESTIONS.every((q) => answers[q.key] != null)

  const submit = async () => {
    setError(null)
    try {
      await api(`/api/routes/${routeId}/feedback`, { method: 'POST', body: answers })
      onDone()
    } catch (err) {
      if (err instanceof ApiError && err.code === 'FEEDBACK_EXISTS') return onDone()
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    }
  }

  return (
    <div className="feedback">
      <h3>Маршрутаа үнэлнэ үү</h3>
      {QUESTIONS.map((q) => (
        <div key={q.key} className="feedback-q">
          <div>{q.text}</div>
          <div className="scale">
            {[1, 2, 3, 4, 5].map((v) => (
              <button key={v} type="button" className={`chip ${answers[q.key] === v ? 'on' : ''}`}
                      onClick={() => setAnswers({ ...answers, [q.key]: v })}>{v}</button>
            ))}
          </div>
        </div>
      ))}
      <div className="scale-legend"><span>1 – Огт үгүй</span><span>5 – Маш сайн</span></div>
      {error && <div className="error">{error}</div>}
      <button className="btn block" disabled={!complete} onClick={submit}>Илгээх</button>
    </div>
  )
}
