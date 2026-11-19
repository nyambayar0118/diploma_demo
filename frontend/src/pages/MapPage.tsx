import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import * as maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { agoText, api, ApiError, isActiveRoute, type Category, type FriendLocation, type PlaceSummary, type RecommendationItem, type RouteView,
  type VisitResult } from '../api'
import { addPlacesLayer, filterPlacesLayer, onRecommendationClick, onRouteStopClick, showRecommendations,
  showRoute } from '../places'
import { basemapStyle } from '../basemap'
import { useAuth } from '../auth'
import SearchBox from '../components/SearchBox'
import PlaceSheet from '../components/PlaceSheet'
import RecommendationPanel from '../components/RecommendationPanel'
import RoutePanel from '../components/RoutePanel'
import VisitPrompt from '../components/VisitPrompt'
import ContributionForm from '../components/ContributionForm'
import SideMenu from '../components/SideMenu'
import Icon from '../components/Icon'
import { MAIN_CATEGORY_COLORS } from '../places'
import { useLocationSharing } from '../locationSharing'
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
  const [route, setRoute] = useState<RouteView | null>(null)
  const [showRoutePanel, setShowRoutePanel] = useState(false)
  const [mapReady, setMapReady] = useState(false)
  // Adding a missing place (АШ03): pick the point, then fill the form
  const [newPlaceAt, setNewPlaceAt] = useState<{ lat: number; lng: number } | null>(null)
  const [pickingNew, setPickingNew] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [menuOpen, setMenuOpen] = useState(false)
  const closeMenu = useCallback(() => setMenuOpen(false), [])
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
      // Phones: pinch to zoom, so no zoom buttons; the locate button sits low, within thumb reach
      const phone = window.matchMedia('(max-width: 640px)').matches
      map = new maplibregl.Map({
        container: mapContainer.current, style, center: UB_CENTER, zoom: 13, attributionControl: false,
      })
      map.addControl(new maplibregl.AttributionControl({ compact: true }), 'bottom-right')
      if (!phone) map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'bottom-right')
      map.addControl(new maplibregl.GeolocateControl({ trackUserLocation: false }), 'bottom-right')
      const m = map
      mapRef.current = m
      m.on('load', () => {
        addPlacesLayer(m, (id) => !picking.current && selectRef.current(id))
        onRecommendationClick(m, (id) => !picking.current && selectRef.current(id))
        onRouteStopClick(m, (id) => !picking.current && selectRef.current(id))
        setMapReady(true)
      })
    })
    return () => {
      cancelled = true
      map?.remove()
      mapRef.current = null
    }
  }, [])

  // Location sharing (sends my position while the app is open) and friends' shared positions (АШ21)
  useLocationSharing(!!user)
  const friendMarkers = useRef<maplibregl.Marker[]>([])
  useEffect(() => {
    const map = mapRef.current
    if (!user || !map || !mapReady) return
    const draw = (list: FriendLocation[]) => {
      friendMarkers.current.forEach((m) => m.remove())
      friendMarkers.current = list.map((f) => {
        const el = document.createElement('div')
        el.className = 'friend-marker'
        el.textContent = f.displayName.trim().charAt(0).toUpperCase()
        el.title = `${f.displayName} · ${agoText(f.updatedAt)}`
        return new maplibregl.Marker({ element: el }).setLngLat([f.lng, f.lat])
          .setPopup(new maplibregl.Popup({ offset: 18, closeButton: false })
            .setText(`${f.displayName} (@${f.username}) · ${agoText(f.updatedAt)}`))
          .addTo(map)
      })
    }
    const load = () => api<FriendLocation[]>('/api/friends/locations').then(draw).catch(() => undefined)
    load()
    const timer = window.setInterval(load, 60_000)
    return () => {
      window.clearInterval(timer)
      friendMarkers.current.forEach((m) => m.remove())
      friendMarkers.current = []
    }
  }, [user, mapReady])

  // An unfinished route from an earlier visit (e.g. the user went to Google Maps and came back)
  useEffect(() => {
    if (!user) {
      setRoute(null)
      return
    }
    api<RouteView[]>('/api/routes')
      .then((routes) => setRoute(routes.find((r) => isActiveRoute(r)) ?? null))
      .catch(() => undefined)
  }, [user])

  // Draw the route while its panel is open
  const fitRoute = useRef(true)
  useEffect(() => {
    const map = mapRef.current
    if (!map || !mapReady) return
    showRoute(map, showRoutePanel ? route : null, fitRoute.current)
    fitRoute.current = false
  }, [route, showRoutePanel, mapReady])

  const openRoute = (r: RouteView) => {
    fitRoute.current = true
    setRoute(r)
    setShowRoutePanel(true)
  }

  const routeCreated = (r: RouteView) => {
    setShowRecs(false)
    updateRecs([])
    select(null)
    openRoute(r)
  }

  const visitAnswered = (result: VisitResult) => {
    api<RouteView>(`/api/routes/${result.routeId}`).then((r) => {
      // When the last stop is answered the route is completed: show it so the user can rate it
      if (r.status === 'COMPLETED') openRoute(r)
      else setRoute(r)
    }).catch(() => undefined)
  }

  const routeHasPlace = route?.stops.some((s) => s.placeId === selectedId) ?? false
  const canAddToRoute = route?.status === 'CREATED' && route.stops.length < 4 && !routeHasPlace
  const addToRoute = async () => {
    if (!route || selectedId == null) return
    try {
      const r = await api<RouteView>(`/api/routes/${route.id}/stops`, {
        method: 'PATCH',
        body: { op: 'ADD', placeId: selectedId, recommendationId: recItems.find((i) => i.placeId === selectedId)?.recommendationId },
      })
      select(null)
      openRoute(r)
    } catch (err) {
      window.alert(err instanceof ApiError ? err.message : 'Сервертэй холбогдсонгүй')
    }
  }

  const changeCategory = (code: string) => {
    setCategory(code)
    if (mapRef.current) filterPlacesLayer(mapRef.current, code)
  }

  const center = useCallback(() => {
    const c = mapRef.current?.getCenter()
    return c ? { lat: c.lat, lng: c.lng } : { lat: UB_CENTER[1], lng: UB_CENTER[0] }
  }, [])

  const originMarker = useRef<maplibregl.Marker | null>(null)
  // While the user picks a start point, a tap on a place dot must not open that place
  const picking = useRef(false)

  const showOrigin = useCallback((p: { lat: number; lng: number } | null,
                                  onMove: (p: { lat: number; lng: number }) => void) => {
    originMarker.current?.remove()
    originMarker.current = null
    const map = mapRef.current
    if (!p || !map) return
    const el = document.createElement('div')
    el.className = 'origin-marker'
    el.title = 'Эхлэх цэг (чирж зөөнө)'
    const marker = new maplibregl.Marker({ element: el, draggable: true }).setLngLat([p.lng, p.lat]).addTo(map)
    marker.on('dragend', () => {
      const ll = marker.getLngLat()
      onMove({ lat: ll.lat, lng: ll.lng })
    })
    originMarker.current = marker
    map.easeTo({ center: [p.lng, p.lat], zoom: Math.max(map.getZoom(), 14) })
  }, [])

  const pickOnMap = useCallback(() => new Promise<{ lat: number; lng: number }>((resolve) => {
    const map = mapRef.current
    if (!map) return resolve(center())
    map.getCanvas().style.cursor = 'crosshair'
    picking.current = true
    map.once('click', (e) => {
      map.getCanvas().style.cursor = ''
      setTimeout(() => (picking.current = false), 0)
      resolve({ lat: e.lngLat.lat, lng: e.lngLat.lng })
    })
  }), [center])

  const updateRecs = (items: RecommendationItem[]) => {
    setRecItems(items)
    if (mapRef.current) showRecommendations(mapRef.current, items)
  }

  const routeChanged = (r: RouteView | null) => {
    setRoute(r)
  }

  const closeRoutePanel = () => {
    setShowRoutePanel(false)
    if (!isActiveRoute(route)) setRoute(null)
  }

  const closeRecs = () => {
    setShowRecs(false)
    updateRecs([])
  }

  const startAddPlace = async () => {
    setPickingNew(true)
    const p = await pickOnMap()
    setPickingNew(false)
    setNewPlaceAt(p)
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
        <div className="topbar-row">
          <button className="menu-btn" onClick={() => setMenuOpen(true)} aria-label="Цэс">
            <span /><span /><span />
          </button>
          <SearchBox category={category} center={center} onSelect={showResult} />
        </div>
        <div className="cat-chips" role="toolbar" aria-label="Ангилал">
          <button className={`cat-chip ${category === '' ? 'on' : ''}`} onClick={() => changeCategory('')}>Бүгд</button>
          {categories.map((c) => (
            <button key={c.code} className={`cat-chip ${category === c.code ? 'on' : ''}`}
                    onClick={() => changeCategory(category === c.code ? '' : c.code)}>
              <span className="dot" style={{ background: MAIN_CATEGORY_COLORS[c.code] ?? '#888' }} />
              {c.nameMn}
            </button>
          ))}
        </div>
      </div>
      <SideMenu open={menuOpen} onClose={closeMenu} onSelectPlace={showResult} onOpenRoute={openRoute}
                onAddPlace={startAddPlace} />
      {user && <VisitPrompt onAnswered={visitAnswered} />}
      {pickingNew && <div className="toast">Шинэ газрын байршлыг газрын зураг дээр дарж сонгоно уу</div>}
      {notice && (
        <div className="toast ok" onClick={() => setNotice(null)}>{notice}</div>
      )}
      {newPlaceAt && (
        <aside className="sheet">
          <button className="sheet-close" onClick={() => setNewPlaceAt(null)} aria-label="Хаах">×</button>
          <ContributionForm position={newPlaceAt} onCancel={() => setNewPlaceAt(null)}
                            onDone={(m) => { setNewPlaceAt(null); setNotice(m) }} />
        </aside>
      )}
      {showRecs && (
        <RecommendationPanel mapCenter={center} showOrigin={showOrigin} pickOnMap={pickOnMap}
                             onResults={updateRecs} onClose={closeRecs} onRoute={routeCreated}
                             onOpen={(it) => select(it.placeId)} />
      )}
      {showRoutePanel && route && !showRecs && (
        <RoutePanel route={route} onChange={routeChanged} onSelectPlace={(id) => select(id)} onClose={closeRoutePanel} />
      )}
      {!showRecs && !showRoutePanel && selectedId == null && (
        user ? (
          <div className="fabs">
            {isActiveRoute(route) && (
              <button className="btn secondary" onClick={() => openRoute(route)}><Icon name="route" size={18} /> Маршрут</button>
            )}
            <button className="btn" disabled={pickingNew} onClick={() => setShowRecs(true)}><Icon name="sparkles" size={18} /> Санал болгох</button>
          </div>
        ) : (
          <Link className="btn fab" to="/login">✨ Санал авахын тулд нэвтрэх</Link>
        )
      )}
      {selectedId != null && (
        <PlaceSheet placeId={selectedId} onClose={() => select(null)}
                    onAddToRoute={canAddToRoute ? addToRoute : undefined}
                    recommendationId={recItems.find((r) => r.placeId === selectedId)?.recommendationId} />
      )}
    </>
  )
}
