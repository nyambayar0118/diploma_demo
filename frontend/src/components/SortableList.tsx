import { useRef, useState, type ReactNode } from 'react'

type Drag = { from: number; to: number; dy: number; settling: boolean }

/**
 * Бариулаар нь (хулгана эсвэл хүрэлтээр) чирч болох босоо жагсаалт. Чирэх үед бусад мөр зай тавьж хоосон байрыг
 * харуулна; суллахад мөр тийшээ гулсаж ороод onReorder дуудагдана.
 */
export default function SortableList<T>({ items, keyOf, enabled, onReorder, renderItem, className }: {
  items: T[]
  keyOf: (item: T) => string | number
  enabled: boolean
  onReorder: (from: number, to: number) => void
  /** `handle`-ийг мөр дотор зурах ёстой; чирэлтийг тэр эхлүүлнэ. */
  renderItem: (item: T, index: number, handle: ReactNode, dragging: boolean) => ReactNode
  className?: string
}) {
  const refs = useRef<(HTMLLIElement | null)[]>([])
  const start = useRef<{ y: number; rects: DOMRect[] } | null>(null)
  const [drag, setDrag] = useState<Drag | null>(null)
  const [instant, setInstant] = useState(false)

  const begin = (index: number) => (e: React.PointerEvent) => {
    if (!enabled || drag?.settling) return
    e.preventDefault()
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
    start.current = { y: e.clientY, rects: refs.current.slice(0, items.length).map((el) => el!.getBoundingClientRect()) }
    setDrag({ from: index, to: index, dy: 0, settling: false })
  }

  const move = (e: React.PointerEvent) => {
    const s = start.current
    if (!s || !drag || drag.settling) return
    const dy = e.clientY - s.y
    const r = s.rects[drag.from]
    const center = r.top + r.height / 2 + dy
    // Шинэ байр = бусад мөрийн хэд нь чирж буй мөрийн голоос дээр голтой байгаа тоо
    let to = 0
    s.rects.forEach((o, i) => {
      if (i !== drag.from && o.top + o.height / 2 < center) to++
    })
    setDrag({ ...drag, to, dy })
  }

  const end = () => {
    const s = start.current
    if (!s || !drag || drag.settling) return
    const { from, to } = drag
    if (from === to) {
      start.current = null
      setDrag(null)
      return
    }
    // Хоосон байр руу гулсаж ороод шинэ дарааллыг баталгаажуулна
    const between = from < to ? s.rects.slice(from + 1, to + 1) : s.rects.slice(to, from)
    const target = between.reduce((sum, r) => sum + r.height, 0) * (from < to ? 1 : -1)
    setDrag({ ...drag, dy: target, settling: true })
    window.setTimeout(() => {
      start.current = null
      setInstant(true) // байрлал DOM-д одоо өөрчлөгдөнө: энэ кадрт шилжилтгүй
      setDrag(null)
      onReorder(from, to)
      requestAnimationFrame(() => requestAnimationFrame(() => setInstant(false)))
    }, 200)
  }

  const offset = (i: number) => {
    if (!drag || !start.current) return 0
    const { from, to } = drag
    if (i === from) return drag.dy
    const h = start.current.rects[from].height
    if (from < to && i > from && i <= to) return -h
    if (to < from && i >= to && i < from) return h
    return 0
  }

  return (
    <ol className={`sortable ${className ?? ''} ${instant ? 'instant' : ''} ${enabled ? 'has-handles' : ''}`}>
      {items.map((item, i) => {
        const dragging = drag?.from === i
        const handle = enabled ? (
          <span className="drag-handle" onPointerDown={begin(i)} onPointerMove={move} onPointerUp={end}
                onPointerCancel={end} aria-label="Чирж дараалал солих" role="button" tabIndex={-1}>
            <svg width="14" height="20" viewBox="0 0 14 20" aria-hidden>
              {[4, 10, 16].map((y) => [4, 10].map((x) => <circle key={`${x}${y}`} cx={x} cy={y} r="1.6" fill="currentColor" />))}
            </svg>
          </span>
        ) : null
        return (
          <li key={keyOf(item)} ref={(el) => { refs.current[i] = el }}
              className={`${dragging ? 'dragging' : ''} ${drag && !dragging ? 'shifting' : ''} ${drag?.settling && dragging ? 'settling' : ''}`}
              style={{ transform: offset(i) ? `translateY(${offset(i)}px)` : undefined }}>
            {renderItem(item, i, handle, dragging)}
          </li>
        )
      })}
    </ol>
  )
}
