import init, {
  format_lat_lon,
  great_circle_points,
  haversine_km,
  parse_latitude,
  parse_longitude,
  simplify_polyline,
} from '@/wasm/geo/geo_core'

let ready: Promise<void> | null = null
let initialized = false

export async function initGeoWasm(): Promise<void> {
  if (initialized) return
  ready ??= init()
    .then(() => {
      initialized = true
    })
    .catch((err) => {
      ready = null
      throw err
    })
  await ready
}

export function wasmReady(): boolean {
  return initialized
}

const fallbackFormat = (lat: number, lon: number, decimals = 5) => {
  const d = Math.max(0, Math.min(8, decimals))
  const ns = lat >= 0 ? 'N' : 'S'
  const ew = lon >= 0 ? 'E' : 'W'
  return `${Math.abs(lat).toFixed(d)}°${ns}, ${Math.abs(lon).toFixed(d)}°${ew}`
}

export function formatLatLon(lat: number, lon: number, decimals = 5): string {
  if (wasmReady()) return format_lat_lon(lat, lon, decimals)
  return fallbackFormat(lat, lon, decimals)
}

export function parseLat(text: string): number | null {
  if (wasmReady()) return parse_latitude(text) ?? null
  return fallbackParse(text, true)
}

export function parseLon(text: string): number | null {
  if (wasmReady()) return parse_longitude(text) ?? null
  return fallbackParse(text, false)
}

export function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  if (wasmReady()) return haversine_km(lat1, lon1, lat2, lon2)
  const p1 = (lat1 * Math.PI) / 180
  const p2 = (lat2 * Math.PI) / 180
  const dp = ((lat2 - lat1) * Math.PI) / 180
  const dl = ((lon2 - lon1) * Math.PI) / 180
  const a = Math.sin(dp / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) ** 2
  return 2 * 6371.0088 * Math.asin(Math.sqrt(a))
}

export function greatCircle(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
  segments = 96,
): { lon: number; lat: number }[] {
  if (wasmReady()) {
    const flat = great_circle_points(lat1, lon1, lat2, lon2, segments)
    const out: { lon: number; lat: number }[] = []
    for (let i = 0; i < flat.length; i += 2) out.push({ lon: flat[i], lat: flat[i + 1] })
    return out
  }
  const toRad = Math.PI / 180
  const p1 = lat1 * toRad
  const l1 = lon1 * toRad
  const p2 = lat2 * toRad
  const l2 = lon2 * toRad
  const d = 2 * Math.asin(
    Math.sqrt(
      Math.sin((p2 - p1) / 2) ** 2 +
        Math.cos(p1) * Math.cos(p2) * Math.sin((l2 - l1) / 2) ** 2,
    ),
  )
  if (d < 1e-6 || Math.abs(Math.sin(d)) < 1e-6) {
    return [{ lon: lon1, lat: lat1 }, { lon: lon2, lat: lat2 }]
  }
  const count = Math.max(2, Math.min(segments, 96))
  const out: { lon: number; lat: number }[] = []
  for (let i = 0; i <= count; i++) {
    const f = i / count
    const a = Math.sin((1 - f) * d) / Math.sin(d)
    const b = Math.sin(f * d) / Math.sin(d)
    const x = a * Math.cos(p1) * Math.cos(l1) + b * Math.cos(p2) * Math.cos(l2)
    const y = a * Math.cos(p1) * Math.sin(l1) + b * Math.cos(p2) * Math.sin(l2)
    const z = a * Math.sin(p1) + b * Math.sin(p2)
    out.push({
      lon: Math.atan2(y, x) / toRad,
      lat: Math.atan2(z, Math.hypot(x, y)) / toRad,
    })
  }
  return out
}

export function simplifyPolyline(points: { lon: number; lat: number }[], epsilonMeters = 150) {
  if (!wasmReady() || points.length <= 3) return points
  const flat: number[] = []
  for (const p of points) flat.push(p.lon, p.lat)
  const out = simplify_polyline(new Float64Array(flat), epsilonMeters)
  const result: { lon: number; lat: number }[] = []
  for (let i = 0; i < out.length; i += 2) result.push({ lon: out[i], lat: out[i + 1] })
  return result
}

function fallbackParse(text: string, isLat: boolean): number | null {
  const raw = text.trim().toUpperCase().replace(/，/g, ',')
  if (!raw) return null
  let body = raw
  let direction: string | null = null
  if (/^[NSEW]/.test(body)) {
    direction = body[0]
    body = body.slice(1).trim()
  } else if (/[NSEW]$/.test(body)) {
    direction = body[body.length - 1]
    body = body.slice(0, -1).trim()
  }
  if (/[NSEW]/.test(body)) return null
  if (direction) {
    if (isLat && !/^[NS]$/.test(direction)) return null
    if (!isLat && !/^[EW]$/.test(direction)) return null
  }
  const hasMinSec = /['"′″:]/.test(body)
  const hasDegree = body.includes('°')
  if (!hasMinSec && (body.match(/,/g)?.length ?? 0) === 1) {
    body = body.replace(',', '.').replace('°', ' ')
  }
  const normalized = body.replace(/[°'"′″:,]/g, ' ').trim()
  const tokens = normalized.split(/\s+/).filter(Boolean)
  if (!tokens.length || tokens.length > 3) return null
  const values = tokens.map(Number)
  if (values.some((v) => !Number.isFinite(v))) return null
  const minutesFirst =
    !hasDegree && tokens.length === 2 && /['′]/.test(body)
  let degrees: number
  let minutes: number
  let seconds: number
  if (values.length === 1) {
    degrees = values[0]
    minutes = 0
    seconds = 0
  } else if (minutesFirst) {
    if (values[0] < 0 || values[0] >= 60 || values[1] < 0 || values[1] >= 60) return null
    degrees = 0
    minutes = values[0]
    seconds = values[1]
  } else {
    if (!Number.isInteger(values[0])) return null
    minutes = values[1] ?? 0
    seconds = values[2] ?? 0
    if (minutes < 0 || minutes >= 60 || seconds < 0 || seconds >= 60) return null
    degrees = values[0]
  }
  const tokenNegative = tokens[0].startsWith('-')
  if (direction && tokenNegative) return null
  const magnitude = Math.abs(degrees) + minutes / 60 + seconds / 3600
  const negative = direction === 'S' || direction === 'W' || (!direction && tokenNegative)
  const value = negative ? -magnitude : magnitude
  if (!Number.isFinite(value)) return null
  if (isLat ? value < -90 || value > 90 : value < -180 || value > 180) return null
  return value
}
