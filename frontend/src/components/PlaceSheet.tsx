import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api, googleMapsUrl, logInteraction, type MarkType, type PlaceDetails } from '../api'
import { useAuth } from '../auth'
import { describeOpeningHours, type OpeningInfo } from '../openingHours'
import { MAIN_CATEGORY_COLORS } from '../places'
import Reviews from './Reviews'
import ContributionForm from './ContributionForm'

type Props = {
  placeId: number
  recommendationId?: number
  /** "Маршрутад нэмэх" while a planned route can still take more stops (АШ15). */
  onAddToRoute?: () => void
  onClose: () => void
}

export default function PlaceSheet({ placeId, recommendationId, onAddToRoute, onClose }: Props) {
  const { user } = useAuth()
  const [place, setPlace] = useState<PlaceDetails | null>(null)
  const [mark, setMark] = useState<MarkType | null>(null)
  const [hours, setHours] = useState<OpeningInfo | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [contributing, setContributing] = useState(false)
  const [thanks, setThanks] = useState<string | null>(null)

  useEffect(() => {
    setPlace(null)
    setContributing(false)
    setThanks(null)
    setHours(null)
    setError(null)
    api<PlaceDetails>(`/api/places/${placeId}`)
      .then((p) => {
        setPlace(p)
        if (p.openingHours) describeOpeningHours(p.openingHours).then(setHours)
      })
      .catch(() => setError('Газрын мэдээллийг ачаалж чадсангүй'))
    if (user) {
      api<{ type: MarkType | null }>(`/api/places/${placeId}/mark`).then((m) => setMark(m.type))
      logInteraction(placeId, 'VIEW')
    } else {
      setMark(null)
    }
  }, [placeId, user])

  const toggleMark = async (type: MarkType) => {
    if (mark === type) {
      await api(`/api/places/${placeId}/mark`, { method: 'DELETE' })
      setMark(null)
    } else {
      const r = await api<{ type: MarkType }>(`/api/places/${placeId}/mark`, { method: 'PUT', body: { type } })
      setMark(r.type)
    }
  }

  const openInMaps = () => {
    if (!place) return
    logInteraction(place.id, 'OPEN_IN_MAPS', recommendationId)
    window.open(googleMapsUrl(place.lat, place.lng), '_blank', 'noopener')
  }

  return (
    <aside className="sheet">
      <button className="sheet-close" onClick={onClose} aria-label="Хаах">×</button>
      {error && <div className="error">{error}</div>}
      {!place && !error && <p className="muted">Ачаалж байна…</p>}
      {place && (
        <>
          <div className="sheet-head">
            <div>
              <div className="sheet-cat">
                <span className="dot" style={{ background: MAIN_CATEGORY_COLORS[place.mainCategory] ?? '#888' }} />
                {place.categoryName}
              </div>
              <h2 className="sheet-title">{place.name}</h2>
              {place.nameLatin && place.nameLatin !== place.name && <div className="muted">{place.nameLatin}</div>}
            </div>
            {place.nameTraditional && (
              // Traditional Mongolian script: vertical, rendered by the browser with a Mongolian font
              <div className="mongol-script" lang="mn-Mong">{place.nameTraditional}</div>
            )}
          </div>

          {hours && (
            <div className="sheet-row">
              {hours.openNow === true && <span className="badge open">Одоо нээлттэй</span>}
              {hours.openNow === false && <span className="badge closed">Одоо хаалттай</span>}
              <div className="muted pre">{hours.text}</div>
            </div>
          )}
          {place.address && <div className="sheet-row">📍 {place.address}{place.building && ` · ${place.building}`}</div>}
          {!place.address && place.building && <div className="sheet-row">🏢 {place.building}</div>}
          {place.phone && <div className="sheet-row">📞 <a href={`tel:${place.phone}`}>{place.phone}</a></div>}
          {(place.website || place.facebook || place.instagram) && (
            <div className="sheet-row links">
              {place.website && <a href={place.website} target="_blank" rel="noopener noreferrer">Вэбсайт</a>}
              {place.facebook && <a href={place.facebook} target="_blank" rel="noopener noreferrer">Facebook</a>}
              {place.instagram && <a href={place.instagram} target="_blank" rel="noopener noreferrer">Instagram</a>}
            </div>
          )}
          {place.links && place.links.length > 0 && (
            <div className="sheet-row links" style={{ flexWrap: 'wrap' }}>
              {place.links.map((l) => (
                <a key={l.url} href={l.url} target="_blank" rel="noopener noreferrer nofollow">{l.title || l.platform || 'Холбоос'}</a>
              ))}
            </div>
          )}

          <div className="sheet-actions">
            <button className="btn" onClick={openInMaps}>Google Maps-ээр очих</button>
            {onAddToRoute && <button className="btn secondary" onClick={onAddToRoute}>＋ Маршрутад нэмэх</button>}
            {user ? (
              <>
                <button className={`btn ${mark === 'SAVED' ? '' : 'secondary'}`} onClick={() => toggleMark('SAVED')}>
                  {mark === 'SAVED' ? '★ Хадгалсан' : '☆ Хадгалах'}
                </button>
                <button className="btn secondary" onClick={() => toggleMark('HIDDEN')}
                        title="Энэ газрыг дахин санал болгохгүй">
                  {mark === 'HIDDEN' ? 'Нуусан' : 'Нуух'}
                </button>
              </>
            ) : (
              <Link className="btn secondary" to="/login">Хадгалахын тулд нэвтрэх</Link>
            )}
          </div>
          {thanks && <div className="success" style={{ marginTop: 14 }}>{thanks}</div>}
          {user && !contributing && (
            <p className="muted small"><button className="link" onClick={() => { setThanks(null); setContributing(true) }}>
              Мэдээлэл буруу эсвэл дутуу юу? Засвар санал болгох</button></p>
          )}
          {contributing && (
            <ContributionForm place={place} onCancel={() => setContributing(false)}
                              onDone={(m) => { setContributing(false); setThanks(m) }} />
          )}
          <Reviews placeId={place.id} user={user} />
          <p className="muted small">© OpenStreetMap-ийн хувь нэмэрлэгчид</p>
        </>
      )}
    </aside>
  )
}
