import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { api, ApiError, type Category, type ConsentType, type OnboardingStatus, takeNext } from '../api'
import { MAIN_CATEGORY_COLORS } from '../places'

const MIN_SELECTED = 3

const CONSENT_TEXT: { type: ConsentType; title: string; desc: string; required?: boolean }[] = [
  { type: 'INTERESTS', title: 'Сонирхлоо ашиглуулах',
    desc: 'Таны сонгосон ангилал, үйлдлээр сонирхолд тань нийцсэн газар санал болгоно. Санал болголт гаргахад заавал шаардлагатай.',
    required: true },
  { type: 'LOCATION', title: 'Байршлаа ашиглуулах',
    desc: 'Ойролцоох газрыг санал болгох, маршрутын хугацааг тооцоход одоогийн байршлыг ашиглана. 30 хоног хадгална.' },
  { type: 'INTERACTION_HISTORY', title: 'Үйлдлийн түүхээ ашиглуулах',
    desc: 'Аль газрыг хадгалсан, очсон зэрэг үйлдлийг санал болголтын загварыг сайжруулахад ашиглана.' },
]

/** Бүртгэлийн асуулга (ДШ02, хүйтэн эхлэл): сонирхсон ангилал, тээврийн хэрэгсэл, хувийн мэдээллийн зөвшөөрөл. */
export default function OnboardingPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<1 | 2>(1)
  const [categories, setCategories] = useState<Category[]>([])
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [consents, setConsents] = useState<Set<ConsentType>>(new Set(['INTERESTS', 'LOCATION', 'INTERACTION_HISTORY']))
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api<Category[]>('/api/categories').then(setCategories).catch(() => setError('Ангиллыг ачаалж чадсангүй'))
    // Асуулгыг дахин бөглөхөд өмнөх сонголтоос эхэлнэ
    api<OnboardingStatus>('/api/onboarding').then((s) => s.completed && setSelected(new Set(s.categoryCodes)))
  }, [])

  const toggle = <T,>(set: Set<T>, value: T) => {
    const next = new Set(set)
    if (next.has(value)) next.delete(value)
    else next.add(value)
    return next
  }

  const finish = async () => {
    setBusy(true)
    setError(null)
    try {
      await api('/api/onboarding', { method: 'POST', body: { categoryCodes: [...selected], consents: [...consents] } })
      navigate(takeNext(), { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <div className="card wide">
        <div className="steps"><span className="done" /><span className={step === 2 ? 'done' : ''} /></div>
        {error && <div className="error">{error}</div>}

        {step === 1 && (
          <>
            <h1>Юу сонирхдог вэ?</h1>
            <p className="lead">Дор хаяж {MIN_SELECTED} ангилал сонгоно уу. Дараа нь профайлаасаа өөрчилж болно.</p>
            {categories.map((main) => {
              const subs = (main.children ?? []).filter((c) => c.recommendable)
              if (subs.length === 0) return null
              return (
                <div className="group" key={main.code}>
                  <div className="group-title">
                    <span className="dot" style={{ background: MAIN_CATEGORY_COLORS[main.code] ?? '#888' }} />
                    {main.nameMn}
                  </div>
                  <div className="chips">
                    {subs.map((c) => (
                      <button type="button" key={c.code} className={`chip ${selected.has(c.code) ? 'on' : ''}`}
                              onClick={() => setSelected(toggle(selected, c.code))}>
                        {c.nameMn}
                      </button>
                    ))}
                  </div>
                </div>
              )
            })}
            <div className="row" style={{ marginTop: 20 }}>
              <span className="muted">{selected.size} сонгосон</span>
              <button className="btn" disabled={selected.size < MIN_SELECTED} onClick={() => setStep(2)}>
                Үргэлжлүүлэх
              </button>
            </div>
          </>
        )}

        {step === 2 && (
          <>
            <h1>Хувийн мэдээлэл</h1>
            <p className="lead">Таны мэдээллийг зөвхөн доорх зорилгоор, таны зөвшөөрлөөр ашиглана. Профайлаасаа хэзээ ч цуцалж болно.</p>
            {CONSENT_TEXT.map((c) => (
              <label className="toggle" key={c.type}>
                <input type="checkbox" checked={consents.has(c.type)} disabled={c.required}
                       onChange={() => setConsents(toggle(consents, c.type))} />
                <span>
                  <div className="t-title">{c.title}{c.required && ' (заавал)'}</div>
                  <div className="t-desc">{c.desc}</div>
                </span>
              </label>
            ))}
            <div className="row" style={{ marginTop: 20 }}>
              <button className="btn secondary" onClick={() => setStep(1)}>Буцах</button>
              <button className="btn" disabled={busy} onClick={finish}>{busy ? 'Хадгалж байна…' : 'Дуусгах'}</button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
