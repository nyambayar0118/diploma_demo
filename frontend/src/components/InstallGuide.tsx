import { useEffect, useState, type ReactNode } from 'react'
import { canPromptInstall, INSTALL_CHANGED, platform, promptInstall } from '../pwa'

/** iOS-ийн Share дүрс (сумтай дөрвөлжин), Safari-д байдагтай адил. */
const ShareIos = () => (
  <svg className="inline-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
       strokeLinecap="round" strokeLinejoin="round" aria-label="Хуваалцах">
    <path d="M8 10H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8a2 2 0 0 0-2-2h-2M12 15V2M8 6l4-4 4 4" />
  </svg>
)
const Dots = () => <span className="inline-key">⋮</span>
const AddBox = () => <span className="inline-key">⊞</span>

function Steps({ items }: { items: ReactNode[] }) {
  return (
    <ol className="install-steps">
      {items.map((s, i) => <li key={i}><span className="step-n">{i + 1}</span><span>{s}</span></li>)}
    </ol>
  )
}

/** Хэрэглэгчийн утас, хөтөчид тохирсон, веб аппыг нүүр дэлгэцэд нэмэх заавар. */
export default function InstallGuide({ onClose }: { onClose: () => void }) {
  const [p] = useState(platform)
  const [canPrompt, setCanPrompt] = useState(canPromptInstall)
  const [tab, setTab] = useState<'ios' | 'android'>(p === 'android' ? 'android' : 'ios')

  useEffect(() => {
    const on = () => setCanPrompt(canPromptInstall())
    window.addEventListener(INSTALL_CHANGED, on)
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => {
      window.removeEventListener(INSTALL_CHANGED, on)
      window.removeEventListener('keydown', onKey)
    }
  }, [onClose])

  const install = async () => {
    if (await promptInstall()) onClose()
  }

  const show = p === 'ios' || p === 'android' ? p : tab

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" role="dialog" aria-label="Утсандаа суулгах" onClick={(e) => e.stopPropagation()}>
        <button className="sheet-close" onClick={onClose} aria-label="Хаах">×</button>
        <div className="install-head">
          <img src="/icon-192.png" alt="" width={56} height={56} />
          <div>
            <h2>Утсандаа суулгах</h2>
            <p className="muted">Нүүр дэлгэцээс апп шиг нээгдэнэ: хөтчийн хаягийн мөргүй, бүтэн дэлгэцээр, хурдан.
              App Store, Play Store хэрэггүй.</p>
          </div>
        </div>

        {p === 'installed' && <div className="success">Апп аль хэдийн суусан байна.</div>}

        {p === 'in-app' && (
          <>
            <div className="hint warn install-warn">
              Та Messenger / Facebook зэрэг аппын дотор нээсэн байна. Эндээс суулгах боломжгүй.
            </div>
            <Steps items={[
              <>Баруун дээд (эсвэл доод) булангийн <Dots /> эсвэл <ShareIos /> товчийг дарна.</>,
              <>“<b>Safari-д нээх</b>” (iPhone) эсвэл “<b>Chrome-д нээх</b>” (Android)-ийг сонгоно.</>,
              <>Нээгдсэн хуудсан дээр цэсээс “Утсандаа суулгах”-ыг дахин нээнэ.</>,
            ]} />
          </>
        )}

        {canPrompt && p !== 'installed' && (
          <button className="btn block install-btn" onClick={install}>Суулгах</button>
        )}

        {(p === 'desktop' || p === 'ios' || p === 'android') && !canPrompt && (
          <>
            {p === 'desktop' && (
              <>
                <p className="muted" style={{ marginTop: 0 }}>Утсандаа суулгах заавар:</p>
                <div className="seg" style={{ marginBottom: 12 }}>
                  <button className={tab === 'ios' ? 'on' : ''} onClick={() => setTab('ios')}>iPhone / iPad</button>
                  <button className={tab === 'android' ? 'on' : ''} onClick={() => setTab('android')}>Android</button>
                </div>
              </>
            )}
            {show === 'ios' ? (
              <Steps items={[
                <><b>Safari</b>-аар {window.location.host} хаягийг нээнэ.</>,
                <>Доод талын <ShareIos /> <b>Хуваалцах</b> товчийг дарна (iPad дээр баруун дээд буланд).</>,
                <>Жагсаалтыг доош гүйлгээд <AddBox /> “<b>Нүүр дэлгэцэнд нэмэх</b>” (Add to Home Screen)-ийг сонгоно.</>,
                <>Баруун дээд буланд “<b>Нэмэх</b>” (Add) дарна. Нүүр дэлгэц дээр “Маршрут” апп гарна.</>,
              ]} />
            ) : (
              <Steps items={[
                <><b>Chrome</b>-оор {window.location.host} хаягийг нээнэ.</>,
                <>Баруун дээд буланд <Dots /> цэсийг дарна.</>,
                <>“<b>Апп суулгах</b>” (Install app) эсвэл “<b>Нүүр дэлгэцэнд нэмэх</b>” (Add to Home screen)-ийг сонгоно.</>,
                <>“<b>Суулгах</b>” дарна. Апп таны бусад аппын дунд гарна.</>,
              ]} />
            )}
            {p === 'desktop' && (
              <p className="muted small">Компьютерт: Chrome / Edge-ийн хаягийн мөрийн баруун талын суулгах дүрсийг дарна.</p>
            )}
          </>
        )}

        <p className="muted small install-note">
          Суулгасны дараа байршил асуухад “Зөвшөөрөх” дарвал санал болголт таны яг байгаа газраас тооцогдоно.
        </p>
      </div>
    </div>
  )
}
