import { useEffect, useMemo, useRef } from 'react'
import { MARKER_STYLES, markerPreview, type MarkerStyle } from '../markerShapes'
import { MAIN_CATEGORY_COLORS } from '../places'
import { useTheme } from '../theme'

type Props = {
  /** Bottom-right corner of the map button the panel opens from. */
  anchor: { right: number; bottom: number }
  markerStyle: MarkerStyle
  onMarkerStyle: (s: MarkerStyle) => void
  onClose: () => void
}

const PREVIEW_COLORS = [MAIN_CATEGORY_COLORS.food, MAIN_CATEGORY_COLORS.culture, MAIN_CATEGORY_COLORS.nature]

/** Map look: marker shape and dark mode (opened from the layers button on the map). */
export default function MapStyleMenu({ anchor, markerStyle, onMarkerStyle, onClose }: Props) {
  const [theme, setTheme] = useTheme()
  const ref = useRef<HTMLDivElement>(null)
  const previews = useMemo(
    () => Object.fromEntries(MARKER_STYLES.map((s) => [s.id, PREVIEW_COLORS.map((c) => markerPreview(s.id, c, 18))])),
    [],
  )

  // Closes on a click outside or Escape
  useEffect(() => {
    const onDown = (e: PointerEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose()
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    const t = window.setTimeout(() => document.addEventListener('pointerdown', onDown), 0)
    document.addEventListener('keydown', onKey)
    return () => {
      window.clearTimeout(t)
      document.removeEventListener('pointerdown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [onClose])

  return (
    <div ref={ref} className="map-style-pop" style={{ right: anchor.right, bottom: anchor.bottom }} role="dialog"
         aria-label="Газрын зургийн харагдац">
      <div className="msp-title">Газрын тэмдэглэгээ</div>
      <div className="msp-options">
        {MARKER_STYLES.map((s) => (
          <button key={s.id} className={`msp-option ${markerStyle === s.id ? 'on' : ''}`} onClick={() => onMarkerStyle(s.id)}>
            <span className="msp-preview">
              {previews[s.id].map((src, i) => <img key={i} src={src} alt="" width={18} height={18} />)}
            </span>
            <span>{s.label}</span>
          </button>
        ))}
      </div>
      <label className="msp-row">
        <span>Харанхуй горим</span>
        <input type="checkbox" className="switch" checked={theme === 'dark'}
               onChange={(e) => setTheme(e.target.checked ? 'dark' : 'light')} />
      </label>
    </div>
  )
}
