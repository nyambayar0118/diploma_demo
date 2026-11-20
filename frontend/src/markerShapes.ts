import type * as maplibregl from 'maplibre-gl'
import { MAIN_CATEGORY_COLORS } from './places'

/** How places are drawn on the map (map style menu). */
export type MarkerStyle = 'dot' | 'star' | 'pin' | 'diamond'
export const MARKER_STYLES: { id: MarkerStyle; label: string }[] = [
  { id: 'dot', label: 'Цэг' },
  { id: 'star', label: 'Од' },
  { id: 'pin', label: 'Зүү' },
  { id: 'diamond', label: 'Ромб' },
]

const KEY = 'routeplanner.markerStyle'
export function savedMarkerStyle(): MarkerStyle {
  try {
    const v = localStorage.getItem(KEY)
    if (MARKER_STYLES.some((s) => s.id === v)) return v as MarkerStyle
  } catch {
    /* ignore */
  }
  return 'dot'
}
export function saveMarkerStyle(style: MarkerStyle) {
  try {
    localStorage.setItem(KEY, style)
  } catch {
    /* ignore */
  }
}

const RATIO = 2 // sharp on high-density screens

/** Outline of a shape in a size×size box (pin: point at the bottom centre). */
function trace(ctx: CanvasRenderingContext2D, style: MarkerStyle, size: number) {
  const c = size / 2
  ctx.beginPath()
  if (style === 'star') {
    const outer = size * 0.48
    const inner = outer * 0.48
    for (let i = 0; i < 10; i++) {
      const r = i % 2 === 0 ? outer : inner
      const a = -Math.PI / 2 + (i * Math.PI) / 5
      const x = c + r * Math.cos(a)
      const y = c + 1 + r * Math.sin(a)
      if (i === 0) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
    }
    ctx.closePath()
  } else if (style === 'pin') {
    const r = size * 0.3
    const cy = r + 2
    ctx.moveTo(c, size - 1.5)
    ctx.bezierCurveTo(c - r * 0.4, size * 0.7, c - r, cy + r * 0.9, c - r, cy)
    ctx.arc(c, cy, r, Math.PI, 0)
    ctx.bezierCurveTo(c + r, cy + r * 0.9, c + r * 0.4, size * 0.7, c, size - 1.5)
    ctx.closePath()
  } else if (style === 'diamond') {
    const r = size * 0.44
    ctx.moveTo(c, c - r)
    ctx.lineTo(c + r * 0.8, c)
    ctx.lineTo(c, c + r)
    ctx.lineTo(c - r * 0.8, c)
    ctx.closePath()
  } else {
    ctx.arc(c, c, size * 0.42, 0, Math.PI * 2)
  }
}

/** A coloured shape with a white outline, as ImageData for map.addImage. */
export function drawMarker(style: MarkerStyle, color: string, cssSize: number): ImageData {
  const size = Math.round(cssSize * RATIO)
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext('2d')!
  trace(ctx, style, size)
  ctx.fillStyle = color
  ctx.fill()
  ctx.lineJoin = 'round'
  ctx.lineWidth = Math.max(2, size * 0.09)
  ctx.strokeStyle = '#ffffff'
  ctx.stroke()
  if (style === 'pin') {
    ctx.beginPath()
    ctx.arc(size / 2, size * 0.3 + 2, size * 0.1, 0, Math.PI * 2)
    ctx.fillStyle = '#ffffff'
    ctx.fill()
  }
  return ctx.getImageData(0, 0, size, size)
}

/** Data URL for previews in the menu. */
export function markerPreview(style: MarkerStyle, color: string, cssSize = 22): string {
  const img = drawMarker(style, color, cssSize)
  const canvas = document.createElement('canvas')
  canvas.width = img.width
  canvas.height = img.height
  canvas.getContext('2d')!.putImageData(img, 0, 0)
  return canvas.toDataURL()
}

// Sizes in CSS pixels: recommendable places are bigger than the rest
const SIZES = { l: 18, s: 11 } as const

/** Image id the symbol layer asks for: shape-<style>-<mainCategory>-<l|s> */
export function iconExpression(style: MarkerStyle): maplibregl.ExpressionSpecification {
  return ['concat', `shape-${style}-`, ['coalesce', ['get', 'mainCategory'], 'other'], '-',
    ['case', ['get', 'recommendable'], 'l', 's']] as unknown as maplibregl.ExpressionSpecification
}

/**
 * Draws marker images when the map first needs them (also after a style switch such as dark mode,
 * which drops added images).
 */
export function provideMarkerImages(map: maplibregl.Map) {
  map.on('styleimagemissing', (e: { id: string }) => {
    const m = /^shape-(dot|star|pin|diamond)-([a-z_]+)-(l|s)$/.exec(e.id)
    if (!m || map.hasImage(e.id)) return
    const color = MAIN_CATEGORY_COLORS[m[2]] ?? '#495057'
    map.addImage(e.id, drawMarker(m[1] as MarkerStyle, color, SIZES[m[3] as 'l' | 's']), { pixelRatio: RATIO })
  })
}
