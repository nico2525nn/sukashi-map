export type Ring = [number, number][]

export interface MapDatasets {
  world: Ring[]
  japanCoast: Ring[]
  japanPrefectures: Ring[]
  japanWater: Ring[]
  usStates: Ring[]
}

const cache = new Map<string, Promise<Ring[]>>()

function dataUrl(name: string): string {
  return `${import.meta.env.BASE_URL}data/${name}`
}

function loadRings(name: string): Promise<Ring[]> {
  const cached = cache.get(name)
  if (cached) return cached
  const promise = fetch(dataUrl(name))
    .then((res) => {
      if (!res.ok) throw new Error(`map asset ${name}: ${res.status}`)
      return res.json() as Promise<Ring[]>
    })
    .catch((err) => {
      cache.delete(name)
      throw err
    })
  cache.set(name, promise)
  return promise
}

export const loadWorldMap = () => loadRings('world_map_coarse.json')
export const loadJapanCoast = () => loadRings('japan_coastline_highres.json')
export const loadJapanPrefectures = () => loadRings('japan_prefectures.json')
export const loadJapanWater = () => loadRings('japan_waterbodies.json')
export const loadUsStates = () => loadRings('us_states.json')

export async function loadAllMapDatasets(): Promise<MapDatasets> {
  const [world, japanCoast, japanPrefectures, japanWater, usStates] = await Promise.all([
    loadWorldMap(),
    loadJapanCoast(),
    loadJapanPrefectures(),
    loadJapanWater(),
    loadUsStates(),
  ])
  return { world, japanCoast, japanPrefectures, japanWater, usStates }
}

export interface Bounds {
  minLon: number
  maxLon: number
  minLat: number
  maxLat: number
}

export function ringBounds(ring: Ring): Bounds | null {
  if (!ring.length) return null
  let minLon = Infinity
  let maxLon = -Infinity
  let minLat = Infinity
  let maxLat = -Infinity
  for (const [lon, lat] of ring) {
    if (lon < minLon) minLon = lon
    if (lon > maxLon) maxLon = lon
    if (lat < minLat) minLat = lat
    if (lat > maxLat) maxLat = lat
  }
  return { minLon, maxLon, minLat, maxLat }
}

export function pointInRing(lon: number, lat: number, ring: Ring): boolean {
  let inside = false
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i]
    const [xj, yj] = ring[j]
    if (yi > lat !== yj > lat && lon < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) {
      inside = !inside
    }
  }
  return inside
}

export function findEnclosingRing(lon: number, lat: number, rings: Ring[]): Ring | null {
  for (const ring of rings) {
    const b = ringBounds(ring)
    if (!b) continue
    if (lon < b.minLon || lon > b.maxLon || lat < b.minLat || lat > b.maxLat) continue
    if (pointInRing(lon, lat, ring)) return ring
  }
  return null
}

export function isInJapan(lon: number, lat: number): boolean {
  return lon >= 122 && lon <= 148 && lat >= 24 && lat <= 47
}

export function isInUs(lon: number, lat: number): boolean {
  return lon >= -126 && lon <= -66 && lat >= 23 && lat <= 50
}

export function boundsAround(lon: number, lat: number, lonSpan: number, latSpan: number): Bounds {
  return {
    minLon: lon - lonSpan / 2,
    maxLon: lon + lonSpan / 2,
    minLat: lat - latSpan / 2,
    maxLat: lat + latSpan / 2,
  }
}
