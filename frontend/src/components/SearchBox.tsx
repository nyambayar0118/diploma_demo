import { useEffect, useRef, useState } from 'react'
import { api, type PlaceSummary } from '../api'
import { MAIN_CATEGORY_COLORS } from '../places'

type Props = {
  category: string
  center: () => { lat: number; lng: number }
  onSelect: (place: PlaceSummary) => void
}

function formatDistance(m?: number | null) {
  if (m == null) return ''
  return m < 1000 ? `${Math.round(m)} м` : `${(m / 1000).toFixed(1)} км`
}

export default function SearchBox({ category, center, onSelect }: Props) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<PlaceSummary[]>([])
  const [open, setOpen] = useState(false)
  const timer = useRef<number | undefined>(undefined)

  useEffect(() => {
    window.clearTimeout(timer.current)
    const q = query.trim()
    if (q.length < 2) {
      setResults([])
      return
    }
    // Wait until the user pauses typing
    timer.current = window.setTimeout(() => {
      const c = center()
      const params = new URLSearchParams({ q, lat: c.lat.toFixed(5), lng: c.lng.toFixed(5) })
      if (category) params.set('category', category)
      api<PlaceSummary[]>(`/api/places/search?${params}`).then((r) => {
        setResults(r)
        setOpen(true)
      }).catch(() => setResults([]))
    }, 250)
    return () => window.clearTimeout(timer.current)
  }, [query, category, center])

  return (
    <div className="search">
      <div className="search-bar">
        <input
          type="search" placeholder="Газар хайх (кафе, музей, kino…)" value={query}
          onChange={(e) => setQuery(e.target.value)} onFocus={() => setOpen(true)}
          onKeyDown={(e) => e.key === 'Escape' && setOpen(false)}
        />
      </div>
      {open && query.trim().length >= 2 && (
        <ul className="search-results">
          {results.length === 0 && <li className="muted">Илэрц олдсонгүй</li>}
          {results.map((p) => (
            <li key={p.id}>
              <button type="button" onClick={() => { setOpen(false); onSelect(p) }}>
                <span className="dot" style={{ background: MAIN_CATEGORY_COLORS[p.mainCategory] ?? '#888' }} />
                <span className="r-main">
                  <span className="r-name">{p.name}</span>
                  <span className="r-sub">{p.categoryName}</span>
                </span>
                <span className="r-dist">{formatDistance(p.distanceM)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
