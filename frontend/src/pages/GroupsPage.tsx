import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import PageHeader from '../components/PageHeader'
import Avatar, { AvatarStack } from '../components/Avatar'
import Icon from '../components/Icon'
import { api, ApiError, currentPosition, type Friendship, type GroupView, type TransportMode } from '../api'
import { TRANSPORT_LABELS } from '../categoryLabels'
import { useAuth } from '../auth'

export const GROUP_STATUS: Record<string, string> = {
  RECOMMENDED: 'Төлөвлөж байна',
  SHARED: 'Хуваалцсан',
  COMPLETED: 'Дууссан',
  CANCELLED: 'Цуцалсан',
}

const STATUS_CLASS: Record<string, string> = {
  RECOMMENDED: 'created',
  SHARED: 'opened_in_maps',
  COMPLETED: 'completed',
  CANCELLED: 'cancelled',
}

const HOURS = [1, 2, 3, 4, 6]

// Ulaanbaatar centre, used when the device position is not available
const UB = { lat: 47.9186, lng: 106.9177 }

/** My group plans and a form to start a new one with friends (АШ10). */
export default function GroupsPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [groups, setGroups] = useState<GroupView[]>([])
  const [friends, setFriends] = useState<Friendship[]>([])
  const [picked, setPicked] = useState<number[]>([])
  const [minutes, setMinutes] = useState(180)
  const [mode, setMode] = useState<TransportMode>('WALKING')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [showPast, setShowPast] = useState(false)

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

  const active = groups.filter((g) => g.status === 'RECOMMENDED' || g.status === 'SHARED')
  const past = groups.filter((g) => g.status === 'COMPLETED' || g.status === 'CANCELLED')
  const pickedNames = friends.filter((f) => picked.includes(f.user.id)).map((f) => f.user.displayName)

  return (
    <div className="page social">
      <PageHeader title="Бүлгээр төлөвлөх" back="/" />

      <section className="s-card intro-card">
        <span className="intro-icon"><Icon name="meet" size={24} /></span>
        <p>
          Найзуудаа сонгоход <strong>бүгдийн сонирхолд нийцсэн</strong>, хүн бүр <strong>ойролцоо хугацаанд хүрэх</strong> уулзах
          газрыг санал болгоно.
        </p>
      </section>

      {error && <div className="s-note error">{error}</div>}

      <section className="s-card">
        <h2>Шинэ төлөвлөгөө</h2>
        {friends.length === 0 ? (
          <div className="empty">
            <Icon name="users" size={28} />
            <p>Эхлээд <Link to="/friends">найз нэмнэ үү</Link>. Бүртгэлгүй хүмүүсийг дараа нь урилгын холбоосоор нэгтгэж болно.</p>
          </div>
        ) : (
          <>
            <div className="step"><span className="step-n">1</span>Хэнтэй уулзах вэ?</div>
            <div className="pick-grid">
              <div className="pick me" aria-disabled>
                <Avatar name={user?.displayName ?? '?'} size={36} />
                <span className="r-main"><span className="r-name">Та</span><span className="r-sub">санаачлагч</span></span>
                <Icon name="check" size={16} />
              </div>
              {friends.map((f) => {
                const on = picked.includes(f.user.id)
                return (
                  <button key={f.id} type="button" className={`pick ${on ? 'on' : ''}`} aria-pressed={on}
                          onClick={() => toggle(f.user.id)}>
                    <Avatar name={f.user.displayName} size={36} />
                    <span className="r-main">
                      <span className="r-name">{f.user.displayName}</span>
                      <span className="r-sub">@{f.user.username}</span>
                    </span>
                    <span className="pick-check">{on && <Icon name="check" size={16} />}</span>
                  </button>
                )
              })}
            </div>

            <div className="step"><span className="step-n">2</span>Хэр удаан хамт байх вэ?</div>
            <div className="seg">
              {HOURS.map((h) => (
                <button key={h} type="button" className={minutes === h * 60 ? 'on' : ''} onClick={() => setMinutes(h * 60)}>
                  {h} цаг
                </button>
              ))}
            </div>

            <div className="step"><span className="step-n">3</span>Та юугаар очих вэ?</div>
            <div className="seg wrap">
              {Object.entries(TRANSPORT_LABELS).map(([k, v]) => (
                <button key={k} type="button" className={mode === k ? 'on' : ''} onClick={() => setMode(k as TransportMode)}>
                  {v}
                </button>
              ))}
            </div>
            <p className="s-desc fine">
              Найзуудын эхлэх цэг: байршлаа хуваалцаж байвал тэр, үгүй бол таны байршил. Тээврийн хэрэгслийг тохиргооноос нь авна.
            </p>

            <button className="btn block" disabled={busy || picked.length === 0} onClick={create}>
              {busy ? 'Уулзах газар хайж байна…'
                : picked.length === 0 ? 'Найзаа сонгоно уу'
                : `Уулзах газар санал авах · ${picked.length + 1} хүн`}
            </button>
            {pickedNames.length > 0 && (
              <p className="picked-line">Та, {pickedNames.join(', ')}</p>
            )}
          </>
        )}
      </section>

      <section className="s-card">
        <h2>Миний төлөвлөгөөнүүд</h2>
        {groups.length === 0 && (
          <div className="empty">
            <Icon name="clock" size={28} />
            <p>Одоогоор төлөвлөгөө алга.</p>
          </div>
        )}
        {active.length > 0 && <PlanList groups={active} />}
        {active.length === 0 && groups.length > 0 && <p className="s-desc">Идэвхтэй төлөвлөгөө алга.</p>}
        {past.length > 0 && (
          <>
            <button type="button" className="ghost-btn past-toggle" onClick={() => setShowPast((v) => !v)}>
              {showPast ? 'Өмнөхийг нуух' : `Өмнөх төлөвлөгөө (${past.length})`}
            </button>
            {showPast && <PlanList groups={past} />}
          </>
        )}
      </section>
    </div>
  )
}

function PlanList({ groups }: { groups: GroupView[] }) {
  return (
    <ul className="plans">
      {groups.map((g) => (
        <li key={g.id}>
          <Link to={`/groups/${g.id}`} className={`plan status-${STATUS_CLASS[g.status]}`}>
            <span className="plan-icon"><Icon name={g.meetingPlace ? 'pin' : 'meet'} size={18} /></span>
            <span className="r-main">
              <span className="r-name">{g.meetingPlace?.name ?? 'Уулзах газар сонгоогүй'}</span>
              <span className="plan-meta">
                <AvatarStack names={g.members.map((m) => m.name)} />
                <span className="r-sub">
                  {g.members.length} хүн · {new Date(g.createdAt).toLocaleDateString('mn-MN', { month: 'short', day: 'numeric' })}
                </span>
              </span>
            </span>
            <span className={`badge status-${STATUS_CLASS[g.status]}`}>{GROUP_STATUS[g.status]}</span>
            <Icon name="chevron" size={16} />
          </Link>
        </li>
      ))}
    </ul>
  )
}
