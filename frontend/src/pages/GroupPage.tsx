import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import PageHeader from '../components/PageHeader'
import { api, ApiError, type GroupMember, type GroupOptions, type GroupView, type TransportMode } from '../api'
import { TRANSPORT_LABELS } from '../categoryLabels'
import { MAIN_CATEGORY_COLORS } from '../places'
import { GROUP_STATUS } from './GroupsPage'

const START_LABELS: Record<string, string> = {
  SHARED_LOCATION: 'хуваалцсан байршил',
  PINNED: 'сонгосон цэг',
  SAME_AS_INITIATOR: 'санаачлагчийн байршил',
}

/** One group plan: options with every member's travel time, sharing, invite link, own Maps link (АШ10, АШ24). */
export default function GroupPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [group, setGroup] = useState<GroupView | null>(null)
  const [options, setOptions] = useState<GroupOptions | null>(null)
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

  const loadOptions = () => run(async () => setOptions(await api<GroupOptions>(`/api/groups/${id}/options`)))

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

  const remove = (m: GroupMember) => run(async () => {
    setGroup(await api<GroupView>(`/api/groups/${id}/members`, { method: 'PATCH', body: { op: 'REMOVE', memberId: m.id } }))
    setOptions(null)
  })

  const cancel = () => window.confirm('Төлөвлөгөөг цуцлах уу?') && run(async () => {
    await api(`/api/groups/${id}`, { method: 'DELETE' })
    navigate('/groups', { replace: true })
  })

  if (!group) {
    return <div className="page"><div className="card">{error ? <div className="error">{error}</div> : <p className="muted">Ачаалж байна…</p>}</div></div>
  }

  const inviteUrl = group.inviteToken ? `${window.location.origin}/join/${group.inviteToken}` : null
  const shareInvite = async () => {
    if (!inviteUrl) return
    if (navigator.share) {
      navigator.share({ title: 'Уулзах газар', text: `${group.meetingPlace?.name} дээр уулзъя`, url: inviteUrl }).catch(() => undefined)
    } else {
      await navigator.clipboard.writeText(inviteUrl)
      setCopied(true)
    }
  }
  const mine = group.members.find((m) => m.me)
  const planning = group.status === 'RECOMMENDED' && group.initiator
  const nameOf = (memberId: string) => group.members.find((m) => String(m.id) === memberId)?.name ?? '?'

  return (
    <div className="page">
      <PageHeader title="Бүлгийн төлөвлөгөө" back="/groups" />
      <div className="card wide">
        <p className="lead">{GROUP_STATUS[group.status]} · {group.timeBudgetMinutes / 60} цаг</p>
        {error && <div className="error">{error}</div>}

        {group.meetingPlace && (
          <div className="meeting">
            <div className="sheet-cat">
              <span className="dot" style={{ background: MAIN_CATEGORY_COLORS[group.meetingPlace.mainCategory] ?? '#888' }} />
              {group.meetingPlace.categoryName}
            </div>
            <h2 style={{ margin: '2px 0 8px' }}>{group.meetingPlace.name}</h2>
            <div className="sheet-actions" style={{ marginTop: 0 }}>
              {mine?.mapsUrl && <a className="btn" href={mine.mapsUrl} target="_blank" rel="noopener noreferrer">Google Maps-ээр очих</a>}
              <Link className="btn secondary" to={`/?place=${group.meetingPlace.id}`}>Газрын зураг дээр</Link>
            </div>
            {mine?.travelMinutes != null && <p className="muted">Танд ~{mine.travelMinutes} мин</p>}
          </div>
        )}

        {inviteUrl && (
          <>
            <h2>Урилгын холбоос</h2>
            <p className="muted">Холбоосыг нээсэн хүн бүр (бүртгэлгүй ч) өөрийн байршлаас ирэх Google Maps-ийн холбоосыг авна.
              {group.inviteExpiresAt && ` ${new Date(group.inviteExpiresAt).toLocaleString('mn-MN')} хүртэл хүчинтэй.`}</p>
            <div className="invite-box">
              <code>{inviteUrl}</code>
              <button className="btn secondary" onClick={shareInvite}>{copied ? 'Хууллаа' : 'Хуваалцах'}</button>
            </div>
          </>
        )}

        <h2>Гишүүд ({group.members.length})</h2>
        <ul className="friend-list">
          {group.members.map((m) => (
            <li key={m.id}>
              <span className="person">
                <span className="avatar small">{m.name.trim().charAt(0).toUpperCase()}</span>
                <span className="r-main">
                  <span className="r-name">{m.name}{m.me ? ' (та)' : ''}{m.role === 'INITIATOR' ? ' · санаачлагч' : ''}</span>
                  <span className="r-sub">
                    {m.guest ? 'зочин' : m.usesInterests ? 'сонирхол тооцогдоно' : 'сонирхол тооцогдохгүй'}
                    {m.startSource && ` · эхлэх: ${START_LABELS[m.startSource]}`}
                    {m.travelMinutes != null && ` · ~${m.travelMinutes} мин`}
                  </span>
                </span>
              </span>
              {planning ? (
                <select value={m.transportMode} disabled={busy} onChange={(e) => changeMode(m, e.target.value as TransportMode)}>
                  {Object.entries(TRANSPORT_LABELS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
                </select>
              ) : <span className="muted">{TRANSPORT_LABELS[m.transportMode]}</span>}
              {group.initiator && !m.me && m.mapsUrl && (
                <a className="link" href={m.mapsUrl} target="_blank" rel="noopener noreferrer">Maps</a>
              )}
              {planning && m.role !== 'INITIATOR' && (
                <button className="rec-x" title="Хасах" disabled={busy} onClick={() => remove(m)}>✕</button>
              )}
            </li>
          ))}
        </ul>

        {planning && (
          <>
            <h2>Уулзах газар</h2>
            <button className="btn" disabled={busy} onClick={loadOptions}>
              {busy ? 'Тооцоолж байна…' : options ? 'Дахин санал болгох' : 'Хувилбар авах'}
            </button>
            {options && options.options.length === 0 && (
              <p className="muted">Бүгд цагтаа хүрэх тохирох газар олдсонгүй. Хугацаагаа нэмэх эсвэл тээврийн хэрэгслээ өөрчилнө үү.</p>
            )}
            <ol className="rec-list">
              {options?.options.map((o, i) => (
                <li key={o.recommendationId}>
                  <div className="rec-main" style={{ cursor: 'default' }}>
                    <span className="rec-num" style={{ background: MAIN_CATEGORY_COLORS[o.place.mainCategory] ?? '#888' }}>{i + 1}</span>
                    <span className="r-main">
                      <span className="r-name">{o.place.name}</span>
                      <span className="r-sub">{o.place.categoryName} · хамгийн удаан ~{o.maxTravelMinutes} мин</span>
                      <span className="r-sub">
                        {Object.entries(o.travelMinutesByMember).map(([mid, t]) => `${nameOf(mid)} ${t}′`).join(' · ')}
                      </span>
                    </span>
                  </div>
                  <button className="btn" style={{ alignSelf: 'center' }} disabled={busy} onClick={() => share(o.place.id)}>Сонгох</button>
                </li>
              ))}
            </ol>
          </>
        )}

        {group.initiator && (group.status === 'RECOMMENDED' || group.status === 'SHARED') && (
          <button className="btn danger" style={{ marginTop: 24 }} disabled={busy} onClick={cancel}>Төлөвлөгөөг цуцлах</button>
        )}
      </div>
    </div>
  )
}
