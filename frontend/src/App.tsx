import { useEffect, useRef, useState } from 'react'
import * as maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { addPlacesLayer } from './places'
// MapLibre runs tile parsing in a Web Worker loaded from a separate file. Vite bundles the main
// library into /assets/index-*.js, so the default worker URL points to a file that does not exist
// (nginx then returns index.html and the map stays blank). Emit the worker as an asset and use it.
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url'

maplibregl.setWorkerUrl(workerUrl)

// Ulaanbaatar city centre (Sükhbaatar Square)
const UB_CENTER: [number, number] = [106.9177, 47.9186]

// Temporary public basemap for the skeleton. Replaced by our own PMTiles in the data milestone.
const BASEMAP_STYLE = 'https://tiles.openfreemap.org/styles/liberty'

type Health = { status: string; database?: string; postgis?: string; ml?: string }

export default function App() {
  const mapContainer = useRef<HTMLDivElement>(null)
  const [health, setHealth] = useState<Health | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!mapContainer.current) return
    const map = new maplibregl.Map({
      container: mapContainer.current,
      style: BASEMAP_STYLE,
      center: UB_CENTER,
      zoom: 12,
    })
    map.addControl(new maplibregl.NavigationControl(), 'top-right')
    map.addControl(new maplibregl.GeolocateControl({ trackUserLocation: false }), 'top-right')
    map.on('load', () => addPlacesLayer(map))
    return () => map.remove()
  }, [])

  useEffect(() => {
    fetch('/api/health')
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`))))
      .then(setHealth)
      .catch((e: Error) => setError(e.message))
  }, [])

  return (
    <>
      <div ref={mapContainer} style={{ position: 'absolute', inset: 0 }} />
      <div
        style={{
          position: 'absolute', top: 10, left: 10, padding: '8px 12px', borderRadius: 8,
          background: 'white', boxShadow: '0 1px 4px rgba(0,0,0,.3)', fontSize: 14,
        }}
      >
        <strong>Системийн төлөв</strong>
        <div>
          {error && <span style={{ color: '#c00' }}>Сервертэй холбогдсонгүй ({error})</span>}
          {!error && !health && 'Шалгаж байна…'}
          {health && (
            <>
              API: {health.status} · DB: {health.database ?? '-'} · ML: {health.ml ?? '-'}
            </>
          )}
        </div>
      </div>
    </>
  )
}
