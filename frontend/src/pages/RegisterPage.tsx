import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, ApiError, rememberNext, takeNext, type OnboardingStatus } from '../api'
import { useAuth } from '../auth'
import GoogleButton from '../components/GoogleButton'

/** Бүртгүүлэх хуудас: имэйл, нууц үг эсвэл Google хаягаар; дараа нь бүртгэлийн асуулга руу шилжинэ. */
export default function RegisterPage() {
  const { register, loginWithGoogle } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ displayName: '', username: '', email: '', password: '' })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  useEffect(() => rememberNext(window.location.search), [])

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [key]: e.target.value })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await register(form)
      navigate('/onboarding', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    } finally {
      setBusy(false)
    }
  }

  const google = async (credential: string) => {
    setBusy(true)
    setError(null)
    try {
      await loginWithGoogle(credential)
      const status = await api<OnboardingStatus>('/api/onboarding')
      navigate(status.completed ? takeNext() : '/onboarding', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <form className="card" onSubmit={submit}>
        <h1>Бүртгүүлэх</h1>
        <p className="lead">Бүртгүүлсний дараа сонирхлоо сонгоно.</p>
        {error && <div className="error">{error}</div>}
        <div className="field">
          <label htmlFor="displayName">Таны нэр</label>
          <input id="displayName" required maxLength={100} value={form.displayName} onChange={set('displayName')} />
        </div>
        <div className="field">
          <label htmlFor="username">Хэрэглэгчийн нэр</label>
          <input id="username" required pattern="[A-Za-z0-9_.]{3,30}" value={form.username} onChange={set('username')} />
          <span className="hint">3–30 латин үсэг, тоо, “_” эсвэл “.”. Найзууд тань үүгээр хайж олно.</span>
        </div>
        <div className="field">
          <label htmlFor="email">Имэйл</label>
          <input id="email" type="email" autoComplete="email" required value={form.email} onChange={set('email')} />
        </div>
        <div className="field">
          <label htmlFor="password">Нууц үг</label>
          <input id="password" type="password" autoComplete="new-password" required minLength={8}
                 value={form.password} onChange={set('password')} />
          <span className="hint">Дор хаяж 8 тэмдэгт.</span>
        </div>
        <button className="btn block" disabled={busy}>{busy ? 'Түр хүлээнэ үү…' : 'Бүртгүүлэх'}</button>
        <GoogleButton onCredential={google} />
        <p className="muted" style={{ textAlign: 'center', marginTop: 16 }}>
          Бүртгэлтэй юу? <Link to="/login">Нэвтрэх</Link>
        </p>
      </form>
    </div>
  )
}
