import { useEffect, useState } from 'react'
import { bannerDismissed, dismissBanner, platform } from '../pwa'

/** Утсанд сайтыг суулгаж болохыг нэг удаа сануулна; хаавал дахин гарахгүй. */
export default function InstallBanner({ onOpenGuide }: { onOpenGuide: () => void }) {
  const [show, setShow] = useState(false)

  useEffect(() => {
    const p = platform()
    if (p === 'installed' || p === 'desktop' || bannerDismissed()) return
    const t = window.setTimeout(() => setShow(true), 8000) // хуудас нээгдсэн эхний секундүүдэд биш
    return () => window.clearTimeout(t)
  }, [])

  if (!show) return null
  const close = () => {
    dismissBanner()
    setShow(false)
  }
  return (
    <div className="install-banner" role="status">
      <img src="/icon-192.png" alt="" width={36} height={36} />
      <span className="r-main">
        <span className="r-name">Утсандаа суулгах</span>
        <span className="r-sub">Нүүр дэлгэцээс апп шиг нээнэ</span>
      </span>
      <button className="btn" onClick={() => { close(); onOpenGuide() }}>Заавар</button>
      <button className="install-x" onClick={close} aria-label="Хаах">×</button>
    </div>
  )
}
