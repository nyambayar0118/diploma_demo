import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import * as maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { addPlacesLayer } from '../places'
import { basemapStyle } from '../basemap'
import { useAuth } from '../auth'
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
      m.on('load', () => addPlacesLayer(m))
    })
    return () => {
      cancelled = true
      map?.remove()
    }
  }, [])

  return (
    <>
      <div ref={mapContainer} className="map" />
      <div className="topbar">
        <div className="brand">Ухаалаг маршрут</div>
        {user ? (
          <Link className="avatar" to="/profile" title={user.displayName}>
            {user.displayName.trim().charAt(0).toUpperCase()}
          </Link>
        ) : (
          <Link className="btn" to="/login">Нэвтрэх</Link>
        )}
      </div>
    </>
  )
}
