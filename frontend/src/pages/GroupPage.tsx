import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import PageHeader from '../components/PageHeader'
import Avatar, { AvatarStack } from '../components/Avatar'
import Icon from '../components/Icon'
import { api, ApiError, type GroupMember, type GroupOptions, type GroupView, type TransportMode } from '../api'
import { TRANSPORT_LABELS } from '../categoryLabels'
import { MAIN_CATEGORY_COLORS } from '../places'
import { GROUP_STATUS } from './GroupsPage'

const START_LABELS: Record<string, string> = {
  SHARED_LOCATION: 'хуваалцсан байршлаас',
  PINNED: 'сонгосон цэгээс',
  SAME_AS_INITIATOR: 'санаачлагчийн байршлаас',
}

const STATUS_CLASS: Record<string, string> = {
  RECOMMENDED: 'created',
  SHARED: 'opened_in_maps',
  COMPLETED: 'completed',
  CANCELLED: 'cancelled',
}

/** One group plan: options with every member's travel time, sharing, invite link, own Maps link (АШ10, АШ24). */
export default function GroupPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [group, setGroup] = useState<GroupView | null>(null)
  const [options, setOptions] = useState<GroupOptions | null>(null)
  const [round, setRound] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState(false)

  const load = useCallback(() => api<GroupView>(`/api/groups/${id}`).then(setGroup)
    .catch((e) => setError(e instanceof ApiError ? e.message : 'Сервертэй холбогдсонгүй')), [id])
  useEffect(() => {
    load()
  }, [load])

  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true)
    setError(null)
    try {
      await fn()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    } finally {
      setBusy(false)
    }
  }

  // The first request gives the best places; asking again skips the ones already offered in this plan
  const loadOptions = () => run(async () => {
    const again = options !== null
    setOptions(await api<GroupOptions>(`/api/groups/${id}/options${again ? '?again=true' : ''}`))
    setRound((r) => r + 1)
  })

  const share = (placeId: number) => run(async () => {
    setGroup(await api<GroupView>(`/api/groups/${id}/share`, { method: 'POST', body: { placeId } }))
    setOptions(null)
  })

  const changeMode = (m: GroupMember, transportMode: TransportMode) => run(async () => {
    setGroup(await api<GroupView>(`/api/groups/${id}/members`, {
      method: 'PATCH', body: { op: 'SET_START', memberId: m.id, transportMode },
    }))
    setOptions(null)
  })

  const remove = (m: GroupMember) => window.confirm(`${m.name}-г төлөвлөгөөнөөс хасах уу?`) && run(async () => {
    setGroup(await api<GroupView>(`/api/groups/${id}/members`, { method: 'PATCH', body: { op: 'REMOVE', memberId: m.id } }))
    setOptions(null)
  })

  const cancel = () => window.confirm('Төлөвлөгөөг цуцлах уу?') && run(async () => {
    await api(`/api/groups/${id}`, { method: 'DELETE' })
    navigate('/groups', { replace: true })
  })

  if (!group) {
    return (
      <div className="page social">
        <PageHeader title="Бүлгийн төлөвлөгөө" back="/groups" />
        <section className="s-card">{error ? <div className="error">{error}</div> : <p className="muted">Ачаалж байна…</p>}</section>
      </div>
    )
  }

  const inviteUrl = group.inviteToken ? `${window.location.origin}/join/${group.inviteToken}` : null
  const shareInvite = async () => {
    if (!inviteUrl) return
    if (navigator.share) {
      navigator.share({ title: 'Уулзах газар', text: `${group.meetingPlace?.name} дээр уулзъя`, url: inviteUrl }).catch(() => undefined)
    } else {
      await navigator.clipboard.writeText(inviteUrl).catch(() => undefined)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    }
  }
  const mine = group.members.find((m) => m.me)
  const planning = group.status === 'RECOMMENDED' && group.initiator
  const open = group.status === 'RECOMMENDED' || group.status === 'SHARED'
  const memberById = (memberId: string) => group.members.find((m) => String(m.id) === memberId)
  const place = group.meetingPlace
  const color = place ? MAIN_CATEGORY_COLORS[place.mainCategory] ?? '#888' : undefined

  return (
    <div className="page social">
      <PageHeader title="Бүлгийн төлөвлөгөө" back="/groups" />

      {/* Summary */}
      <section className="s-card plan-hero">
        <div className="hero-top">
          <span className={`badge status-${STATUS_CLASS[group.status]}`}>{GROUP_STATUS[group.status]}</span>
          <span className="hero-meta"><Icon name="clock" size={14} /> {group.timeBudgetMinutes / 60} цаг</span>
          <span className="hero-meta"><AvatarStack names={group.members.map((m) => m.name)} size={24} /> {group.members.length} хүн</span>
        </div>

        {place ? (
          <div className="meet-place" style={{ ['--cat' as string]: color }}>
            <span className="meet-pin"><Icon name="pin" size={22} /></span>
            <span className="r-main">
              <span className="meet-cat"><span className="dot" style={{ background: color }} />{place.categoryName}</span>
              <span className="meet-name">{place.name}</span>
              {mine?.travelMinutes != null && <span className="r-sub">Танд очиход ~{mine.travelMinutes} мин</span>}
            </span>
          </div>
        ) : (
          <p className="hero-empty">
            {planning ? 'Уулзах газраа доорх хувилбаруудаас сонгоорой.' : 'Санаачлагч уулзах газраа хараахан сонгоогүй байна.'}
          </p>
        )}

        {place && (
          <div className="hero-actions">
            {mine?.mapsUrl && (
              <a className="btn" href={mine.mapsUrl} target="_blank" rel="noopener noreferrer">
                <Icon name="navigation" size={16} /> Google Maps-ээр очих
              </a>
            )}
            <Link className="btn secondary" to={`/?place=${place.id}`}><Icon name="layers" size={16} /> Газрын зураг дээр</Link>
          </div>
        )}
      </section>

      {error && <div className="s-note error">{error}</div>}

      {/* Invite */}
      {inviteUrl && (
        <section className="s-card">
          <h2>Урилгын холбоос</h2>
          <p className="s-desc">
            Холбоосыг нээсэн хүн бүр (бүртгэлгүй ч) өөрийн байршлаас ирэх замаа Google Maps-ээр авна.
            {group.inviteExpiresAt && <> <strong>{new Date(group.inviteExpiresAt).toLocaleString('mn-MN',
              { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</strong> хүртэл хүчинтэй.</>}
          </p>
          <div className="invite-box">
            <Icon name="link" size={16} />
            <code>{inviteUrl}</code>
            <button className="btn small-btn" onClick={shareInvite}>
              {copied ? '✓ Хуулсан' : <><Icon name="share" size={14} /> Хуваалцах</>}
            </button>
          </div>
        </section>
      )}

      {/* Options */}
      {planning && (
        <section className="s-card">
          <div className="row">
            <h2 style={{ margin: 0 }}>Уулзах газрын хувилбар</h2>
            {options && <span className="r-sub">{round > 1 ? `${round}-р санал` : ''}</span>}
          </div>
          <p className="s-desc" style={{ marginTop: 4 }}>
            Бүгдийн сонирхлыг нэгтгэж, хэн нэгэн хэт хол явахгүй байхаар эрэмбэлнэ. Дахин санал болгоход өмнө харуулсан газрууд хойно орно.
          </p>
          {options && options.options.length === 0 && (
            <div className="empty">
              <Icon name="pin" size={28} />
              <p>Бүгд цагтаа хүрэх тохирох газар олдсонгүй. Хугацаагаа нэмэх эсвэл тээврийн хэрэгслээ өөрчилнө үү.</p>
            </div>
          )}
          {options && options.options.length > 0 && (
            <ol className="opt-list">
              {options.options.map((o, i) => {
                const c = MAIN_CATEGORY_COLORS[o.place.mainCategory] ?? '#888'
                const times = Object.entries(o.travelMinutesByMember)
                return (
                  <li key={o.recommendationId} className="opt">
                    <span className="opt-num" style={{ background: c }}>{i + 1}</span>
                    <span className="r-main">
                      <span className="r-name">{o.place.name}</span>
                      <span className="r-sub">{o.place.categoryName} · хамгийн удаан ~{o.maxTravelMinutes} мин</span>
                      <span className="opt-times">
                        {times.map(([mid, t]) => {
                          const m = memberById(mid)
                          return (
                            <span key={mid} className="opt-time" title={m?.name}>
                              <Avatar name={m?.name ?? '?'} size={18} />{t}′
                            </span>
                          )
                        })}
                      </span>
                    </span>
                    <span className="opt-side">
                      <Link className="ghost-btn" to={`/?place=${o.place.id}`} title="Газрын зураг дээр">Харах</Link>
                      <button className="btn small-btn" disabled={busy} onClick={() => share(o.place.id)}>Сонгох</button>
                    </span>
                  </li>
                )
              })}
            </ol>
          )}
          <button className={`btn ${options ? 'secondary' : ''} block`} disabled={busy} onClick={loadOptions}>
            {busy ? 'Тооцоолж байна…' : options ? '↻ Өөр газрууд санал болгох' : 'Хувилбар авах'}
          </button>
        </section>
      )}

      {/* Members */}
      <section className="s-card">
        <h2>Гишүүд <span className="count muted-count">{group.members.length}</span></h2>
        <ul className="people">
          {group.members.map((m) => (
            <li key={m.id}>
              <Avatar name={m.name} />
              <span className="r-main">
                <span className="r-name">
                  {m.name}
                  {m.me && <span className="tag">та</span>}
                  {m.role === 'INITIATOR' && <span className="tag primary">санаачлагч</span>}
                  {m.guest && <span className="tag">зочин</span>}
                </span>
                <span className="r-sub">
                  {[
                    m.startSource ? START_LABELS[m.startSource] : null,
                    m.travelMinutes != null ? `~${m.travelMinutes} мин` : null,
                    !m.guest && !m.usesInterests ? 'сонирхол тооцохгүй' : null,
                  ].filter(Boolean).join(' · ')}
                </span>
              </span>
              <span className="member-side">
                {planning ? (
                  <select className="mini-select" value={m.transportMode} disabled={busy}
                          onChange={(e) => changeMode(m, e.target.value as TransportMode)} aria-label="Тээвэр">
                    {Object.entries(TRANSPORT_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                  </select>
                ) : <span className="mode-pill">{TRANSPORT_LABELS[m.transportMode]}</span>}
                {group.initiator && !m.me && m.mapsUrl && (
                  <a className="ghost-btn" href={m.mapsUrl} target="_blank" rel="noopener noreferrer" title="Энэ гишүүний зам">Зам</a>
                )}
                {planning && m.role !== 'INITIATOR' && (
                  <button className="icon-x" title="Хасах" aria-label="Хасах" disabled={busy} onClick={() => remove(m)}>✕</button>
                )}
              </span>
            </li>
          ))}
        </ul>
      </section>

      {group.initiator && open && (
        <button className="cancel-plan" disabled={busy} onClick={cancel}>Төлөвлөгөөг цуцлах</button>
      )}
    </div>
  )
}
