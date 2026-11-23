import * as maplibregl from 'maplibre-gl'
import { iconExpression, provideMarkerImages, savedMarkerStyle, type MarkerStyle } from './markerShapes'

// Colour per main category (place_categories.code of the parent)
export const MAIN_CATEGORY_COLORS: Record<string, string> = {
  food: '#e8590c',
  entertainment: '#c2255c',
  culture: '#7048e8',
  nature: '#2f9e44',
  sport: '#1971c2',
  shopping: '#f08c00',
  wellness: '#0c8599',
  services: '#868e96',
}

// Below this zoom the whole city would be requested at once, so places are hidden.
export const MIN_PLACES_ZOOM = 13

const SOURCE_ID = 'places'

// Place dots can be hidden from the map control; remembered between visits
const HIDDEN_KEY = 'routeplanner:placesHidden'
let placesHidden = (() => {
  try {
    return localStorage.getItem(HIDDEN_KEY) === '1'
  } catch {
    return false
  }
})()
let refreshPlaces: (() => void) | null = null
let currentStyle: MarkerStyle = savedMarkerStyle()

export function placesAreHidden() {
  return placesHidden
}

/** Shows or hides every place dot (recommendations, routes and groups stay visible). */
export function setPlacesHidden(map: maplibregl.Map, hidden: boolean) {
  placesHidden = hidden
  try {
    localStorage.setItem(HIDDEN_KEY, hidden ? '1' : '0')
  } catch {
    // private mode: only for this visit
  }
  setMarkerStyle(map, currentStyle)
  if (!hidden) refreshPlaces?.()
}

/** Adds the "places" GeoJSON layer and keeps it in sync with the visible map area. */
export function addPlacesLayer(map: maplibregl.Map, onSelect: (placeId: number) => void) {
  map.addSource(SOURCE_ID, { type: 'geojson', data: { type: 'FeatureCollection', features: [] } })

  const colorByCategory: maplibregl.ExpressionSpecification = [
    'match', ['get', 'mainCategory'],
    ...Object.entries(MAIN_CATEGORY_COLORS).flat(),
    '#495057',
  ] as unknown as maplibregl.ExpressionSpecification

  map.addLayer({
    id: 'places-circles',
    type: 'circle',
    source: SOURCE_ID,
    minzoom: MIN_PLACES_ZOOM,
    paint: {
      'circle-color': colorByCategory,
      // Places that are never recommended (services, pitches, playgrounds) are drawn smaller
      'circle-radius': ['case', ['get', 'recommendable'], 6, 3.5],
      'circle-stroke-color': '#ffffff',
      'circle-stroke-width': 1.5,
      'circle-opacity-transition': { duration: 250 },
      'circle-stroke-opacity-transition': { duration: 250 },
    },
  })

  // Other marker shapes (star, pin, diamond) are images; only one of the two layers is visible
  provideMarkerImages(map)
  map.addLayer({
    id: 'places-shapes',
    type: 'symbol',
    source: SOURCE_ID,
    minzoom: MIN_PLACES_ZOOM,
    layout: {
      visibility: 'none',
      'icon-image': iconExpression('star'),
      'icon-allow-overlap': true,
      'icon-ignore-placement': true,
      'symbol-sort-key': ['case', ['get', 'recommendable'], 1, 0],
    },
    paint: { 'icon-opacity-transition': { duration: 250 } },
  })
  setMarkerStyle(map, savedMarkerStyle())

  let controller: AbortController | null = null
  const refresh = () => {
    if (map.getZoom() < MIN_PLACES_ZOOM || placesHidden) return
    controller?.abort()
    controller = new AbortController()
    const b = map.getBounds()
    const bbox = [b.getWest(), b.getSouth(), b.getEast(), b.getNorth()].map((v) => v.toFixed(5)).join(',')
    fetch(`/api/places?bbox=${bbox}`, { signal: controller.signal })
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
      .then((data) => (map.getSource(SOURCE_ID) as maplibregl.GeoJSONSource).setData(data))
      .catch((e: Error) => {
        if (e.name !== 'AbortError') console.warn('places request failed', e)
      })
  }
  map.on('moveend', refresh)
  refreshPlaces = refresh
  refresh()

  for (const layer of PLACE_LAYERS) {
    map.on('click', layer, (e) => {
      const id = e.features?.[0]?.properties?.id
      if (id != null) onSelect(Number(id))
    })
    map.on('mouseenter', layer, () => (map.getCanvas().style.cursor = 'pointer'))
    map.on('mouseleave', layer, () => (map.getCanvas().style.cursor = ''))
  }
}

const PLACE_LAYERS = ['places-circles', 'places-shapes']

const DIMMED = 0.2

/**
 * Fades every place dot except keepId while something else is in focus (a selected place, a route,
 * recommendations or group options), so that content stands out. dimmed false restores them.
 */
export function setPlacesDimmed(map: maplibregl.Map, dimmed: boolean, keepId: number | null = null) {
  if (!map.getLayer('places-circles') || !map.getLayer('places-shapes')) return
  const isKept: maplibregl.ExpressionSpecification = ['==', ['get', 'id'], keepId ?? -1]
  const opacity: maplibregl.DataDrivenPropertyValueSpecification<number> = dimmed
    ? ['case', isKept, 1, DIMMED]
    : 1
  map.setPaintProperty('places-circles', 'circle-opacity', opacity)
  map.setPaintProperty('places-circles', 'circle-stroke-opacity', opacity)
  map.setPaintProperty('places-shapes', 'icon-opacity', opacity)
  // The kept place is drawn a little larger, the rest as usual
  map.setPaintProperty('places-circles', 'circle-radius', keepId != null
    ? ['case', isKept, 9, ['get', 'recommendable'], 6, 3.5]
    : ['case', ['get', 'recommendable'], 6, 3.5])
  map.setPaintProperty('places-circles', 'circle-stroke-width', keepId != null ? ['case', isKept, 3, 1.5] : 1.5)
}

/** Switches the place markers between dots and an image shape. */
export function setMarkerStyle(map: maplibregl.Map, style: MarkerStyle) {
  currentStyle = style
  if (!map.getLayer('places-circles') || !map.getLayer('places-shapes')) return
  const dots = style === 'dot'
  map.setLayoutProperty('places-circles', 'visibility', dots && !placesHidden ? 'visible' : 'none')
  map.setLayoutProperty('places-shapes', 'visibility', !dots && !placesHidden ? 'visible' : 'none')
  if (!dots) {
    map.setLayoutProperty('places-shapes', 'icon-image', iconExpression(style))
    map.setLayoutProperty('places-shapes', 'icon-anchor', style === 'pin' ? 'bottom' : 'center')
  }
}

/** Shows only one main category on the map ('' = all). */
export function filterPlacesLayer(map: maplibregl.Map, mainCategory: string) {
  for (const layer of PLACE_LAYERS) {
    if (map.getLayer(layer)) map.setFilter(layer, mainCategory ? ['==', ['get', 'mainCategory'], mainCategory] : null)
  }
}

const RECS_SOURCE = 'recommendations'
const MEMBERS_SOURCE = 'group-members'
const MEMBER_LINES_SOURCE = 'group-member-lines'

export type RecMarker = { placeId: number; lat: number; lng: number; mainCategory: string }

/**
 * Numbered markers for the current recommendation list (single user or group options). The focused one is
 * drawn larger. Fits the map to all of them unless fit is false.
 */
export function showRecommendations(map: maplibregl.Map, items: RecMarker[], focusedId: number | null = null,
                                    fit = true) {
  const data = {
    type: 'FeatureCollection' as const,
    features: items.map((it, i) => ({
      type: 'Feature' as const,
      geometry: { type: 'Point' as const, coordinates: [it.lng, it.lat] },
      properties: { id: it.placeId, n: String(i + 1), mainCategory: it.mainCategory, focused: it.placeId === focusedId },
    })),
  }
  const source = map.getSource(RECS_SOURCE) as maplibregl.GeoJSONSource | undefined
  if (source) {
    source.setData(data)
  } else {
    map.addSource(RECS_SOURCE, { type: 'geojson', data })
    map.addLayer({
      id: 'recs-circles', type: 'circle', source: RECS_SOURCE,
      paint: {
        'circle-radius': ['case', ['get', 'focused'], 17, 12],
        'circle-color': ['match', ['get', 'mainCategory'], ...Object.entries(MAIN_CATEGORY_COLORS).flat(), '#495057'] as unknown as maplibregl.ExpressionSpecification,
        'circle-stroke-color': '#ffffff',
        'circle-stroke-width': ['case', ['get', 'focused'], 4, 2.5],
        'circle-radius-transition': { duration: 200 },
      },
    })
    map.addLayer({
      id: 'recs-labels', type: 'symbol', source: RECS_SOURCE,
      layout: {
        'text-field': ['get', 'n'], 'text-font': ['Noto Sans Regular'], 'text-allow-overlap': true,
        'text-size': ['case', ['get', 'focused'], 15, 12],
        'symbol-sort-key': ['case', ['get', 'focused'], 1, 0],
      },
      paint: { 'text-color': '#ffffff' },
    })
    map.on('mouseenter', 'recs-circles', () => { map.getCanvas().style.cursor = 'pointer' })
    map.on('mouseleave', 'recs-circles', () => { map.getCanvas().style.cursor = '' })
  }
  if (fit && items.length > 0) {
    const bounds = new maplibregl.LngLatBounds()
    items.forEach((it) => bounds.extend([it.lng, it.lat]))
    map.fitBounds(bounds, { padding: sidePadding(), maxZoom: 16 })
  }
}

/** Room taken by the side panel (desktop) or the bottom sheet (phone), so markers are not hidden under it. */
function sidePadding() {
  const phone = window.innerWidth <= 640
  return phone
    ? { top: 120, bottom: Math.round(window.innerHeight * 0.5) + 20, left: 40, right: 40 }
    : { top: 130, bottom: 60, left: 420, right: 70 }
}

/** Moves the map to one recommended place, keeping it clear of the panel. */
export function focusOn(map: maplibregl.Map, lat: number, lng: number) {
  const phone = window.innerWidth <= 640
  map.flyTo({
    center: [lng, lat], zoom: Math.max(map.getZoom(), 15), duration: 700,
    offset: phone ? [0, -Math.round(window.innerHeight * 0.22)] : [200, 30],
  })
}

export type MemberMarker = { id: number; name: string; color: string; lat: number; lng: number }

/**
 * Group members' start points (coloured initials) and, when an option is focused, a dashed line from every
 * member to it, so the fairness of a meeting place can be seen. null removes them.
 */
export function showGroupMembers(map: maplibregl.Map, members: MemberMarker[] | null,
                                 target: { lat: number; lng: number } | null) {
  const list = members ?? []
  const points = {
    type: 'FeatureCollection' as const,
    features: list.map((m) => ({
      type: 'Feature' as const,
      geometry: { type: 'Point' as const, coordinates: [m.lng, m.lat] },
      properties: { initial: m.name.trim().charAt(0).toUpperCase(), color: m.color, name: m.name },
    })),
  }
  const lines = {
    type: 'FeatureCollection' as const,
    features: target ? list.map((m) => ({
      type: 'Feature' as const,
      geometry: { type: 'LineString' as const, coordinates: [[m.lng, m.lat], [target.lng, target.lat]] },
      properties: { color: m.color },
    })) : [],
  }
  const src = map.getSource(MEMBERS_SOURCE) as maplibregl.GeoJSONSource | undefined
  if (src) {
    src.setData(points)
    ;(map.getSource(MEMBER_LINES_SOURCE) as maplibregl.GeoJSONSource).setData(lines)
    return
  }
  map.addSource(MEMBER_LINES_SOURCE, { type: 'geojson', data: lines })
  map.addSource(MEMBERS_SOURCE, { type: 'geojson', data: points })
  // Lines go under the numbered option markers
  const before = map.getLayer('recs-circles') ? 'recs-circles' : undefined
  map.addLayer({
    id: 'group-member-lines', type: 'line', source: MEMBER_LINES_SOURCE,
    paint: { 'line-color': ['get', 'color'], 'line-width': 2.5, 'line-dasharray': [2, 2], 'line-opacity': 0.85 },
  }, before)
  map.addLayer({
    id: 'group-members', type: 'circle', source: MEMBERS_SOURCE,
    paint: { 'circle-radius': 13, 'circle-color': ['get', 'color'], 'circle-stroke-color': '#ffffff', 'circle-stroke-width': 3 },
  })
  map.addLayer({
    id: 'group-member-initials', type: 'symbol', source: MEMBERS_SOURCE,
    layout: { 'text-field': ['get', 'initial'], 'text-font': ['Noto Sans Regular'], 'text-size': 13, 'text-allow-overlap': true },
    paint: { 'text-color': '#ffffff' },
  })
}

export function onRecommendationClick(map: maplibregl.Map, onSelect: (placeId: number) => void) {
  map.on('click', 'recs-circles', (e) => {
    const id = e.features?.[0]?.properties?.id
    if (id != null) onSelect(Number(id))
  })
}

const ROUTE_SOURCE = 'route'
const ROUTE_LINE_SOURCE = 'route-line'

type RouteLike = {
  startLat: number
  startLng: number
  stops: { placeId: number; order: number; lat: number; lng: number; mainCategory: string; visited: boolean; skipped: boolean }[]
}

/**
 * The route on the map (ХХШ05): start point, numbered stops and straight dashed lines between them in visiting
 * order. The real path is drawn by Google Maps, so the lines only show the order. null removes the route.
 */
export function showRoute(map: maplibregl.Map, route: RouteLike | null, fit = true) {
  const stops = route ? [...route.stops].sort((a, b) => a.order - b.order) : []
  const points = {
    type: 'FeatureCollection' as const,
    features: route ? [
      {
        type: 'Feature' as const,
        geometry: { type: 'Point' as const, coordinates: [route.startLng, route.startLat] },
        properties: { kind: 'start', n: '', mainCategory: '', done: false },
      },
      ...stops.map((s) => ({
        type: 'Feature' as const,
        geometry: { type: 'Point' as const, coordinates: [s.lng, s.lat] },
        properties: { kind: 'stop', id: s.placeId, n: s.visited ? '✓' : String(s.order), mainCategory: s.mainCategory,
          done: s.visited || s.skipped },
      })),
    ] : [],
  }
  const line = {
    type: 'FeatureCollection' as const,
    features: route && stops.length > 0 ? [{
      type: 'Feature' as const,
      geometry: {
        type: 'LineString' as const,
        coordinates: [[route.startLng, route.startLat], ...stops.map((s) => [s.lng, s.lat])],
      },
      properties: {},
    }] : [],
  }

  const pointSource = map.getSource(ROUTE_SOURCE) as maplibregl.GeoJSONSource | undefined
  if (pointSource) {
    pointSource.setData(points)
    ;(map.getSource(ROUTE_LINE_SOURCE) as maplibregl.GeoJSONSource).setData(line)
  } else {
    map.addSource(ROUTE_LINE_SOURCE, { type: 'geojson', data: line })
    map.addSource(ROUTE_SOURCE, { type: 'geojson', data: points })
    map.addLayer({
      id: 'route-line', type: 'line', source: ROUTE_LINE_SOURCE,
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': '#1f6feb', 'line-width': 4, 'line-opacity': 0.8, 'line-dasharray': [1.5, 1.5] },
    })
    map.addLayer({
      id: 'route-start', type: 'circle', source: ROUTE_SOURCE, filter: ['==', ['get', 'kind'], 'start'],
      paint: { 'circle-radius': 7, 'circle-color': '#1f6feb', 'circle-stroke-color': '#ffffff', 'circle-stroke-width': 3 },
    })
    map.addLayer({
      id: 'route-stops', type: 'circle', source: ROUTE_SOURCE, filter: ['==', ['get', 'kind'], 'stop'],
      paint: {
        'circle-radius': 13,
        'circle-color': ['case', ['get', 'done'], '#adb5bd',
          ['match', ['get', 'mainCategory'], ...Object.entries(MAIN_CATEGORY_COLORS).flat(), '#495057']] as unknown as maplibregl.ExpressionSpecification,
        'circle-stroke-color': '#ffffff',
        'circle-stroke-width': 3,
      },
    })
    map.addLayer({
      id: 'route-labels', type: 'symbol', source: ROUTE_SOURCE, filter: ['==', ['get', 'kind'], 'stop'],
      layout: { 'text-field': ['get', 'n'], 'text-font': ['Noto Sans Regular'], 'text-size': 13, 'text-allow-overlap': true },
      paint: { 'text-color': '#ffffff' },
    })
  }
  if (route && fit) {
    const bounds = new maplibregl.LngLatBounds()
    bounds.extend([route.startLng, route.startLat])
    stops.forEach((s) => bounds.extend([s.lng, s.lat]))
    map.fitBounds(bounds, { padding: { top: 90, bottom: 90, left: window.innerWidth > 640 ? 420 : 40, right: 60 }, maxZoom: 16 })
  }
}

export function onRouteStopClick(map: maplibregl.Map, onSelect: (placeId: number) => void) {
  map.on('click', 'route-stops', (e) => {
    const id = e.features?.[0]?.properties?.id
    if (id != null) onSelect(Number(id))
  })
}
