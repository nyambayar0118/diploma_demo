import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api, ApiError, LINK_TYPE_LABELS, REQUEST_STATUS_LABELS, REQUEST_TYPE_LABELS, type ChangeRequest,
  type ProposedData } from '../api'

const FIELD_LABELS: Partial<Record<keyof ProposedData, string>> = {
  nameCyrillic: 'Нэр', nameLatin: 'Латин нэр', categoryCode: 'Ангилал', phone: 'Утас', openingHours: 'Ажиллах цаг',
  street: 'Хаяг', priceLevel: 'Үнэ', permanentlyClosed: 'Хаагдсан', url: 'Холбоос', linkType: 'Төрөл', linkTitle: 'Гарчиг',
}

/** Current value of the target place for a proposed field, to show what would change. */
function current(r: ChangeRequest, key: keyof ProposedData): string | null {
  const t = r.target
  if (!t) return null
  const map: Partial<Record<keyof ProposedData, unknown>> = {
    nameCyrillic: t.name, nameLatin: t.nameLatin, categoryCode: t.category, phone: t.phone,
    openingHours: t.openingHours, street: t.street, priceLevel: t.priceLevel,
  }
  const v = map[key]
  return v == null ? null : String(v)
}

/** Moderation queue (АШ06): approve or reject users' requests with a reason. */
export default function ModerationPage() {
  const [status, setStatus] = useState<'PENDING' | 'APPROVED' | 'REJECTED'>('PENDING')
  const [requests, setRequests] = useState<ChangeRequest[]>([])
  const [comments, setComments] = useState<Record<number, string>>({})
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    api<ChangeRequest[]>(`/api/moderation/requests?status=${status}`).then(setRequests)
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Сервертэй холбогдсонгүй'))
  }, [status])
  useEffect(() => {
    load()
  }, [load])

  const decide = async (r: ChangeRequest, action: 'approve' | 'reject') => {
    setError(null)
    try {
      await api(`/api/moderation/requests/${r.id}/${action}`, { method: 'POST', body: { comment: comments[r.id] ?? '' } })
      load()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Сервертэй холбогдсонгүй')
    }
  }

  return (
    <div className="page">
      <div className="card wide">
        <div className="row">
          <h1>Хүсэлт хянах</h1>
          <Link className="btn secondary" to="/">Газрын зураг</Link>
        </div>
        <div className="chips" style={{ margin: '12px 0' }}>
          {(['PENDING', 'APPROVED', 'REJECTED'] as const).map((s) => (
            <button key={s} className={`chip ${status === s ? 'on' : ''}`} onClick={() => setStatus(s)}>{REQUEST_STATUS_LABELS[s]}</button>
          ))}
        </div>
        {error && <div className="error">{error}</div>}
        {requests.length === 0 && <p className="muted">Хүсэлт алга.</p>}
        {requests.map((r) => (
          <div key={r.id} className="request">
            <div className="row">
              <strong>{REQUEST_TYPE_LABELS[r.type]}{r.target ? `: ${r.target.name}` : ''}</strong>
              <span className="muted">{r.submitterName ?? 'Устгасан хэрэглэгч'} · {new Date(r.submittedAt).toLocaleString('mn-MN')}</span>
            </div>
            <table className="diff">
              <tbody>
                {(Object.keys(r.data) as (keyof ProposedData)[]).filter((k) => r.data[k] != null && k !== 'lat' && k !== 'lng').map((k) => (
                  <tr key={k}>
                    <th>{FIELD_LABELS[k] ?? k}</th>
                    {r.target && <td className="old">{current(r, k) ?? '—'}</td>}
                    <td className="new">
                      {k === 'url' ? <a href={String(r.data.url)} target="_blank" rel="noopener noreferrer nofollow">{String(r.data.url)}</a>
                        : k === 'linkType' ? LINK_TYPE_LABELS[r.data.linkType!]
                        : k === 'permanentlyClosed' ? 'Тийм' : String(r.data[k])}
                    </td>
                  </tr>
                ))}
                {r.data.lat != null && r.data.lng != null && (
                  <tr>
                    <th>Байршил</th>
                    {r.target && <td className="old">{r.target.lat.toFixed(5)}, {r.target.lng.toFixed(5)}</td>}
                    <td className="new">
                      <a href={`https://www.google.com/maps/search/?api=1&query=${r.data.lat},${r.data.lng}`} target="_blank" rel="noopener noreferrer">
                        {r.data.lat.toFixed(5)}, {r.data.lng.toFixed(5)}
                      </a>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
            {r.target && <p className="muted"><Link to={`/?place=${r.target.id}`}>Газрын зураг дээр харах</Link></p>}
            {r.status === 'PENDING' ? (
              <div className="row" style={{ justifyContent: 'flex-start', flexWrap: 'wrap' }}>
                <input className="comment" placeholder="Тайлбар (татгалзах бол заавал)" value={comments[r.id] ?? ''}
                       onChange={(e) => setComments({ ...comments, [r.id]: e.target.value })} />
                <button className="btn" onClick={() => decide(r, 'approve')}>Батлах</button>
                <button className="btn danger" onClick={() => decide(r, 'reject')}>Татгалзах</button>
              </div>
            ) : r.moderatorComment && <p className="muted">Тайлбар: {r.moderatorComment}</p>}
          </div>
        ))}
      </div>
    </div>
  )
}
