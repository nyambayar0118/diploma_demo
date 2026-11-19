import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, ApiError, REQUEST_STATUS_LABELS, REQUEST_TYPE_LABELS, type ChangeRequest, type Rewards, type Category, type Consent, type InterestWeight, type PlaceSummary, type Preferences, type TransportMode } from '../api'
import { useAuth } from '../auth'
import { BUDGET_LABELS, CONSENT_LABELS, TRANSPORT_LABELS } from '../categoryLabels'

type Health = { status: string; database?: string; ml?: string }

export default function ProfilePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const deleteAccount = async () => {
    if (!window.confirm('Бүртгэлээ бүрмөсөн устгах уу? Энэ үйлдлийг буцаах боломжгүй.')) return
    try {
      await api('/api/users/me', { method: 'DELETE' })
      logout()
      navigate('/', { replace: true })
    } catch (err) {
      window.alert(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    }
  }
  const [prefs, setPrefs] = useState<Preferences | null>(null)
  const [requests, setRequests] = useState<ChangeRequest[]>([])
  const [rewards, setRewards] = useState<Rewards | null>(null)
  useEffect(() => {
    api<ChangeRequest[]>('/api/users/me/change-requests').then(setRequests).catch(() => undefined)
    api<Rewards>('/api/users/me/rewards').then(setRewards).catch(() => undefined)
  }, [])
  const [consents, setConsents] = useState<Consent[]>([])
  const [interests, setInterests] = useState<InterestWeight[]>([])
  const [names, setNames] = useState<Record<string, string>>({})
  const [health, setHealth] = useState<Health | null>(null)
  const [saved, setSaved] = useState<PlaceSummary[]>([])
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null)

  useEffect(() => {
    api<Preferences>('/api/users/me/preferences').then(setPrefs)
    api<Consent[]>('/api/consents').then(setConsents)
    api<InterestWeight[]>('/api/users/me/interests').then(setInterests)
    api<Category[]>('/api/categories').then((cats) => {
      const map: Record<string, string> = {}
      cats.forEach((m) => m.children?.forEach((c) => (map[c.code] = c.nameMn)))
      setNames(map)
    })
    api<Health>('/api/health').then(setHealth).catch(() => setHealth(null))
    api<PlaceSummary[]>('/api/users/me/saved').then(setSaved)
  }, [])

  const savePrefs = async () => {
    if (!prefs) return
    try {
      setPrefs(await api<Preferences>('/api/users/me/preferences', { method: 'PUT', body: prefs }))
      setMessage({ ok: true, text: 'Тохиргоо хадгалагдлаа' })
    } catch (err) {
      setMessage({ ok: false, text: err instanceof ApiError ? err.message : 'Хадгалж чадсангүй' })
    }
  }

  const toggleConsent = async (c: Consent) => {
    setConsents(await api<Consent[]>(`/api/consents/${c.dataType}`, { method: c.granted ? 'DELETE' : 'POST' }))
  }

  const toggleMode = (mode: TransportMode) => {
    if (!prefs) return
    const has = prefs.transportModes.includes(mode)
    const modes = has ? prefs.transportModes.filter((m) => m !== mode) : [...prefs.transportModes, mode]
    if (modes.length > 0) setPrefs({ ...prefs, transportModes: modes })
  }

  if (!user) return null

  return (
    <div className="page">
      <div className="card wide">
        <div className="row">
          <div>
            <h1>{user.displayName}</h1>
            <p className="lead" style={{ margin: 0 }}>@{user.username} · {user.email}</p>
          </div>
          <span className="row" style={{ gap: 8 }}>
            <Link className="btn secondary" to="/friends">Найзууд</Link>
            <Link className="btn secondary" to="/groups">Бүлэг</Link>
            <Link className="btn secondary" to="/">Газрын зураг</Link>
          </span>
        </div>

        {message && <div className={message.ok ? 'success' : 'error'} style={{ marginTop: 16 }}>{message.text}</div>}

        <h2>Тохиргоо</h2>
        {prefs && (
          <>
            <div className="field">
              <label>Тээврийн хэрэгсэл</label>
              <div className="chips">
                {(Object.keys(TRANSPORT_LABELS) as TransportMode[]).map((m) => (
                  <button type="button" key={m} className={`chip ${prefs.transportModes.includes(m) ? 'on' : ''}`}
                          onClick={() => toggleMode(m)}>{TRANSPORT_LABELS[m]}</button>
                ))}
              </div>
            </div>
            <div className="field">
              <label htmlFor="budget">Төсөв</label>
              <select id="budget" value={prefs.budgetLevel}
                      onChange={(e) => setPrefs({ ...prefs, budgetLevel: e.target.value as Preferences['budgetLevel'] })}>
                {Object.entries(BUDGET_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select>
            </div>
            <div className="field">
              <label htmlFor="radius">Хайх радиус: {(prefs.searchRadiusM / 1000).toFixed(1)} км</label>
              <input id="radius" type="range" min={500} max={20000} step={500} value={prefs.searchRadiusM}
                     onChange={(e) => setPrefs({ ...prefs, searchRadiusM: Number(e.target.value) })} />
            </div>
            <div className="field">
              <label htmlFor="time">Боломжит хугацаа: {Math.floor(prefs.timeBudgetMin / 60)} цаг {prefs.timeBudgetMin % 60} мин</label>
              <input id="time" type="range" min={30} max={480} step={30} value={prefs.timeBudgetMin}
                     onChange={(e) => setPrefs({ ...prefs, timeBudgetMin: Number(e.target.value) })} />
            </div>
            <div className="field">
              <label htmlFor="stops">Зогсоолын тоо: {prefs.stopCount}</label>
              <input id="stops" type="range" min={1} max={4} step={1} value={prefs.stopCount}
                     onChange={(e) => setPrefs({ ...prefs, stopCount: Number(e.target.value) })} />
            </div>
            <button className="btn" onClick={savePrefs}>Хадгалах</button>
          </>
        )}

        <h2>Сонирхол</h2>
        <div className="chips">
          {interests.filter((w) => w.weight >= 0.5).map((w) => (
            <span key={w.category} className="chip" title={`жин ${w.weight.toFixed(2)}`}>
              {names[w.category] ?? w.category} · {w.weight.toFixed(2)}
            </span>
          ))}
        </div>
        <p className="muted"><Link to="/onboarding">Сонирхлоо дахин сонгох</Link></p>

        <h2>Хадгалсан газрууд</h2>
        {saved.length === 0 && <p className="muted">Газрын мэдээллээс “Хадгалах” дарж нэмнэ.</p>}
        <div className="chips">
          {saved.map((p) => (
            <Link key={p.id} className="chip" to={`/?place=${p.id}`}>{p.name}</Link>
          ))}
        </div>

        <h2>Миний хувь нэмэр {rewards ? `· ${rewards.total} оноо` : ''}</h2>
        {requests.length === 0 ? (
          <p className="muted">Газрын зураг дээр “＋” дарж шинэ газар нэмэх, эсвэл газрын мэдээлэл дээр “Засвар санал болгох” дарж
            оноо цуглуулаарай.</p>
        ) : (
          <ul className="friend-list">
            {requests.map((r) => (
              <li key={r.id}>
                <span className="r-main">
                  <span className="r-name">{REQUEST_TYPE_LABELS[r.type]}: {r.target?.name ?? r.data.nameCyrillic ?? ''}</span>
                  <span className="r-sub">
                    {REQUEST_STATUS_LABELS[r.status]}{r.pointsGranted ? ` · +${r.pointsGranted} оноо` : ''}
                    {r.moderatorComment ? ` · ${r.moderatorComment}` : ''}
                  </span>
                </span>
              </li>
            ))}
          </ul>
        )}
        {(user?.role === 'MODERATOR' || user?.role === 'ADMIN') && (
          <p className="row" style={{ justifyContent: 'flex-start' }}>
            <Link className="btn secondary" to="/moderation">Хүсэлт хянах (модератор)</Link>
            {user?.role === 'ADMIN' && <Link className="btn secondary" to="/admin">Хяналтын самбар</Link>}
          </p>
        )}

        <h2>Хувийн мэдээллийн зөвшөөрөл</h2>
        {consents.map((c) => (
          <label className="toggle" key={c.dataType}>
            <input type="checkbox" checked={c.granted} onChange={() => toggleConsent(c)} />
            <span>
              <div className="t-title">{CONSENT_LABELS[c.dataType] ?? c.dataType}</div>
              <div className="t-desc">{c.purpose} · {c.retentionDays} хоног хадгална</div>
            </span>
          </label>
        ))}

        <div className="row" style={{ marginTop: 28 }}>
          <span className="muted">
            Систем: {health ? `API ${health.status} · DB ${health.database ?? '-'} · ML ${health.ml ?? '-'}` : 'холбогдсонгүй'}
          </span>
          <button className="btn danger" onClick={() => { logout(); navigate('/', { replace: true }) }}>Гарах</button>
        </div>

        <h2>Бүртгэл устгах</h2>
        <p className="muted">
          Таны имэйл, нэр, сонирхол, хадгалсан газар, зөвшөөрөл, маршрутын эхлэх цэгүүд устгагдана. Үйлдлийн түүх
          хэнийх болох нь мэдэгдэхгүй болж зөвхөн судалгааны нэргүй өгөгдөл болон үлдэнэ.
        </p>
        <button className="btn danger" onClick={deleteAccount}>Бүртгэлээ бүрмөсөн устгах</button>
      </div>
    </div>
  )
}
