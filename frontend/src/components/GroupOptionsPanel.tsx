import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { api, ApiError, type GroupOption, type GroupOptions, type GroupView } from '../api'
import { MAIN_CATEGORY_COLORS, type MemberMarker, type RecMarker } from '../places'
import Avatar, { AvatarStack, avatarColor } from './Avatar'
import BottomSheet from './BottomSheet'

type Props = {
  groupId: number
  focusedId: number | null
  /** Хувилбарыг дарахад газрын зураг тийш шилжинэ. */
  onFocus: (option: GroupOption) => void
  /** Зурах хувилбарын тэмдэгтүүд (хоосон бол арилгана). */
  onResults: (markers: RecMarker[]) => void
  /** Зурах гишүүдийн эхлэх цэг (null бол арилгана). */
  onMembers: (members: MemberMarker[] | null) => void
}

/**
 * Бүлгийн төлөвлөгөөний уулзах газрын хувилбарууд газрын зургийн хажууд (АШ10): нэгийг дарахад газрын зураг тийш
 * шилжиж, гишүүн бүрээс очих замыг зурна. "Сонгох" газрыг тогтоож, төлөвлөгөөний хуудас руу буцна.
 */
export default function GroupOptionsPanel({ groupId, focusedId, onFocus, onResults, onMembers }: Props) {
  const navigate = useNavigate()
  const [group, setGroup] = useState<GroupView | null>(null)
  const [options, setOptions] = useState<GroupOptions | null>(null)
  const [round, setRound] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const itemRefs = useRef(new Map<number, HTMLLIElement>())

  const fetchOptions = async (again: boolean) => {
    setBusy(true)
    setError(null)
    try {
      const o = await api<GroupOptions>(`/api/groups/${groupId}/options${again ? '?again=true' : ''}`)
      setOptions(o)
      setRound((r) => r + 1)
      onResults(o.options.map((x) => ({ placeId: x.place.id, lat: x.place.lat, lng: x.place.lng, mainCategory: x.place.mainCategory })))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    } finally {
      setBusy(false)
    }
  }

  // Төлөвлөгөөг ачаалж, гишүүдийг зурсны дараа эхний хувилбаруудыг хүснэ
  useEffect(() => {
    let alive = true
    api<GroupView>(`/api/groups/${groupId}`).then((g) => {
      if (!alive) return
      setGroup(g)
      onMembers(g.members.filter((m) => m.startLat != null && m.startLng != null).map((m) => ({
        id: m.id, name: m.name, color: avatarColor(m.name), lat: m.startLat as number, lng: m.startLng as number,
      })))
      if (g.status === 'RECOMMENDED' && g.initiator) fetchOptions(false)
    }).catch((e) => setError(e instanceof ApiError ? e.message : 'Сервертэй холбогдсонгүй'))
    return () => {
      alive = false
      onMembers(null)
      onResults([])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId])

  useEffect(() => {
    if (focusedId != null) itemRefs.current.get(focusedId)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [focusedId])

  const back = () => navigate(`/groups/${groupId}`)

  const choose = async (o: GroupOption) => {
    setBusy(true)
    setError(null)
    try {
      await api<GroupView>(`/api/groups/${groupId}/share`, { method: 'POST', body: { placeId: o.place.id } })
      navigate(`/groups/${groupId}`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
      setBusy(false)
    }
  }

  const memberName = (id: string) => group?.members.find((m) => String(m.id) === id)?.name ?? '?'
  const canPlan = group?.status === 'RECOMMENDED' && group.initiator

  return (
    <BottomSheet className="panel group-panel">
      <button className="sheet-close" onClick={back} aria-label="Хаах">×</button>
      <h2 className="sheet-title">Уулзах газар</h2>
      {group && (
        <div className="gp-members">
          <AvatarStack names={group.members.map((m) => m.name)} size={26} max={6} />
          <span className="r-sub">{group.members.map((m) => (m.me ? 'Та' : m.name)).join(', ')} · {group.timeBudgetMinutes / 60} цаг</span>
        </div>
      )}
      {error && <div className="error" style={{ marginTop: 12 }}>{error}</div>}
      {group && !canPlan && (
        <p className="muted">Энэ төлөвлөгөөнд хувилбар сонгох боломжгүй ({group.initiator ? 'аль хэдийн сонгосон' : 'зөвхөн санаачлагч сонгоно'}).</p>
      )}

      {canPlan && (
        <>
          <p className="gp-hint">Газрыг дарвал газрын зураг тийш шилжиж, гишүүн бүрээс очих зам тасархай зураасаар харагдана.</p>
          {!options && busy && <p className="muted">Бүгдэд тохирох газар хайж байна…</p>}
          {options && options.options.length === 0 && (
            <p className="muted">Бүгд цагтаа хүрэх тохирох газар олдсонгүй. Төлөвлөгөөний хуудаснаас хугацаа эсвэл тээврээ өөрчилнө үү.</p>
          )}
          {options && options.options.length > 0 && (
            <ol className="opt-list">
              {options.options.map((o, i) => {
                const c = MAIN_CATEGORY_COLORS[o.place.mainCategory] ?? '#888'
                const focused = focusedId === o.place.id
                return (
                  <li key={o.recommendationId} className={`opt ${focused ? 'focused' : ''}`}
                      ref={(el) => { if (el) itemRefs.current.set(o.place.id, el); else itemRefs.current.delete(o.place.id) }}
                      onClick={() => onFocus(o)} role="button" tabIndex={0}
                      onKeyDown={(e) => { if (e.key === 'Enter') onFocus(o) }}>
                    <span className="opt-num" style={{ background: c }}>{i + 1}</span>
                    <span className="r-main">
                      <span className="r-name">{o.place.name}</span>
                      <span className="r-sub">{o.place.categoryName} · хамгийн удаан ~{o.maxTravelMinutes} мин</span>
                      <span className="opt-times">
                        {Object.entries(o.travelMinutesByMember).map(([mid, t]) => (
                          <span key={mid} className="opt-time" title={memberName(mid)}>
                            <Avatar name={memberName(mid)} size={18} />{t}′
                          </span>
                        ))}
                      </span>
                      {focused && (
                        <span className="rec-more">
                          <button type="button" className="btn small-btn" disabled={busy}
                                  onClick={(e) => { e.stopPropagation(); choose(o) }}>Энэ газрыг сонгох</button>
                        </span>
                      )}
                    </span>
                  </li>
                )
              })}
            </ol>
          )}
          {options && (
            <button className="btn secondary block" disabled={busy} onClick={() => fetchOptions(true)}>
              {busy ? 'Тооцоолж байна…' : '↻ Өөр газрууд санал болгох'}
            </button>
          )}
          {round > 1 && <p className="gp-hint" style={{ textAlign: 'center' }}>{round}-р санал · өмнө харуулсан газрууд хойно орсон</p>}
        </>
      )}
    </BottomSheet>
  )
}
