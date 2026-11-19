import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api, ApiError, type UserRef } from '../api'
import { useAuth } from '../auth'

/** Opened from a friend's invite link: accept to become friends right away. */
export default function FriendInvitePage() {
  const { token = '' } = useParams()
  const { user, loading } = useAuth()
  const navigate = useNavigate()
  const [inviter, setInviter] = useState<UserRef | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<UserRef>(`/api/friends/invites/${token}`).then(setInviter)
      .catch((e) => setError(e instanceof ApiError ? e.message : 'Сервертэй холбогдсонгүй'))
  }, [token])

  const accept = async () => {
    try {
      await api(`/api/friends/invites/${token}/accept`, { method: 'POST' })
      navigate('/friends', { replace: true })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Сервертэй холбогдсонгүй')
    }
  }

  const next = encodeURIComponent(`/invite/friend/${token}`)
  return (
    <div className="page">
      <div className="card">
        <h1>Найзын урилга</h1>
        {error && <div className="error">{error}</div>}
        {inviter && (
          <>
            <p className="lead"><strong>{inviter.displayName}</strong> (@{inviter.username}) таныг найзаар урьж байна.</p>
            {loading ? null : user ? (
              user.id === inviter.id
                ? <p className="muted">Энэ бол таны өөрийн урилга. Холбоосыг найздаа илгээнэ үү.</p>
                : <button className="btn block" onClick={accept}>Найз болох</button>
            ) : (
              <>
                <Link className="btn block" to={`/login?next=${next}`}>Нэвтэрч зөвшөөрөх</Link>
                <p className="muted" style={{ textAlign: 'center' }}>
                  Бүртгэлгүй юу? <Link to={`/register?next=${next}`}>Бүртгүүлэх</Link>
                </p>
              </>
            )}
          </>
        )}
        <p className="muted" style={{ textAlign: 'center', marginTop: 16 }}><Link to="/">Газрын зураг</Link></p>
      </div>
    </div>
  )
}
