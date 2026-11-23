import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api, ApiError, type User } from '../api'

type Review = {
  id: number
  placeId: number
  authorId: number
  authorName: string
  rating: number
  comment: string | null
  createdAt: string
  updatedAt: string | null
  hidden: boolean
  mine: boolean
}

function Stars({ value, onChange }: { value: number; onChange?: (v: number) => void }) {
  return (
    <span className={`stars ${onChange ? 'input' : ''}`} aria-label={`${value}/5`}>
      {[1, 2, 3, 4, 5].map((v) => onChange
        ? <button key={v} type="button" className={v <= value ? 'on' : ''} onClick={() => onChange(v)} aria-label={`${v}`}>★</button>
        : <span key={v} className={v <= value ? 'on' : ''}>★</span>)}
    </span>
  )
}

/** Газрын үнэлгээ, сэтгэгдэл (АШ16); модератор зохисгүйг нууж болно (АШ17). */
export default function Reviews({ placeId, user }: { placeId: number; user: User | null }) {
  const [reviews, setReviews] = useState<Review[]>([])
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [editing, setEditing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const moderator = user?.role === 'MODERATOR' || user?.role === 'ADMIN'

  const load = () => api<Review[]>(`/api/places/${placeId}/reviews`).then((list) => {
    setReviews(list)
    const own = list.find((r) => r.mine)
    setRating(own?.rating ?? 0)
    setComment(own?.comment ?? '')
    setEditing(false)
  }).catch(() => setReviews([]))

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [placeId, user])

  const own = reviews.find((r) => r.mine)
  const visible = reviews.filter((r) => !r.hidden)
  const average = visible.length ? visible.reduce((s, r) => s + r.rating, 0) / visible.length : 0

  const save = async () => {
    setError(null)
    try {
      await api(`/api/places/${placeId}/reviews`, { method: 'POST', body: { rating, comment } })
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    }
  }

  const remove = async () => {
    await api(`/api/places/${placeId}/reviews/mine`, { method: 'DELETE' })
    await load()
  }

  const toggleHidden = async (r: Review) => {
    await api(`/api/moderation/reviews/${r.id}/${r.hidden ? 'unhide' : 'hide'}`, { method: 'POST' })
    await load()
  }

  const showForm = user && (!own || editing)

  return (
    <div className="reviews">
      <div className="reviews-head">
        <h3>Үнэлгээ, сэтгэгдэл</h3>
        {visible.length > 0 && <span><Stars value={Math.round(average)} /> {average.toFixed(1)} ({visible.length})</span>}
      </div>

      {!user && <p className="muted"><Link to="/login">Нэвтэрч</Link> үнэлгээ өгнө үү.</p>}
      {showForm && (
        <div className="review-form">
          <Stars value={rating} onChange={setRating} />
          <textarea placeholder="Сэтгэгдэл (заавал биш)" maxLength={1000} rows={3} value={comment}
                    onChange={(e) => setComment(e.target.value)} />
          {error && <div className="error">{error}</div>}
          <div className="row" style={{ justifyContent: 'flex-start' }}>
            <button className="btn" disabled={rating === 0} onClick={save}>{own ? 'Хадгалах' : 'Илгээх'}</button>
            {editing && <button className="btn secondary" onClick={() => setEditing(false)}>Болих</button>}
          </div>
        </div>
      )}

      <ul className="review-list">
        {reviews.map((r) => (
          <li key={r.id} className={r.hidden ? 'hidden' : ''}>
            <div className="review-top">
              <strong>{r.mine ? 'Та' : r.authorName}</strong>
              <Stars value={r.rating} />
              <span className="muted">{new Date(r.createdAt).toLocaleDateString('mn-MN')}</span>
            </div>
            {r.comment && <div className="pre">{r.comment}</div>}
            {r.hidden && <div className="hint warn">Модератор нуусан{r.mine ? ': бусдад харагдахгүй' : ''}</div>}
            <div className="review-actions">
              {r.mine && !editing && <button className="link" onClick={() => setEditing(true)}>Засах</button>}
              {r.mine && <button className="link" onClick={remove}>Устгах</button>}
              {moderator && !r.mine && (
                <button className="link" onClick={() => toggleHidden(r)}>{r.hidden ? 'Дахин харуулах' : 'Нуух'}</button>
              )}
            </div>
          </li>
        ))}
      </ul>
      {reviews.length === 0 && <p className="muted">Одоогоор сэтгэгдэл алга.</p>}
    </div>
  )
}
