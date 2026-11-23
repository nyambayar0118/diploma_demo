import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { api, googleMapsUrl, logInteraction, type MarkType, type PlaceDetails } from '../api'
import { useAuth } from '../auth'
import { describeOpeningHours, type OpeningInfo } from '../openingHours'
import { MAIN_CATEGORY_COLORS } from '../places'
import Reviews from './Reviews'
import ContributionForm from './ContributionForm'
import BottomSheet from './BottomSheet'
import Icon, { type IconName } from './Icon'

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

  const navigate = useNavigate()
  const [copied, setCopied] = useState(false)

  const share = async () => {
    if (!place) return
    const url = `${window.location.origin}/?place=${place.id}`
    if (navigator.share) {
      navigator.share({ title: place.name, text: `${place.name} · ${place.categoryName}`, url }).catch(() => undefined)
    } else {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    }
  }

  const openInMaps = () => {
    if (!place) return
    logInteraction(place.id, 'OPEN_IN_MAPS', recommendationId)
    window.open(googleMapsUrl(place.lat, place.lng), '_blank', 'noopener')
  }

  const color = place ? MAIN_CATEGORY_COLORS[place.mainCategory] ?? '#888' : '#888'
  const host = (url: string) => {
    try {
      return new URL(url).hostname.replace(/^www\./, '')
    } catch {
      return url
    }
  }

  return (
    <BottomSheet className="sheet">
      <button className="sheet-close" onClick={onClose} aria-label="Хаах">×</button>
      {error && <div className="error">{error}</div>}
      {!place && !error && (
        <div className="ps-skeleton" aria-label="Ачаалж байна"><span /><span /><span /></div>
      )}
      {place && (
        <>
          <div className="ps-head">
            <div className="ps-titles">
              <span className="ps-cat" style={{ color }}>
                <span className="dot" style={{ background: color }} />{place.categoryName}
              </span>
              <h2 className="ps-name">{place.name}</h2>
              {place.nameLatin && place.nameLatin !== place.name && <div className="ps-latin">{place.nameLatin}</div>}
              <div className="ps-meta">
                {place.reviewCount > 0 && (
                  <span className="ps-rating">
                    <span className="star">★</span> {place.averageRating.toFixed(1)}
                    <span className="muted"> ({place.reviewCount})</span>
                  </span>
                )}
                {hours?.openNow === true && <span className="ps-open">Нээлттэй</span>}
                {hours?.openNow === false && <span className="ps-closed">Хаалттай</span>}
                {hours?.next && <span className="muted">· {hours.next}</span>}
              </div>
            </div>
            {place.nameTraditional && (
              // Traditional Mongolian script: vertical, rendered by the browser with a Mongolian font
              <div className="mongol-script" lang="mn-Mong">{place.nameTraditional}</div>
            )}
          </div>

          <div className="ps-actions">
            <Action icon="navigation" label="Чиглэл" primary onClick={openInMaps} />
            <Action icon="bookmark" label={mark === 'SAVED' ? 'Хадгалсан' : 'Хадгалах'} active={mark === 'SAVED'}
                    onClick={() => (user ? toggleMark('SAVED') : navigate('/login'))} />
            {onAddToRoute && <Action icon="plus" label="Маршрутад" onClick={onAddToRoute} />}
            <Action icon="share" label={copied ? 'Хуулсан' : 'Хуваалцах'} onClick={share} />
            {user && (
              <Action icon="eyeOff" label={mark === 'HIDDEN' ? 'Нуусан' : 'Нуух'} active={mark === 'HIDDEN'}
                      title="Энэ газрыг дахин санал болгохгүй" onClick={() => toggleMark('HIDDEN')} />
            )}
          </div>

          <ul className="ps-info">
            {hours && (
              <li>
                <Icon name="clock" />
                <details className="ps-hours">
                  <summary>
                    {hours.openNow === true ? 'Одоо нээлттэй' : hours.openNow === false ? 'Одоо хаалттай' : 'Цагийн хуваарь'}
                    {hours.next && <span className="muted"> · {hours.next}</span>}
                    <Icon name="chevron" size={16} />
                  </summary>
                  <ul>{hours.lines.map((l) => <li key={l}>{l}</li>)}</ul>
                </details>
              </li>
            )}
            {(place.address || place.building) && (
              <li>
                <Icon name="pin" />
                <span>
                  {place.address ?? place.building}
                  {place.address && place.building && <span className="muted"> · {place.building}</span>}
                </span>
              </li>
            )}
            {place.phone && (
              <li><Icon name="phone" /><a href={`tel:${place.phone}`}>{place.phone}</a></li>
            )}
            {place.website && (
              <li><Icon name="globe" /><a href={place.website} target="_blank" rel="noopener noreferrer">{host(place.website)}</a></li>
            )}
            {(place.facebook || place.instagram || (place.links?.length ?? 0) > 0) && (
              <li>
                <Icon name="link" />
                <span className="ps-links">
                  {place.facebook && <a href={place.facebook} target="_blank" rel="noopener noreferrer">Facebook</a>}
                  {place.instagram && <a href={place.instagram} target="_blank" rel="noopener noreferrer">Instagram</a>}
                  {place.links?.map((l) => (
                    <a key={l.url} href={l.url} target="_blank" rel="noopener noreferrer nofollow">{l.title || l.platform || host(l.url)}</a>
                  ))}
                </span>
              </li>
            )}
          </ul>

          {thanks && <div className="success">{thanks}</div>}
          {user && !contributing && (
            <button className="ps-suggest" onClick={() => { setThanks(null); setContributing(true) }}>
              <Icon name="edit" size={16} /> Мэдээлэл буруу эсвэл дутуу юу? Засвар санал болгох
            </button>
          )}
          {contributing && (
            <ContributionForm place={place} onCancel={() => setContributing(false)}
                              onDone={(m) => { setContributing(false); setThanks(m) }} />
          )}
          <Reviews placeId={place.id} user={user} />
          <p className="muted small">
            {place.source === 'MINU' ? '© Minu Map'
              : place.source === 'USER' ? 'Хэрэглэгчийн нэмсэн газар'
              : place.fromMinu ? '© OpenStreetMap-ийн хувь нэмэрлэгчид, © Minu Map'
              : '© OpenStreetMap-ийн хувь нэмэрлэгчид'}
          </p>
        </>
      )}
    </BottomSheet>
  )
}

function Action({ icon, label, onClick, primary, active, title }: {
  icon: IconName; label: string; onClick: () => void; primary?: boolean; active?: boolean; title?: string
}) {
  return (
    <button type="button" className={`ps-action ${primary ? 'primary' : ''} ${active ? 'active' : ''}`}
            onClick={onClick} title={title ?? label}>
      <span className="ps-action-icon"><Icon name={icon} /></span>
      <span className="ps-action-label">{label}</span>
    </button>
  )
}
