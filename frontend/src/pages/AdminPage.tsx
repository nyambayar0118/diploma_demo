import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api, ApiError } from '../api'

type Component = { name: string; up: boolean; latencyMs: number | null; detail: string }
type Health = { up: boolean; checkedAt: string; components: Component[]; availability24h: number; checks24h: number; startedAt: string }
type Endpoint = {
  endpoint: string; count: number; clientErrors: number; serverErrors: number; p50Ms: number; p95Ms: number; maxMs: number
  targetP95Ms: number | null
}
type Row = Record<string, number | string | null>
type Metrics = {
  windowMinutes: number
  emailAlerts: boolean
  endpoints: Endpoint[]
  usage: {
    users: Row; activeUsers: Row; recommendations: Row; visits: Row; satisfaction: Row; moderation: Row; places: Row
    interactions24h: Row[]; interactionsTotal: Row[]; routes: Row[]; groups: Row[]
  }
}
type AdminUser = { id: number; email: string; username: string; display_name: string; role: string; auth_provider: string }

function Stat({ label, value, sub }: { label: string; value: React.ReactNode; sub?: string }) {
  return (
    <div className="stat">
      <div className="stat-value">{value ?? '—'}</div>
      <div className="stat-label">{label}</div>
      {sub && <div className="stat-sub">{sub}</div>}
    </div>
  )
}

const n = (v: unknown) => (v == null ? '—' : String(v))
const list = (rows: Row[], key: string) => rows.map((r) => `${r[key]}: ${r.n}`).join(' · ') || '—'

/** Admin dashboard (СНШ05, СНШ07): component health, response times vs targets, errors, trial usage, roles. */
export default function AdminPage() {
  const [health, setHealth] = useState<Health | null>(null)
  const [metrics, setMetrics] = useState<Metrics | null>(null)
  const [minutes, setMinutes] = useState(60)
  const [q, setQ] = useState('')
  const [users, setUsers] = useState<AdminUser[]>([])
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    api<Health>('/api/admin/health').then(setHealth).catch((e) => setError(e instanceof ApiError ? e.message : 'Алдаа'))
    api<Metrics>(`/api/admin/metrics?minutes=${minutes}`).then(setMetrics).catch(() => undefined)
  }, [minutes])

  useEffect(() => {
    load()
    const t = window.setInterval(load, 30_000)
    return () => window.clearInterval(t)
  }, [load])

  const searchUsers = () => api<AdminUser[]>(`/api/admin/users?q=${encodeURIComponent(q)}`).then(setUsers).catch(() => undefined)
  useEffect(() => {
    searchUsers()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const setRole = async (u: AdminUser, role: string) => {
    await api(`/api/admin/users/${u.id}/role`, { method: 'PUT', body: { role } })
    searchUsers()
  }

  const u = metrics?.usage
  return (
    <div className="page">
      <div className="card wide admin">
        <div className="row">
          <h1>Хяналтын самбар</h1>
          <Link className="btn secondary" to="/">Газрын зураг</Link>
        </div>
        {error && <div className="error">{error}</div>}

        <h2>Бүрэлдэхүүн хэсгүүд</h2>
        <div className="stats">
          {health?.components.map((c) => (
            <Stat key={c.name} label={c.name} value={<span className={c.up ? 'ok' : 'bad'}>{c.up ? 'UP' : 'DOWN'}</span>}
                  sub={`${c.detail}${c.latencyMs != null ? ` · ${c.latencyMs} мс` : ''}`} />
          ))}
          <Stat label="Ашиглах боломж (24 цаг)" value={health ? `${(health.availability24h * 100).toFixed(1)}%` : null}
                sub={health ? `${health.checks24h} шалгалт · зорилт 95%` : undefined} />
          <Stat label="Имэйл мэдэгдэл" value={metrics ? (metrics.emailAlerts ? 'Идэвхтэй' : 'Тохируулаагүй') : null}
                sub="SMTP_HOST, ALERT_EMAIL" />
        </div>

        <div className="row">
          <h2>Хариу өгөх хугацаа</h2>
          <select value={minutes} onChange={(e) => setMinutes(Number(e.target.value))}>
            {[5, 15, 60].map((m) => <option key={m} value={m}>Сүүлийн {m} мин</option>)}
          </select>
        </div>
        <div className="table-wrap">
          <table className="metrics">
            <thead>
              <tr><th>Endpoint</th><th>Хүсэлт</th><th>p50</th><th>p95</th><th>Max</th><th>4xx</th><th>5xx</th><th>Зорилт</th></tr>
            </thead>
            <tbody>
              {metrics?.endpoints.map((e) => {
                const ok = e.targetP95Ms == null || e.count === 0 || e.p95Ms <= e.targetP95Ms
                return (
                  <tr key={e.endpoint}>
                    <td><code>{e.endpoint}</code></td>
                    <td>{e.count}</td>
                    <td>{e.p50Ms}</td>
                    <td className={ok ? '' : 'bad'}>{e.p95Ms}</td>
                    <td>{e.maxMs}</td>
                    <td>{e.clientErrors || ''}</td>
                    <td className={e.serverErrors ? 'bad' : ''}>{e.serverErrors || ''}</td>
                    <td>{e.targetP95Ms ? <span className={ok ? 'ok' : 'bad'}>≤{e.targetP95Ms} мс</span> : ''}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <p className="muted" style={{ fontSize: 12 }}>Хугацаа миллисекундээр. Сервер дахин эхлэхэд тэглэгдэнэ.</p>

        {u && (
          <>
            <h2>Туршилтын хэрэглээ</h2>
            <div className="stats">
              <Stat label="Хэрэглэгч" value={n(u.users.total)} sub={`7 хоногт +${n(u.users.new_7d)} · Google ${n(u.users.google)}`} />
              <Stat label="Идэвхтэй хэрэглэгч" value={n(u.activeUsers.day)} sub={`7 хоногт ${n(u.activeUsers.week)}`} />
              <Stat label="Санал болголт" value={n(u.recommendations.batches)}
                    sub={`24 цагт ${n(u.recommendations.batches_24h)} · сургалтад ${n(u.recommendations.training_batches)}`} />
              <Stat label="Зочилсон" value={n(u.visits.confirmed)} sub={`очоогүй ${n(u.visits.skipped)} / нийт ${n(u.visits.stops)} зогсоол`} />
              <Stat label="Сэтгэл ханамж" value={n(u.satisfaction.n)}
                    sub={`нийцэл ${n(u.satisfaction.relevance)} · хугацаа ${n(u.satisfaction.time_suitability)} · тооцоо ${n(u.satisfaction.estimate_accuracy)}`} />
              <Stat label="Хянах хүсэлт" value={n(u.moderation.pending)}
                    sub={`батлагдсан ${n(u.moderation.approved)} · сэтгэгдэл ${n(u.moderation.reviews)} (нуусан ${n(u.moderation.hidden_reviews)})`} />
              <Stat label="Газар" value={n(u.places.active)} sub={`хэрэглэгч нэмсэн ${n(u.places.user_added)}`} />
            </div>
            <p className="muted"><strong>Үйлдэл (24 цаг):</strong> {list(u.interactions24h, 'type')}</p>
            <p className="muted"><strong>Үйлдэл (нийт):</strong> {list(u.interactionsTotal, 'type')}</p>
            <p className="muted"><strong>Маршрут:</strong> {list(u.routes, 'status')} · <strong>Бүлэг:</strong> {list(u.groups, 'status')}</p>
          </>
        )}

        <h2>Хэрэглэгчийн эрх</h2>
        <form className="row" onSubmit={(e) => { e.preventDefault(); searchUsers() }}>
          <div className="field" style={{ flex: 1, margin: 0 }}>
            <input placeholder="Имэйл, нэр эсвэл хэрэглэгчийн нэр" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <button className="btn secondary">Хайх</button>
        </form>
        <ul className="friend-list">
          {users.map((x) => (
            <li key={x.id}>
              <span className="r-main">
                <span className="r-name">{x.display_name} (@{x.username})</span>
                <span className="r-sub">{x.email} · {x.auth_provider}</span>
              </span>
              <select value={x.role} onChange={(e) => setRole(x, e.target.value)}>
                <option value="USER">Хэрэглэгч</option>
                <option value="MODERATOR">Модератор</option>
                <option value="ADMIN">Админ</option>
              </select>
            </li>
          ))}
        </ul>
        <p className="muted" style={{ fontSize: 12 }}>Эрхийн өөрчлөлт тухайн хэрэглэгч дахин нэвтрэхэд хүчинтэй болно.</p>
      </div>
    </div>
  )
}
