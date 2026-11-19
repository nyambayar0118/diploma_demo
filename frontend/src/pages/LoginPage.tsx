import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, ApiError, rememberNext, takeNext, type OnboardingStatus } from '../api'
import { useAuth } from '../auth'
import GoogleButton from '../components/GoogleButton'

export default function LoginPage() {
  const { login, loginWithGoogle } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  useEffect(() => rememberNext(window.location.search), [])

  const run = async (doLogin: () => Promise<unknown>) => {
    setBusy(true)
    setError(null)
    try {
      await doLogin()
      const status = await api<OnboardingStatus>('/api/onboarding')
      navigate(status.completed ? takeNext() : '/onboarding', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    } finally {
      setBusy(false)
    }
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    run(() => login(email, password))
  }

  return (
    <div className="page">
      <form className="card" onSubmit={submit}>
        <h1>Нэвтрэх</h1>
        <p className="lead">Сонирхолд тань нийцсэн газруудыг санал болгоё.</p>
        {error && <div className="error">{error}</div>}
        <div className="field">
          <label htmlFor="email">Имэйл</label>
          <input id="email" type="email" autoComplete="email" required value={email}
                 onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="password">Нууц үг</label>
          <input id="password" type="password" autoComplete="current-password" required value={password}
                 onChange={(e) => setPassword(e.target.value)} />
        </div>
        <button className="btn block" disabled={busy}>{busy ? 'Түр хүлээнэ үү…' : 'Нэвтрэх'}</button>
        <GoogleButton onCredential={(c) => run(() => loginWithGoogle(c))} />
        <p className="muted" style={{ textAlign: 'center', marginTop: 16 }}>
          Бүртгэлгүй юу? <Link to={`/register${window.location.search}`}>Бүртгүүлэх</Link> · <Link to="/">Газрын зураг</Link>
        </p>
      </form>
    </div>
  )
}
