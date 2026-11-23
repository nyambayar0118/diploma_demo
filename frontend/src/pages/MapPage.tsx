import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import * as maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { agoText, api, ApiError, isActiveRoute, type Category, type FriendLocation, type PlaceSummary, type RecommendationItem, type RouteView,
  type VisitResult } from '../api'
import { addPlacesLayer, filterPlacesLayer, focusOn, onRecommendationClick, onRouteStopClick, showGroupMembers, showRecommendations,
  type MemberMarker, type RecMarker,
  showRoute } from '../places'
import { basemapStyle } from '../basemap'
import { useAuth } from '../auth'
import SearchBox from '../components/SearchBox'
import PlaceSheet from '../components/PlaceSheet'
import RecommendationPanel from '../components/RecommendationPanel'
import GroupOptionsPanel from '../components/GroupOptionsPanel'
import RoutePanel from '../components/RoutePanel'
import VisitPrompt from '../components/VisitPrompt'
import ContributionForm from '../components/ContributionForm'
import SideMenu from '../components/SideMenu'
import InstallGuide from '../components/InstallGuide'
import InstallBanner from '../components/InstallBanner'
import MapStyleMenu from '../components/MapStyleMenu'
import { saveMarkerStyle, savedMarkerStyle, type MarkerStyle } from '../markerShapes'
import { placesAreHidden, setMarkerStyle, setPlacesHidden } from '../places'
import Icon from '../components/Icon'
import { MAIN_CATEGORY_COLORS } from '../places'
import { useLocationSharing } from '../locationSharing'
import { currentTheme, useTheme } from '../theme'
// MapLibre runs tile parsing in a Web Worker loaded from a separate file. Vite bundles the main
// library into /assets/index-*.js, so the default worker URL points to a file that does not exist
// (nginx then returns index.html and the map stays blank). Emit the worker as an asset and use it.
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url'
import BottomSheet from '../components/BottomSheet'

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
  const [guideOpen, setGuideOpen] = useState(false)
  // Map look menu (marker shape, dark mode), opened from a button among the map controls
  const [styleMenu, setStyleMenu] = useState<{ right: number; bottom: number } | null>(null)
  const [markerStyle, setMarkerStyleState] = useState<MarkerStyle>(savedMarkerStyle)
  const closeStyleMenu = useCallback(() => setStyleMenu(null), [])
  const changeMarkerStyle = (s: MarkerStyle) => {
    setMarkerStyleState(s)
    saveMarkerStyle(s)
    if (mapRef.current) setMarkerStyle(mapRef.current, s)
  }
  const [dotsHidden, setDotsHidden] = useState(placesAreHidden)
  const placesToggle = useRef<PlacesToggle | null>(null)
  const flashTimer = useRef<number | undefined>(undefined)
  const [flashText, setFlashText] = useState<string | null>(null)
  const flash = (text: string) => {
    setFlashText(text)
    window.clearTimeout(flashTimer.current)
    flashTimer.current = window.setTimeout(() => setFlashText(null), 1600)
  }
  const closeGuide = useCallback(() => setGuideOpen(false), [])
  const closeMenu = useCallback(() => setMenuOpen(false), [])
  const selectedId = params.get('place') ? Number(params.get('place')) : null

  // The selected place lives in the URL (?place=123), so it can be linked to and survives reloads
  const select = useCallback((id: number | null) => {
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      if (id == null) next.delete('place')
      else next.set('place', String(id))
      return next
    })
  }, [setParams])
  // Group plan options shown on the map (?group=6)
  const groupId = params.get('group') ? Number(params.get('group')) : null

  // Recommendation markers (single user or group options) and the one shown in focus
  const [focusedRec, setFocusedRec] = useState<number | null>(null)
  const recMarkers = useRef<RecMarker[]>([])
  const groupMembers = useRef<MemberMarker[] | null>(null)
  const drawRecs = useCallback((markers: RecMarker[]) => {
    recMarkers.current = markers
    setFocusedRec(null)
    const map = mapRef.current
    if (!map) return
    showRecommendations(map, markers)
    if (groupMembers.current) showGroupMembers(map, groupMembers.current, null)
  }, [])
  const focusRec = useCallback((placeId: number) => {
    const m = recMarkers.current.find((r) => r.placeId === placeId)
    const map = mapRef.current
    if (!m || !map) return
    setFocusedRec(placeId)
    showRecommendations(map, recMarkers.current, placeId, false)
    if (groupMembers.current) showGroupMembers(map, groupMembers.current, m)
    focusOn(map, m.lat, m.lng)
  }, [])
  const drawMembers = useCallback((members: MemberMarker[] | null) => {
    groupMembers.current = members
    if (mapRef.current) showGroupMembers(mapRef.current, members, null)
  }, [])
  const focusRef = useRef(focusRec)
  // Markers that arrived before the map finished loading
  useEffect(() => {
    const map = mapRef.current
    if (!map || !mapReady) return
    if (recMarkers.current.length > 0) showRecommendations(map, recMarkers.current)
    if (groupMembers.current) showGroupMembers(map, groupMembers.current, null)
  }, [mapReady])
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
    basemapStyle(currentTheme()).then((style) => {
      if (cancelled || !mapContainer.current) return
      // Phones: pinch to zoom, so no zoom buttons; the locate button sits low, within thumb reach
      const phone = window.matchMedia('(max-width: 640px)').matches
      map = new maplibregl.Map({
        container: mapContainer.current, style, center: UB_CENTER, zoom: 13, attributionControl: false,
      })
      map.addControl(new maplibregl.AttributionControl({ compact: true }), 'bottom-right')
      if (!phone) map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'bottom-right')
      map.addControl(new maplibregl.GeolocateControl({ trackUserLocation: false }), 'bottom-right')
      map.addControl(new StyleButton((rect) => setStyleMenu((open) => open ? null : {
        right: window.innerWidth - rect.left + 8, bottom: window.innerHeight - rect.bottom,
      })), 'bottom-right')
      // Added last, so it sits on top of the stack: one tap hides or shows every place dot
      const toggle = new PlacesToggle(placesAreHidden(), (hidden) => {
        if (!mapRef.current) return
        setPlacesHidden(mapRef.current, hidden)
        setDotsHidden(hidden)
        flash(hidden ? 'Газрын цэгүүдийг нуулаа' : 'Газрын цэгүүдийг харууллаа')
      })
      placesToggle.current = toggle
      map.addControl(toggle, 'bottom-right')
      const m = map
      mapRef.current = m
      m.on('load', () => {
        addPlacesLayer(m, (id) => !picking.current && selectRef.current(id))
        // A numbered marker: the map moves to it and its row in the panel is highlighted
        onRecommendationClick(m, (id) => !picking.current && focusRef.current(id))
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

  // Dark / light basemap. Our own sources and layers (places, recommendations, route) are carried over.
  const [theme] = useTheme()
  const shownTheme = useRef(currentTheme())
  useEffect(() => {
    const map = mapRef.current
    if (!map || !mapReady || shownTheme.current === theme) return
    shownTheme.current = theme
    basemapStyle(theme).then((style) => {
      if (typeof style === 'string') return // public fallback style has no dark variant
      map.setStyle(style, {
        transformStyle: (previous, next) => {
          if (!previous) return next
          const own = Object.fromEntries(Object.entries(previous.sources).filter(([id]) => !(id in next.sources)))
          return {
            ...next,
            sources: { ...next.sources, ...own },
            layers: [...next.layers, ...previous.layers.filter((l) => 'source' in l && (l.source as string) in own)],
          }
        },
      })
    })
  }, [theme, mapReady])

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
    if (!mapRef.current) return
    filterPlacesLayer(mapRef.current, code)
    // Picking a category means the user wants to see places
    if (dotsHidden) {
      setPlacesHidden(mapRef.current, false)
      setDotsHidden(false)
      placesToggle.current?.setHidden(false)
    }
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
    drawRecs(items.map((it) => ({ placeId: it.placeId, lat: it.lat, lng: it.lng, mainCategory: it.mainCategory })))
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
                onAddPlace={startAddPlace} onInstall={() => setGuideOpen(true)} />
      {guideOpen && <InstallGuide onClose={closeGuide} />}
      {styleMenu && (
        <MapStyleMenu anchor={styleMenu} markerStyle={markerStyle} onMarkerStyle={changeMarkerStyle} onClose={closeStyleMenu} />
      )}
      {!showRecs && !showRoutePanel && groupId == null && selectedId == null && !newPlaceAt && (
        <InstallBanner onOpenGuide={() => setGuideOpen(true)} />
      )}
      {user && <VisitPrompt onAnswered={visitAnswered} />}
      {pickingNew && <div className="toast">Шинэ газрын байршлыг газрын зураг дээр дарж сонгоно уу</div>}
      {flashText && <div className="toast flash">{flashText}</div>}
      {notice && (
        <div className="toast ok" onClick={() => setNotice(null)}>{notice}</div>
      )}
      {newPlaceAt && (
        <BottomSheet className="sheet">
          <button className="sheet-close" onClick={() => setNewPlaceAt(null)} aria-label="Хаах">×</button>
          <ContributionForm position={newPlaceAt} onCancel={() => setNewPlaceAt(null)}
                            onDone={(m) => { setNewPlaceAt(null); setNotice(m) }} />
        </BottomSheet>
      )}
      {groupId != null && user && (
        <GroupOptionsPanel key={groupId} groupId={groupId} focusedId={focusedRec}
                           onFocus={(o) => focusRec(o.place.id)} onResults={drawRecs} onMembers={drawMembers} />
      )}
      {showRecs && groupId == null && (
        <RecommendationPanel mapCenter={center} showOrigin={showOrigin} pickOnMap={pickOnMap}
                             focusedId={focusedRec} onFocus={(it) => focusRec(it.placeId)}
                             onResults={updateRecs} onClose={closeRecs} onRoute={routeCreated}
                             categories={categories} initialCategory={category || undefined}
                             onOpen={(it) => select(it.placeId)} />
      )}
      {showRoutePanel && route && !showRecs && groupId == null && (
        <RoutePanel route={route} onChange={routeChanged} onSelectPlace={(id) => select(id)} onClose={closeRoutePanel} />
      )}
      {!showRecs && !showRoutePanel && groupId == null && selectedId == null && (
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

const EYE = '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8S1 12 1 12z"/><circle cx="12" cy="12" r="3"/>'
const EYE_OFF = '<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>'
  + '<path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>'
  + '<path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/><path d="M1 1l22 22"/>'

/** Map control button that hides or shows all place dots. */
class PlacesToggle implements maplibregl.IControl {
  private container?: HTMLDivElement
  private button?: HTMLButtonElement
  private hidden: boolean
  private readonly onToggle: (hidden: boolean) => void

  constructor(hidden: boolean, onToggle: (hidden: boolean) => void) {
    this.hidden = hidden
    this.onToggle = onToggle
  }

  onAdd() {
    const div = document.createElement('div')
    div.className = 'maplibregl-ctrl maplibregl-ctrl-group'
    const button = document.createElement('button')
    button.type = 'button'
    button.className = 'style-ctrl places-toggle'
    button.addEventListener('pointerdown', (e) => e.stopPropagation())
    button.addEventListener('click', () => {
      this.setHidden(!this.hidden)
      this.onToggle(this.hidden)
    })
    div.appendChild(button)
    this.container = div
    this.button = button
    this.render()
    return div
  }

  setHidden(hidden: boolean) {
    this.hidden = hidden
    this.render()
  }

  private render() {
    if (!this.button) return
    const label = this.hidden ? 'Газрын цэгүүдийг харуулах' : 'Газрын цэгүүдийг нуух'
    this.button.title = label
    this.button.setAttribute('aria-label', label)
    this.button.setAttribute('aria-pressed', String(this.hidden))
    this.button.classList.toggle('off', this.hidden)
    this.button.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"'
      + ` stroke-linecap="round" stroke-linejoin="round">${this.hidden ? EYE_OFF : EYE}</svg>`
  }

  onRemove() {
    this.container?.remove()
  }
}

/** Map control button (stacked with zoom / locate) that opens the map look menu. */
class StyleButton implements maplibregl.IControl {
  private container?: HTMLDivElement
  private readonly onOpen: (rect: DOMRect) => void

  constructor(onOpen: (rect: DOMRect) => void) {
    this.onOpen = onOpen
  }

  onAdd() {
    const div = document.createElement('div')
    div.className = 'maplibregl-ctrl maplibregl-ctrl-group'
    const button = document.createElement('button')
    button.type = 'button'
    button.title = 'Газрын зургийн харагдац'
    button.setAttribute('aria-label', 'Газрын зургийн харагдац')
    button.className = 'style-ctrl'
    button.innerHTML = '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"'
      + ' stroke-linecap="round" stroke-linejoin="round"><path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/></svg>'
    button.addEventListener('pointerdown', (e) => e.stopPropagation())
    button.addEventListener('click', () => this.onOpen(div.getBoundingClientRect()))
    div.appendChild(button)
    this.container = div
    return div
  }

  onRemove() {
    this.container?.remove()
  }
}
