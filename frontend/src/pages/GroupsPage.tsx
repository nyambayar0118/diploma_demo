import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import PageHeader from '../components/PageHeader'
import { api, ApiError, currentPosition, type Friendship, type GroupView, type TransportMode } from '../api'
import { TRANSPORT_LABELS } from '../categoryLabels'

export const GROUP_STATUS: Record<string, string> = {
  RECOMMENDED: 'Төлөвлөж байна',
  SHARED: 'Хуваалцсан',
  COMPLETED: 'Дууссан',
  CANCELLED: 'Цуцалсан',
}

// Ulaanbaatar centre, used when the device position is not available
const UB = { lat: 47.9186, lng: 106.9177 }

/** My group plans and a form to start a new one with friends (АШ10). */
export default function GroupsPage() {
  const navigate = useNavigate()
  const [groups, setGroups] = useState<GroupView[]>([])
  const [friends, setFriends] = useState<Friendship[]>([])
  const [picked, setPicked] = useState<number[]>([])
  const [minutes, setMinutes] = useState(180)
  const [mode, setMode] = useState<TransportMode>('WALKING')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api<GroupView[]>('/api/groups').then(setGroups).catch(() => undefined)
    api<Friendship[]>('/api/friends').then((f) => setFriends(f.filter((x) => x.direction === 'FRIEND'))).catch(() => undefined)
  }, [])

  const toggle = (id: number) => setPicked((p) => p.includes(id) ? p.filter((x) => x !== id) : [...p, id])

  const create = async () => {
    setBusy(true)
    setError(null)
    try {
      const pos = (await currentPosition()) ?? UB
      const g = await api<GroupView>('/api/groups', {
        method: 'POST',
        body: { friendIds: picked, lat: pos.lat, lng: pos.lng, transportMode: mode, timeBudgetMinutes: minutes },
      })
      navigate(`/groups/${g.id}`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <PageHeader title="Бүлгээр төлөвлөх" back="/" />
      <div className="card wide">
        <p className="lead">Найзуудаа сонгоход бүгдийн сонирхолд нийцсэн, бүгд ойролцоо хугацаанд хүрэх уулзах газрыг санал болгоно.</p>
        {error && <div className="error">{error}</div>}

        <h2>Шинэ төлөвлөгөө</h2>
        {friends.length === 0 ? (
          <p className="muted">Эхлээд <Link to="/friends">найз нэмнэ үү</Link>. Бүртгэлгүй хүмүүсийг дараа нь урилгын холбоосоор нэгтгэж болно.</p>
        ) : (
          <>
            <div className="chips" style={{ marginBottom: 12 }}>
              {friends.map((f) => (
                <button key={f.id} type="button" className={`chip ${picked.includes(f.user.id) ? 'on' : ''}`}
                        onClick={() => toggle(f.user.id)}>{f.user.displayName}</button>
              ))}
            </div>
            <div className="row" style={{ flexWrap: 'wrap', justifyContent: 'flex-start' }}>
              <div className="field" style={{ margin: 0 }}>
                <label htmlFor="g-time">Боломжит хугацаа</label>
                <select id="g-time" value={minutes} onChange={(e) => setMinutes(Number(e.target.value))}>
                  {[60, 120, 180, 240, 360].map((m) => <option key={m} value={m}>{m / 60} цаг</option>)}
                </select>
              </div>
              <div className="field" style={{ margin: 0 }}>
                <label htmlFor="g-mode">Миний тээвэр</label>
                <select id="g-mode" value={mode} onChange={(e) => setMode(e.target.value as TransportMode)}>
                  {Object.entries(TRANSPORT_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
              </div>
            </div>
            <p className="hint" style={{ fontSize: 12, color: 'var(--c-muted)' }}>
              Найзын эхлэх цэг: байршлаа хуваалцаж байвал тэр, үгүй бол таны байршил.
            </p>
            <button className="btn" disabled={busy || picked.length === 0} onClick={create}>
              {busy ? 'Үүсгэж байна…' : `Төлөвлөгөө үүсгэх (${picked.length + 1} хүн)`}
            </button>
          </>
        )}

        <h2>Миний төлөвлөгөөнүүд</h2>
        {groups.length === 0 && <p className="muted">Одоогоор алга.</p>}
        <ul className="friend-list">
          {groups.map((g) => (
            <li key={g.id}>
              <Link className="person" to={`/groups/${g.id}`} style={{ color: 'inherit', textDecoration: 'none' }}>
                <span className="r-main">
                  <span className="r-name">{g.meetingPlace?.name ?? 'Уулзах газар сонгоогүй'}</span>
                  <span className="r-sub">
                    {g.members.map((m) => m.name).join(', ')} · {new Date(g.createdAt).toLocaleDateString('mn-MN')}
                  </span>
                </span>
              </Link>
              <span className={`badge status-${g.status === 'SHARED' ? 'opened_in_maps' : g.status === 'RECOMMENDED' ? 'created' : g.status.toLowerCase()}`}>
                {GROUP_STATUS[g.status]}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
