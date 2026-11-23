import * as maplibregl from 'maplibre-gl'
import { Protocol } from 'pmtiles'
import { layers, namedFlavor } from '@protomaps/basemaps'

// Улаанбаатарын өөрийн вектор тайл (`docker compose ... run --rm tiles`-ээр үүсгэж, nginx түгээнэ)
const PMTILES_PATH = '/tiles/ulaanbaatar.pmtiles'
// Зөвхөн PMTiles файл хараахан үүсээгүй үед ашиглана (жишээ нь шинээр татсан төсөл)
const FALLBACK_STYLE = 'https://tiles.openfreemap.org/styles/liberty'

let protocolAdded = false

function ownStyle(theme: 'light' | 'dark'): maplibregl.StyleSpecification {
  return {
    version: 8,
    // Protomaps суурь газрын зургийн фонт, дүрс (нийтийн CDN; дараа нь өөрөө байршуулж болно)
    glyphs: 'https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf',
    sprite: `https://protomaps.github.io/basemaps-assets/sprites/v4/${theme}`,
    sources: {
      protomaps: {
        type: 'vector',
        url: `pmtiles://${location.origin}${PMTILES_PATH}`,
        attribution: '<a href="https://protomaps.com">Protomaps</a> © <a href="https://openstreetmap.org/copyright">OpenStreetMap</a> · © <a href="https://map.minu.mn">Minu Map</a>',
      },
    },
    // Нэрийг name:mn-ээр, байхгүй бол үндсэн нэрээр харуулна
    layers: layers('protomaps', namedFlavor(theme), { lang: 'mn' }),
  }
}

/** PMTiles файл байвал өөрийн суурь газрын зургийн загварыг, үгүй бол нийтийн нөөц загварыг буцаана. */
export async function basemapStyle(theme: 'light' | 'dark' = 'light'): Promise<maplibregl.StyleSpecification | string> {
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
  return ownStyle(theme)
}
