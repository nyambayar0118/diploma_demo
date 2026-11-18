import * as maplibregl from 'maplibre-gl'

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
    },
  })

  let controller: AbortController | null = null
  const refresh = () => {
    if (map.getZoom() < MIN_PLACES_ZOOM) return
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
  refresh()

  map.on('click', 'places-circles', (e) => {
    const id = e.features?.[0]?.properties?.id
    if (id != null) onSelect(Number(id))
  })
  map.on('mouseenter', 'places-circles', () => (map.getCanvas().style.cursor = 'pointer'))
  map.on('mouseleave', 'places-circles', () => (map.getCanvas().style.cursor = ''))
}

/** Shows only one main category on the map ('' = all). */
export function filterPlacesLayer(map: maplibregl.Map, mainCategory: string) {
  if (!map.getLayer('places-circles')) return
  map.setFilter('places-circles', mainCategory ? ['==', ['get', 'mainCategory'], mainCategory] : null)
}

const RECS_SOURCE = 'recommendations'

/** Numbered markers for the current recommendation list; fits the map to them. */
export function showRecommendations(map: maplibregl.Map,
                                    items: { placeId: number; lat: number; lng: number; mainCategory: string }[]) {
  const data = {
    type: 'FeatureCollection' as const,
    features: items.map((it, i) => ({
      type: 'Feature' as const,
      geometry: { type: 'Point' as const, coordinates: [it.lng, it.lat] },
      properties: { id: it.placeId, n: String(i + 1), mainCategory: it.mainCategory },
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
        'circle-radius': 12,
        'circle-color': ['match', ['get', 'mainCategory'], ...Object.entries(MAIN_CATEGORY_COLORS).flat(), '#495057'] as unknown as maplibregl.ExpressionSpecification,
        'circle-stroke-color': '#ffffff',
        'circle-stroke-width': 2.5,
      },
    })
    map.addLayer({
      id: 'recs-labels', type: 'symbol', source: RECS_SOURCE,
      layout: { 'text-field': ['get', 'n'], 'text-font': ['Noto Sans Regular'], 'text-size': 12, 'text-allow-overlap': true },
      paint: { 'text-color': '#ffffff' },
    })
  }
  if (items.length > 0) {
    const bounds = new maplibregl.LngLatBounds()
    items.forEach((it) => bounds.extend([it.lng, it.lat]))
    map.fitBounds(bounds, { padding: { top: 80, bottom: 80, left: window.innerWidth > 640 ? 420 : 40, right: 60 }, maxZoom: 16 })
  }
}

export function onRecommendationClick(map: maplibregl.Map, onSelect: (placeId: number) => void) {
  map.on('click', 'recs-circles', (e) => {
    const id = e.features?.[0]?.properties?.id
    if (id != null) onSelect(Number(id))
  })
}
