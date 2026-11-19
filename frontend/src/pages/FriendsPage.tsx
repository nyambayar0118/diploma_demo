import { useEffect, useState, type FormEvent } from 'react'
import PageHeader from '../components/PageHeader'
import { agoText, api, ApiError, type Consent, type Friendship, type MyLocation } from '../api'
import { useAuth } from '../auth'
import { SHARING_CHANGED } from '../locationSharing'

/** Friends (АШ20) and location sharing (АШ21, ХШ07). */
export default function FriendsPage() {
  const { user } = useAuth()
  const [friends, setFriends] = useState<Friendship[]>([])
  const [username, setUsername] = useState('')
  const [invite, setInvite] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
  const [location, setLocation] = useState<MyLocation | null>(null)
  const [sharingConsent, setSharingConsent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const load = () => {
    api<Friendship[]>('/api/friends').then(setFriends).catch(() => undefined)
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
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    }
  }

  const sendRequest = (e: FormEvent) => {
    e.preventDefault()
    run(() => api('/api/friends/requests', { method: 'POST', body: { username } }), 'Хүсэлт илгээлээ')
      .then(() => setUsername(''))
  }

  const createInvite = async () => {
    const r = await api<{ token: string }>('/api/friends/invite-link', { method: 'POST' })
    const url = `${window.location.origin}/invite/friend/${r.token}`
    setInvite(url)
    if (navigator.share) {
      navigator.share({ title: 'Ухаалаг маршрут', text: `${user?.displayName} таныг найзаар урьж байна`, url })
        .catch(() => undefined)
    }
  }

  const copy = async () => {
    if (!invite) return
    await navigator.clipboard.writeText(invite)
    setCopied(true)
  }

  const position = () => new Promise<GeolocationPosition>((resolve, reject) =>
    navigator.geolocation.getCurrentPosition(resolve, reject, { enableHighAccuracy: true, timeout: 10000 }))

  const toggleSharing = async (on: boolean) => {
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
    window.dispatchEvent(new Event(SHARING_CHANGED))
  }

  const incoming = friends.filter((f) => f.direction === 'INCOMING')
  const outgoing = friends.filter((f) => f.direction === 'OUTGOING')
  const accepted = friends.filter((f) => f.direction === 'FRIEND')

  return (
    <div className="page">
      <PageHeader title="Найзууд" back="/" />
      <div className="card wide">
        <p className="lead">Таны хэрэглэгчийн нэр: <strong>@{user?.username}</strong></p>
        {error && <div className="error">{error}</div>}
        {message && <div className="success">{message}</div>}

        <h2>Байршил хуваалцах</h2>
        <label className="toggle">
          <input type="checkbox" checked={!!location?.shared} onChange={(e) => toggleSharing(e.target.checked)} />
          <span>
            <div className="t-title">Байршлаа найзуудтайгаа хуваалцах</div>
            <div className="t-desc">
              Зөвхөн доорх найзууд таны сүүлийн байршлыг газрын зураг дээр харна. Апп нээлттэй үед 2 минут тутам
              шинэчлэгдэнэ; 60 минутаас хуучин байршил харагдахгүй, устгагдана. Хүссэн үедээ унтраана.
              {location?.shared && location.updatedAt && ` Сүүлд: ${agoText(location.updatedAt)}.`}
            </div>
          </span>
        </label>

        <h2>Найз нэмэх</h2>
        <form className="row" onSubmit={sendRequest} style={{ alignItems: 'stretch' }}>
          <div className="field" style={{ flex: 1, margin: 0 }}>
            <input placeholder="Хэрэглэгчийн нэр" required value={username} onChange={(e) => setUsername(e.target.value)} />
          </div>
          <button className="btn">Хүсэлт илгээх</button>
        </form>
        <p className="muted" style={{ marginTop: 10 }}>
          Эсвэл <button type="button" className="link" onClick={createInvite}>урилгын холбоос үүсгэх</button> (7 хоног хүчинтэй)
        </p>
        {invite && (
          <div className="invite-box">
            <code>{invite}</code>
            <button className="btn secondary" onClick={copy}>{copied ? 'Хууллаа' : 'Хуулах'}</button>
          </div>
        )}

        {incoming.length > 0 && (
          <>
            <h2>Ирсэн хүсэлт</h2>
            <ul className="friend-list">
              {incoming.map((f) => (
                <li key={f.id}>
                  <Person f={f} />
                  <button className="btn" onClick={() => run(() => api(`/api/friends/requests/${f.id}/accept`, { method: 'POST' }))}>
                    Зөвшөөрөх
                  </button>
                  <button className="btn secondary" onClick={() => run(() => api(`/api/friends/requests/${f.id}/reject`, { method: 'POST' }))}>
                    Татгалзах
                  </button>
                </li>
              ))}
            </ul>
          </>
        )}

        <h2>Найзууд ({accepted.length})</h2>
        {accepted.length === 0 && <p className="muted">Одоогоор найз алга.</p>}
        <ul className="friend-list">
          {accepted.map((f) => (
            <li key={f.id}>
              <Person f={f} />
              <button className="link" onClick={() => window.confirm(`${f.user.displayName}-г найзаас хасах уу?`)
                && run(() => api(`/api/friends/${f.id}`, { method: 'DELETE' }))}>Хасах</button>
            </li>
          ))}
        </ul>

        {outgoing.length > 0 && (
          <>
            <h2>Илгээсэн хүсэлт</h2>
            <ul className="friend-list">
              {outgoing.map((f) => (
                <li key={f.id}>
                  <Person f={f} />
                  <button className="link" onClick={() => run(() => api(`/api/friends/${f.id}`, { method: 'DELETE' }))}>Цуцлах</button>
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </div>
  )
}

function Person({ f }: { f: Friendship }) {
  return (
    <span className="person">
      <span className="avatar small">{f.user.displayName.trim().charAt(0).toUpperCase()}</span>
      <span className="r-main">
        <span className="r-name">{f.user.displayName}</span>
        <span className="r-sub">@{f.user.username}</span>
      </span>
    </span>
  )
}
