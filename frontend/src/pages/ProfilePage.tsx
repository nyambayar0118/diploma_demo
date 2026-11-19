import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, ApiError, REQUEST_STATUS_LABELS, REQUEST_TYPE_LABELS, type Category, type ChangeRequest, type Consent,
  type InterestWeight, type Preferences, type Rewards, type TransportMode } from '../api'
import { useAuth } from '../auth'
import { BUDGET_LABELS, CONSENT_LABELS, TRANSPORT_LABELS } from '../categoryLabels'
import PageHeader from '../components/PageHeader'
import { MAIN_CATEGORY_COLORS } from '../places'

const ROLE_LABELS: Record<string, string> = { USER: 'Хэрэглэгч', MODERATOR: 'Модератор', ADMIN: 'Админ' }
const TRANSPORT_ICONS: Record<string, string> = { WALKING: '🚶', PUBLIC_TRANSPORT: '🚌', DRIVING: '🚗', BICYCLE: '🚲' }

const hours = (min: number) => (min % 60 === 0 ? `${min / 60} цаг` : `${Math.floor(min / 60)} цаг ${min % 60} мин`)

/** Settings (ХШ03-ХШ08): recommendation preferences (saved automatically), interests, privacy, contributions, account. */
export default function ProfilePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [prefs, setPrefs] = useState<Preferences | null>(null)
  const [saveState, setSaveState] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const [consents, setConsents] = useState<Consent[]>([])
  const [interests, setInterests] = useState<InterestWeight[]>([])
  const [names, setNames] = useState<Record<string, string>>({})
  const [requests, setRequests] = useState<ChangeRequest[]>([])
  const [rewards, setRewards] = useState<Rewards | null>(null)
  const saveTimer = useRef<number | undefined>(undefined)
  const loaded = useRef(false)

  useEffect(() => {
    api<Preferences>('/api/users/me/preferences').then((p) => {
      setPrefs(p)
      loaded.current = true
    })
    api<Consent[]>('/api/consents').then(setConsents)
    api<InterestWeight[]>('/api/users/me/interests').then(setInterests)
    api<Category[]>('/api/categories').then((cats) => {
      const map: Record<string, string> = {}
      cats.forEach((m) => m.children?.forEach((c) => (map[c.code] = c.nameMn)))
      setNames(map)
    })
    api<ChangeRequest[]>('/api/users/me/change-requests').then(setRequests).catch(() => undefined)
    api<Rewards>('/api/users/me/rewards').then(setRewards).catch(() => undefined)
  }, [])

  // Preferences are saved automatically, shortly after the last change
  const update = (next: Preferences) => {
    setPrefs(next)
    setSaveState('saving')
    window.clearTimeout(saveTimer.current)
    saveTimer.current = window.setTimeout(async () => {
      try {
        await api<Preferences>('/api/users/me/preferences', { method: 'PUT', body: next })
        setSaveState('saved')
      } catch {
        setSaveState('error')
      }
    }, 600)
  }

  const toggleMode = (mode: TransportMode) => {
    if (!prefs) return
    const has = prefs.transportModes.includes(mode)
    const modes = has ? prefs.transportModes.filter((m) => m !== mode) : [...prefs.transportModes, mode]
    if (modes.length > 0) update({ ...prefs, transportModes: modes })
  }

  const toggleConsent = async (c: Consent) => {
    setConsents(await api<Consent[]>(`/api/consents/${c.dataType}`, { method: c.granted ? 'DELETE' : 'POST' }))
  }

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

  if (!user) return null
  const top = [...interests].sort((a, b) => b.weight - a.weight).filter((w) => w.weight >= 0.3).slice(0, 12)

  return (
    <div className="page settings">
      <PageHeader title="Тохиргоо">
        {saveState === 'saving' && <span className="save-state">Хадгалж байна…</span>}
        {saveState === 'saved' && <span className="save-state ok">✓ Хадгалагдсан</span>}
        {saveState === 'error' && <span className="save-state bad">Хадгалж чадсангүй</span>}
      </PageHeader>

      <section className="s-card profile-card">
        <span className="avatar big">{user.displayName.trim().charAt(0).toUpperCase()}</span>
        <div className="r-main">
          <div className="profile-name">{user.displayName}</div>
          <div className="muted">@{user.username} · {user.email}</div>
          <div className="profile-tags">
            {user.role !== 'USER' && <span className="badge status-created">{ROLE_LABELS[user.role]}</span>}
            {rewards && <span className="badge status-completed">{rewards.total} оноо</span>}
          </div>
        </div>
      </section>

      <section className="s-card">
        <h2>Санал болголт</h2>
        <p className="s-desc">Санал авах бүрт эдгээрийг анхны утга болгон ашиглана. Өөрчлөхөд шууд хадгалагдана.</p>
        {prefs && (
          <>
            <div className="s-field">
              <div className="s-label">Тээврийн хэрэгсэл</div>
              <div className="seg wrap">
                {(Object.keys(TRANSPORT_LABELS) as TransportMode[]).map((m) => (
                  <button type="button" key={m} className={prefs.transportModes.includes(m) ? 'on' : ''}
                          onClick={() => toggleMode(m)}>
                    <span aria-hidden>{TRANSPORT_ICONS[m]}</span> {TRANSPORT_LABELS[m]}
                  </button>
                ))}
              </div>
            </div>
            <div className="s-field">
              <div className="s-label">Төсөв</div>
              <div className="seg">
                {Object.entries(BUDGET_LABELS).map(([k, v]) => (
                  <button type="button" key={k} className={prefs.budgetLevel === k ? 'on' : ''}
                          onClick={() => update({ ...prefs, budgetLevel: k as Preferences['budgetLevel'] })}>{v}</button>
                ))}
              </div>
            </div>
            <div className="s-field">
              <div className="s-label">Боломжит хугацаа <strong>{hours(prefs.timeBudgetMin)}</strong></div>
              <input type="range" min={30} max={480} step={30} value={prefs.timeBudgetMin}
                     onChange={(e) => update({ ...prefs, timeBudgetMin: Number(e.target.value) })} />
            </div>
            <div className="s-field">
              <div className="s-label">Хайх радиус <strong>{(prefs.searchRadiusM / 1000).toFixed(1)} км</strong></div>
              <input type="range" min={500} max={20000} step={500} value={prefs.searchRadiusM}
                     onChange={(e) => update({ ...prefs, searchRadiusM: Number(e.target.value) })} />
            </div>
            <div className="s-field">
              <div className="s-label">Маршрутын зогсоол <strong>{prefs.stopCount}</strong></div>
              <input type="range" min={1} max={4} step={1} value={prefs.stopCount}
                     onChange={(e) => update({ ...prefs, stopCount: Number(e.target.value) })} />
            </div>
          </>
        )}
      </section>

      <section className="s-card">
        <div className="row">
          <h2>Сонирхол</h2>
          <Link to="/onboarding" className="btn secondary small-btn">Дахин сонгох</Link>
        </div>
        <p className="s-desc">Таны үйлдлээс (хадгалах, очих, татгалзах) аажмаар өөрчлөгдөнө.</p>
        {top.length === 0 && <p className="muted">Сонирхол сонгоогүй байна.</p>}
        <ul className="interest-bars">
          {top.map((w) => (
            <li key={w.category}>
              <span className="ib-name">{names[w.category] ?? w.category}</span>
              <span className="ib-track">
                <span className="ib-fill" style={{ width: `${Math.round(w.weight * 100)}%`,
                  background: MAIN_CATEGORY_COLORS[w.mainCategory] ?? 'var(--c-primary)' }} />
              </span>
            </li>
          ))}
        </ul>
      </section>

      <section className="s-card">
        <h2>Нууцлал</h2>
        <p className="s-desc">Аль мэдээллийг ашиглахыг та шийднэ. Унтраахад тухайн мэдээллийг дахин цуглуулахгүй.</p>
        {consents.map((c) => (
          <label className="switch-row" key={c.dataType}>
            <span className="r-main">
              <span className="t-title">{CONSENT_LABELS[c.dataType] ?? c.dataType}</span>
              <span className="t-desc">{c.purpose} · {c.retentionDays} хоног хадгална</span>
            </span>
            <input type="checkbox" className="switch" checked={c.granted} onChange={() => toggleConsent(c)} />
          </label>
        ))}
      </section>

      <section className="s-card">
        <h2>Миний хувь нэмэр</h2>
        {requests.length === 0 ? (
          <p className="s-desc">Цэснээс “Газар нэмэх”, эсвэл газрын мэдээлэл дээр “Засвар санал болгох” дарж оноо цуглуулаарай.</p>
        ) : (
          <ul className="friend-list">
            {requests.map((r) => (
              <li key={r.id}>
                <span className="r-main">
                  <span className="r-name">{REQUEST_TYPE_LABELS[r.type]}: {r.target?.name ?? r.data.nameCyrillic ?? ''}</span>
                  <span className="r-sub">
                    {new Date(r.submittedAt).toLocaleDateString('mn-MN')}
                    {r.moderatorComment ? ` · ${r.moderatorComment}` : ''}
                  </span>
                </span>
                <span className={`badge req-${r.status.toLowerCase()}`}>
                  {REQUEST_STATUS_LABELS[r.status]}{r.pointsGranted ? ` +${r.pointsGranted}` : ''}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="s-card">
        <h2>Бүртгэл</h2>
        <button className="btn secondary block" onClick={() => { logout(); navigate('/', { replace: true }) }}>Гарах</button>
        <details className="danger-zone">
          <summary>Бүртгэл устгах</summary>
          <p className="s-desc">
            Таны имэйл, нэр, сонирхол, хадгалсан газар, зөвшөөрөл, маршрутын эхлэх цэгүүд устгагдана. Үйлдлийн түүх нэргүй
            болж зөвхөн судалгааны өгөгдөл болон үлдэнэ.
          </p>
          <button className="btn danger block" onClick={deleteAccount}>Бүртгэлээ бүрмөсөн устгах</button>
        </details>
      </section>
    </div>
  )
}
