import * as maplibregl from 'maplibre-gl'
import { Protocol } from 'pmtiles'
import { layers, namedFlavor } from '@protomaps/basemaps'

// Our own vector tiles for Ulaanbaatar (made by `docker compose ... run --rm tiles`, served by nginx)
const PMTILES_PATH = '/tiles/ulaanbaatar.pmtiles'
// Used only when the PMTiles file has not been generated yet (e.g. a fresh clone)
const FALLBACK_STYLE = 'https://tiles.openfreemap.org/styles/liberty'

let protocolAdded = false

function ownStyle(): maplibregl.StyleSpecification {
  return {
    version: 8,
    // Fonts and icons of the Protomaps basemap (public CDN; can be self-hosted later)
    glyphs: 'https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf',
    sprite: 'https://protomaps.github.io/basemaps-assets/sprites/v4/light',
    sources: {
      protomaps: {
        type: 'vector',
        url: `pmtiles://${location.origin}${PMTILES_PATH}`,
        attribution: '<a href="https://protomaps.com">Protomaps</a> © <a href="https://openstreetmap.org/copyright">OpenStreetMap</a>',
      },
    },
    // Labels prefer name:mn and fall back to the default name
    layers: layers('protomaps', namedFlavor('light'), { lang: 'mn' }),
  }
}

/** Returns our own basemap style if the PMTiles file exists, otherwise the public fallback. */
export async function basemapStyle(): Promise<maplibregl.StyleSpecification | string> {
  try {
    const res = await fetch(PMTILES_PATH, { method: 'HEAD' })
    if (!res.ok) return FALLBACK_STYLE
  } catch {
    return FALLBACK_STYLE
  }
  if (!protocolAdded) {
    maplibregl.addProtocol('pmtiles', new Protocol().tile)
    protocolAdded = true
  }
  return ownStyle()
}
