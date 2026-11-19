import { useEffect, useRef, useState, type ReactNode } from 'react'

const PHONE = '(max-width: 640px)'

/** Snap heights as a share of the screen height. */
const SNAPS = { peek: 0.22, half: 0.5, full: 0.92 }
type Snap = keyof typeof SNAPS

function usePhone() {
  const [phone, setPhone] = useState(() => window.matchMedia(PHONE).matches)
  useEffect(() => {
    const mq = window.matchMedia(PHONE)
    const onChange = () => setPhone(mq.matches)
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])
  return phone
}

/**
 * Side panel on wide screens; on phones a bottom sheet that can be dragged by its grip between
 * three heights (peek, half, almost full screen). Tapping the grip switches between half and full.
 */
export default function BottomSheet({ className, children, initial = 'half' }:
  { className: string; children: ReactNode; initial?: Snap }) {
  const phone = usePhone()
  const [snap, setSnap] = useState<Snap>(initial)
  const [dragHeight, setDragHeight] = useState<number | null>(null)
  const drag = useRef<{ startY: number; startH: number; moved: boolean } | null>(null)

  const heightOf = (s: Snap) => Math.round(window.innerHeight * SNAPS[s])

  const onPointerDown = (e: React.PointerEvent) => {
    drag.current = { startY: e.clientY, startH: dragHeight ?? heightOf(snap), moved: false }
    ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
  }

  const onPointerMove = (e: React.PointerEvent) => {
    const d = drag.current
    if (!d) return
    const dy = e.clientY - d.startY
    if (Math.abs(dy) > 4) d.moved = true
    const min = heightOf('peek') * 0.6
    const max = heightOf('full')
    setDragHeight(Math.min(max, Math.max(min, d.startH - dy)))
  }

  const onPointerUp = () => {
    const d = drag.current
    drag.current = null
    if (!d) return
    if (!d.moved) {
      // A tap on the grip
      setSnap(snap === 'full' ? 'half' : 'full')
      setDragHeight(null)
      return
    }
    // Snap to the closest height
    const h = dragHeight ?? heightOf(snap)
    const closest = (Object.keys(SNAPS) as Snap[])
      .reduce((best, s) => (Math.abs(heightOf(s) - h) < Math.abs(heightOf(best) - h) ? s : best), 'half' as Snap)
    setSnap(closest)
    setDragHeight(null)
  }

  const style = phone
    ? { height: dragHeight ?? heightOf(snap), transition: dragHeight == null ? 'height 0.22s ease' : 'none' }
    : undefined

  return (
    <aside className={`${className} ${phone ? 'is-sheet' : ''}`} style={style}>
      {phone && (
        <div className="sheet-grip" role="separator" aria-label="Чирж томруулах, жижигрүүлэх"
             onPointerDown={onPointerDown} onPointerMove={onPointerMove} onPointerUp={onPointerUp}
             onPointerCancel={onPointerUp}>
          <span />
        </div>
      )}
      <div className="sheet-body">{children}</div>
    </aside>
  )
}
