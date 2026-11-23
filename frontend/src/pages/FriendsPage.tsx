import { useEffect, useState, type FormEvent } from 'react'
import PageHeader from '../components/PageHeader'
import Avatar from '../components/Avatar'
import Icon from '../components/Icon'
import { agoText, api, ApiError, type Consent, type FriendLocation, type Friendship, type MyLocation } from '../api'
import { useAuth } from '../auth'
import { SHARING_CHANGED } from '../locationSharing'

/** Найз (АШ20) болон байршил хуваалцах (АШ21, ХШ07). */
export default function FriendsPage() {
  const { user } = useAuth()
  const [friends, setFriends] = useState<Friendship[]>([])
  const [friendLocations, setFriendLocations] = useState<FriendLocation[]>([])
  const [username, setUsername] = useState('')
  const [invite, setInvite] = useState<string | null>(null)
  const [copied, setCopied] = useState<'invite' | 'username' | null>(null)
  const [location, setLocation] = useState<MyLocation | null>(null)
  const [sharingConsent, setSharingConsent] = useState(false)
  const [sharingBusy, setSharingBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const load = () => {
    api<Friendship[]>('/api/friends').then(setFriends).catch(() => undefined)
    api<FriendLocation[]>('/api/friends/locations').then(setFriendLocations).catch(() => undefined)
    api<MyLocation>('/api/locations/me').then(setLocation).catch(() => undefined)
    api<Consent[]>('/api/consents')
      .then((cs) => setSharingConsent(cs.some((c) => c.dataType === 'LOCATION_SHARING' && c.granted)))
      .catch(() => undefined)
  }
  useEffect(load, [])

  const run = async (fn: () => Promise<unknown>, ok?: string) => {
    setError(null)
    setMessage(null)
    try {
      await fn()
      if (ok) setMessage(ok)
      load()
      return true
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
      return false
    }
  }

  const sendRequest = async (e: FormEvent) => {
    e.preventDefault()
    const name = username.trim().replace(/^@/, '')
    if (await run(() => api('/api/friends/requests', { method: 'POST', body: { username: name } }), `@${name}-д хүсэлт илгээлээ`)) {
      setUsername('')
    }
  }

  const createInvite = async () => {
    const r = await api<{ token: string }>('/api/friends/invite-link', { method: 'POST' })
    const url = `${window.location.origin}/invite/friend/${r.token}`
    setInvite(url)
    setCopied(null)
    if (navigator.share) {
      navigator.share({ title: 'Ухаалаг маршрут', text: `${user?.displayName} таныг найзаар урьж байна`, url })
        .catch(() => undefined)
    }
  }

  const copy = async (what: 'invite' | 'username', text: string) => {
    await navigator.clipboard.writeText(text).catch(() => undefined)
    setCopied(what)
    window.setTimeout(() => setCopied(null), 2000)
  }

  const position = () => new Promise<GeolocationPosition>((resolve, reject) =>
    navigator.geolocation.getCurrentPosition(resolve, reject, { enableHighAccuracy: true, timeout: 10000 }))

  const toggleSharing = async (on: boolean) => {
    setSharingBusy(true)
    await run(async () => {
      if (!on) {
        await api('/api/locations/me', { method: 'PUT', body: { shared: false } })
      } else {
        if (!sharingConsent) await api('/api/consents/LOCATION_SHARING', { method: 'POST' })
        const p = await position().catch(() => {
          throw new ApiError(0, 'NO_POSITION', 'Байршил тодорхойлогдсонгүй. Хөтчийн байршлын зөвшөөрлийг шалгана уу.')
        })
        await api('/api/locations/me', {
          method: 'PUT', body: { lat: p.coords.latitude, lng: p.coords.longitude, shared: true },
        })
      }
    })
    setSharingBusy(false)
    window.dispatchEvent(new Event(SHARING_CHANGED))
  }

  const incoming = friends.filter((f) => f.direction === 'INCOMING')
  const outgoing = friends.filter((f) => f.direction === 'OUTGOING')
  const accepted = friends.filter((f) => f.direction === 'FRIEND')
  const sharedBy = new Map(friendLocations.map((l) => [l.userId, l]))
  const sharing = !!location?.shared

  return (
    <div className="page social">
      <PageHeader title="Найзууд" back="/" />

      {/* Би хэн бэ */}
      <section className="s-card me-card">
        <Avatar name={user?.displayName ?? '?'} size={56} />
        <div className="r-main">
          <div className="profile-name">{user?.displayName}</div>
          <button className="handle" onClick={() => copy('username', user?.username ?? '')}
                  title="Хэрэглэгчийн нэрээ хуулах">
            @{user?.username} <span className="handle-hint">{copied === 'username' ? '✓ хуулсан' : 'хуулах'}</span>
          </button>
        </div>
        <div className="me-count">
          <strong>{accepted.length}</strong>
          <span>найз</span>
        </div>
      </section>

      {error && <div className="s-note error">{error}</div>}
      {message && <div className="s-note success">{message}</div>}

      {/* Ирсэн хүсэлт эхэндээ: хариу шаарддаг */}
      {incoming.length > 0 && (
        <section className="s-card highlight">
          <h2>Ирсэн хүсэлт <span className="count">{incoming.length}</span></h2>
          <ul className="people">
            {incoming.map((f) => (
              <li key={f.id}>
                <Avatar name={f.user.displayName} />
                <span className="r-main">
                  <span className="r-name">{f.user.displayName}</span>
                  <span className="r-sub">@{f.user.username} таныг найзаар нэмэхийг хүсэж байна</span>
                </span>
                <span className="people-actions">
                  <button className="btn small-btn" onClick={() => run(() => api(`/api/friends/requests/${f.id}/accept`, { method: 'POST' }),
                    `${f.user.displayName} одоо таны найз`)}>Зөвшөөрөх</button>
                  <button className="btn secondary small-btn" onClick={() => run(() => api(`/api/friends/requests/${f.id}/reject`, { method: 'POST' }))}>
                    Татгалзах
                  </button>
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* Байршил хуваалцах */}
      <section className="s-card">
        <label className="switch-row first">
          <span className={`share-dot ${sharing ? 'on' : ''}`}><Icon name="navigation" size={18} /></span>
          <span className="r-main">
            <span className="t-title">Байршлаа найзуудтайгаа хуваалцах</span>
            <span className="t-desc">
              {sharing
                ? `Хуваалцаж байна${location?.updatedAt ? ` · сүүлд ${agoText(location.updatedAt)}` : ''}`
                : 'Унтраастай · найзууд таны байршлыг харахгүй'}
            </span>
          </span>
          <input type="checkbox" className="switch" checked={sharing} disabled={sharingBusy}
                 onChange={(e) => toggleSharing(e.target.checked)} />
        </label>
        <p className="s-desc fine">
          Зөвхөн таны найзууд таны сүүлийн байршлыг газрын зураг дээр харна. Апп нээлттэй үед 2 минут тутам
          шинэчлэгдэж, 60 минутаас хуучин байршил устгагдана.
        </p>
      </section>

      {/* Найз нэмэх */}
      <section className="s-card">
        <h2>Найз нэмэх</h2>
        <p className="s-desc">Найзынхаа хэрэглэгчийн нэрийг оруулах эсвэл урилгын холбоос илгээнэ үү.</p>
        <form className="add-friend" onSubmit={sendRequest}>
          <span className="at-input">
            <span>@</span>
            <input placeholder="хэрэглэгчийн нэр" required value={username} autoCapitalize="none" autoCorrect="off"
                   onChange={(e) => setUsername(e.target.value)} />
          </span>
          <button className="btn" disabled={!username.trim()}>Хүсэлт илгээх</button>
        </form>
        <div className="or"><span>эсвэл</span></div>
        {!invite ? (
          <button type="button" className="invite-btn" onClick={createInvite}>
            <Icon name="share" size={18} />
            <span className="r-main">
              <span className="t-title">Урилгын холбоос үүсгэх</span>
              <span className="t-desc">Бүртгэлгүй найздаа ч илгээж болно · 7 хоног хүчинтэй</span>
            </span>
            <Icon name="chevron" size={16} />
          </button>
        ) : (
          <div className="invite-box">
            <Icon name="link" size={16} />
            <code>{invite}</code>
            <button className="btn secondary small-btn" onClick={() => copy('invite', invite)}>
              {copied === 'invite' ? '✓ Хуулсан' : 'Хуулах'}
            </button>
          </div>
        )}
      </section>

      {/* Найзууд */}
      <section className="s-card">
        <h2>Найзууд <span className="count muted-count">{accepted.length}</span></h2>
        {accepted.length === 0 ? (
          <div className="empty">
            <Icon name="users" size={28} />
            <p>Одоогоор найз алга. Дээрээс найзаа нэмээрэй.</p>
          </div>
        ) : (
          <ul className="people">
            {accepted.map((f) => {
              const loc = sharedBy.get(f.user.id)
              return (
                <li key={f.id}>
                  <Avatar name={f.user.displayName} />
                  <span className="r-main">
                    <span className="r-name">{f.user.displayName}</span>
                    <span className="r-sub">
                      @{f.user.username}
                      {loc && <span className="live"> · байршлаа хуваалцаж байна ({agoText(loc.updatedAt)})</span>}
                    </span>
                  </span>
                  <button className="ghost-btn" onClick={() => window.confirm(`${f.user.displayName}-г найзаас хасах уу?`)
                    && run(() => api(`/api/friends/${f.id}`, { method: 'DELETE' }))}>Хасах</button>
                </li>
              )
            })}
          </ul>
        )}

        {outgoing.length > 0 && (
          <>
            <h3 className="sub-h">Хариу хүлээж буй</h3>
            <ul className="people">
              {outgoing.map((f) => (
                <li key={f.id} className="pending">
                  <Avatar name={f.user.displayName} size={34} />
                  <span className="r-main">
                    <span className="r-name">{f.user.displayName}</span>
                    <span className="r-sub">@{f.user.username} · хүсэлт илгээсэн</span>
                  </span>
                  <button className="ghost-btn" onClick={() => run(() => api(`/api/friends/${f.id}`, { method: 'DELETE' }))}>Цуцлах</button>
                </li>
              ))}
            </ul>
          </>
        )}
      </section>
    </div>
  )
}
