import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, ApiError, currentPosition, type JoinInfo, type JoinResult, type TransportMode } from '../api'
import { useAuth } from '../auth'
import { TRANSPORT_LABELS } from '../categoryLabels'

/**
 * Opened from a group invite link (АШ24). No account needed: a guest gives a name, optionally their position,
 * and gets their own Google Maps link to the meeting place. The result is remembered on this device.
 */
export default function JoinPage() {
  const { token = '' } = useParams()
  const { user } = useAuth()
  const storageKey = `routeplanner:join:${token}`
  const [info, setInfo] = useState<JoinInfo | null>(null)
  const [result, setResult] = useState<JoinResult | null>(() => {
    try {
      const saved = localStorage.getItem(storageKey)
      return saved ? JSON.parse(saved) : null
    } catch {
      return null
    }
  })
  const [name, setName] = useState('')
  const [mode, setMode] = useState<TransportMode>('WALKING')
  const [useLocation, setUseLocation] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api<JoinInfo>(`/api/groups/join/${token}`).then(setInfo)
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Сервертэй холбогдсонгүй'))
  }, [token])

  const join = async () => {
    setBusy(true)
    setError(null)
    try {
      const pos = useLocation ? await currentPosition() : null
      const r = await api<JoinResult>(`/api/groups/join/${token}`, {
        method: 'POST',
        body: { guestName: user ? undefined : name, transportMode: mode, lat: pos?.lat, lng: pos?.lng },
      })
      setResult(r)
      try {
        localStorage.setItem(storageKey, JSON.stringify(r))
      } catch {
        /* private mode: just not remembered */
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <div className="card">
        <h1>Уулзалтын урилга</h1>
        {error && <div className="error">{error}</div>}
        {info && !info.valid && !result && <p className="muted">Энэ урилгын хугацаа дууссан эсвэл цуцлагдсан байна.</p>}
        {info?.meetingPlace && (
          <p className="lead">
            <strong>{info.initiatorName}</strong> таныг <strong>{info.meetingPlace.name}</strong> ({info.meetingPlace.categoryName})
            дээр уулзахыг урьж байна · {info.memberCount} хүн
          </p>
        )}

        {result ? (
          <>
            <a className="btn block" href={result.mapsUrl} target="_blank" rel="noopener noreferrer">Google Maps-ээр очих</a>
            {result.travelMinutes != null && <p className="muted" style={{ textAlign: 'center' }}>Танд ~{result.travelMinutes} мин</p>}
            {user && result.routeId && <p className="muted" style={{ textAlign: 'center' }}>Маршрут “Миний маршрут”-д нэмэгдлээ.</p>}
          </>
        ) : info?.valid && (
          <>
            {!user && (
              <div className="field">
                <label htmlFor="j-name">Таны нэр</label>
                <input id="j-name" maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
              </div>
            )}
            <div className="field">
              <label htmlFor="j-mode">Тээврийн хэрэгсэл</label>
              <select id="j-mode" value={mode} onChange={(e) => setMode(e.target.value as TransportMode)}>
                {Object.entries(TRANSPORT_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </div>
            <label className="toggle">
              <input type="checkbox" checked={useLocation} onChange={(e) => setUseLocation(e.target.checked)} />
              <span>
                <div className="t-title">Миний байршлыг ашиглах</div>
                <div className="t-desc">Очих хугацааг тооцоход л ашиглана. Ашиглахгүй бол Google Maps таны одоогийн байршлаас чиглэл гаргана.</div>
              </span>
            </label>
            <button className="btn block" disabled={busy || (!user && !name.trim())} onClick={join}>
              {busy ? 'Түр хүлээнэ үү…' : user ? 'Нэгдэх' : 'Зочноор нэгдэх'}
            </button>
            {!user && (
              <p className="muted" style={{ textAlign: 'center', marginTop: 12 }}>
                Бүртгэлтэй юу? <Link to={`/login?next=${encodeURIComponent(`/join/${token}`)}`}>Нэвтрэх</Link>
              </p>
            )}
          </>
        )}
      </div>
    </div>
  )
}
