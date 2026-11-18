import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import * as maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { api, type Category, type PlaceSummary, type RecommendationItem } from '../api'
import { addPlacesLayer, filterPlacesLayer, onRecommendationClick, showRecommendations } from '../places'
import { basemapStyle } from '../basemap'
import { useAuth } from '../auth'
import SearchBox from '../components/SearchBox'
import PlaceSheet from '../components/PlaceSheet'
import RecommendationPanel from '../components/RecommendationPanel'
// MapLibre runs tile parsing in a Web Worker loaded from a separate file. Vite bundles the main
// library into /assets/index-*.js, so the default worker URL points to a file that does not exist
// (nginx then returns index.html and the map stays blank). Emit the worker as an asset and use it.
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url'

maplibregl.setWorkerUrl(workerUrl)

// Ulaanbaatar city centre (Sükhbaatar Square)
const UB_CENTER: [number, number] = [106.9177, 47.9186]

export default function MapPage() {
  const { user } = useAuth()
  const mapContainer = useRef<HTMLDivElement>(null)
  const mapRef = useRef<maplibregl.Map | null>(null)
  const [params, setParams] = useSearchParams()
  const [categories, setCategories] = useState<Category[]>([])
  const [category, setCategory] = useState('')
  const [showRecs, setShowRecs] = useState(false)
  const [recItems, setRecItems] = useState<RecommendationItem[]>([])
  const selectedId = params.get('place') ? Number(params.get('place')) : null

  // The selected place lives in the URL (?place=123), so it can be linked to and survives reloads
  const select = useCallback((id: number | null) => {
    setParams(id == null ? {} : { place: String(id) })
  }, [setParams])
  // react-router gives setParams a new identity on every URL change. The map must be created only once,
  // so its click handler reads the latest `select` through a ref instead of depending on it.
  const selectRef = useRef(select)
  useEffect(() => {
    selectRef.current = select
  }, [select])

  useEffect(() => {
    api<Category[]>('/api/categories').then(setCategories).catch(() => setCategories([]))
  }, [])

  useEffect(() => {
    if (!mapContainer.current) return
    let map: maplibregl.Map | null = null
    let cancelled = false
    basemapStyle().then((style) => {
      if (cancelled || !mapContainer.current) return
      map = new maplibregl.Map({ container: mapContainer.current, style, center: UB_CENTER, zoom: 13 })
      map.addControl(new maplibregl.NavigationControl(), 'top-right')
      map.addControl(new maplibregl.GeolocateControl({ trackUserLocation: false }), 'top-right')
      const m = map
      mapRef.current = m
      m.on('load', () => {
        addPlacesLayer(m, (id) => selectRef.current(id))
        onRecommendationClick(m, (id) => selectRef.current(id))
      })
    })
    return () => {
      cancelled = true
      map?.remove()
      mapRef.current = null
    }
  }, [])

  const changeCategory = (code: string) => {
    setCategory(code)
    if (mapRef.current) filterPlacesLayer(mapRef.current, code)
  }

  const center = useCallback(() => {
    const c = mapRef.current?.getCenter()
    return c ? { lat: c.lat, lng: c.lng } : { lat: UB_CENTER[1], lng: UB_CENTER[0] }
  }, [])

  const updateRecs = (items: RecommendationItem[]) => {
    setRecItems(items)
    if (mapRef.current) showRecommendations(mapRef.current, items)
  }

  const closeRecs = () => {
    setShowRecs(false)
    updateRecs([])
  }

  const showResult = (p: PlaceSummary) => {
    mapRef.current?.flyTo({ center: [p.lng, p.lat], zoom: Math.max(mapRef.current.getZoom(), 16) })
    select(p.id)
  }

  return (
    <>
      {/* Inline style on purpose: maplibre-gl.css sets ".maplibregl-map { position: relative }" on this
          element, which would override a class and collapse the map to zero height. */}
      <div ref={mapContainer} style={{ position: 'absolute', inset: 0 }} />
      <div className="topbar">
        <SearchBox categories={categories} category={category} onCategoryChange={changeCategory}
                   center={center} onSelect={showResult} />
        {user ? (
          <Link className="avatar" to="/profile" title={user.displayName}>
            {user.displayName.trim().charAt(0).toUpperCase()}
          </Link>
        ) : (
          <Link className="btn" to="/login">Нэвтрэх</Link>
        )}
      </div>
      {showRecs && (
        <RecommendationPanel mapCenter={center} onResults={updateRecs} onClose={closeRecs}
                             onOpen={(it) => select(it.placeId)} />
      )}
      {!showRecs && selectedId == null && (
        user
          ? <button className="btn fab" onClick={() => setShowRecs(true)}>✨ Санал болгох</button>
          : <Link className="btn fab" to="/login">✨ Санал авахын тулд нэвтрэх</Link>
      )}
      {selectedId != null && (
        <PlaceSheet placeId={selectedId} onClose={() => select(null)}
                    recommendationId={recItems.find((r) => r.placeId === selectedId)?.recommendationId} />
      )}
    </>
  )
}
