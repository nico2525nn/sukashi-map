export type Ring = [number, number][]

interface EncodedRings {
  scale: number
  rings: number[][]
}

/** Decode the compact delta-encoded integer rings used by the Web assets. */
export function decodeRings(data: Ring[] | EncodedRings): Ring[] {
  if (Array.isArray(data)) return data
  const { scale, rings } = data
  return rings.map((flat) => {
    const ring: Ring = []
    let x = 0
    let y = 0
    for (let index = 0; index < flat.length; index += 2) {
      x += flat[index]
      y += flat[index + 1]
      ring.push([x / scale, y / scale])
    }
    return ring
  })
}

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
      return res.json().then(decodeRings)
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

const boundsCache = new WeakMap<Ring, Bounds>()

export function ringBounds(ring: Ring): Bounds | null {
  const cached = boundsCache.get(ring)
  if (cached) return cached
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
  const bounds = { minLon, maxLon, minLat, maxLat }
  boundsCache.set(ring, bounds)
  return bounds
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

const containingCache = new WeakMap<Ring[], Map<string, Ring | null>>()

export function findEnclosingRing(lon: number, lat: number, rings: Ring[]): Ring | null {
  let cache = containingCache.get(rings)
  if (!cache) {
    cache = new Map()
    containingCache.set(rings, cache)
  }
  const key = `${lon.toFixed(3)}:${lat.toFixed(3)}`
  if (cache.has(key)) return cache.get(key) ?? null
  for (const ring of rings) {
    const b = ringBounds(ring)
    if (!b) continue
    if (lon < b.minLon || lon > b.maxLon || lat < b.minLat || lat > b.maxLat) continue
    if (pointInRing(lon, lat, ring)) {
      cache.set(key, ring)
      return ring
    }
  }
  cache.set(key, null)
  return null
}

export function isInJapan(lon: number, lat: number): boolean {
  const main = lon >= 122 && lon <= 148 && lat >= 24 && lat <= 47
  // Minamitorishima is administered by Tokyo but lies far east of the main box.
  const minamitorishima = lon >= 153 && lon <= 154.5 && lat >= 23.5 && lat <= 25
  return main || minamitorishima
}

export function isInUs(lon: number, lat: number): boolean {
  // Mainland, Alaska, Hawaii, and the Aleutians on the positive side of the
  // dateline; the bundled US state polygons contain all of them.
  const inLongitude = (lon >= -180 && lon <= -60) || (lon >= 170 && lon <= 180)
  return inLongitude && lat >= 14 && lat <= 84
}

export function boundsAround(lon: number, lat: number, lonSpan: number, latSpan: number): Bounds {
  return {
    minLon: lon - lonSpan / 2,
    maxLon: lon + lonSpan / 2,
    minLat: lat - latSpan / 2,
    maxLat: lat + latSpan / 2,
  }
}

let datasetCache: MapDatasets = {
  world: [],
  japanCoast: [],
  japanPrefectures: [],
  japanWater: [],
  usStates: [],
}
let worldPromise: Promise<Ring[]> | null = null
let japanPromise: Promise<[Ring[], Ring[], Ring[]]> | null = null
let usPromise: Promise<Ring[]> | null = null

/**
 * Loads only the layers needed by the current anchors, reusing everything already fetched.
 * World is always required as the fallback/context layer.
 */
export async function ensureMapDatasetsFor(points: { lon: number; lat: number }[]): Promise<MapDatasets> {
  const needJapan = points.some((p) => isInJapan(p.lon, p.lat))
  const needUs = points.some((p) => isInUs(p.lon, p.lat))
  worldPromise ??= loadWorldMap().catch((err) => {
    worldPromise = null
    throw err
  })
  if (needJapan) {
    japanPromise ??= Promise.all([loadJapanCoast(), loadJapanPrefectures(), loadJapanWater()]).catch((err) => {
      japanPromise = null
      throw err
    })
  }
  if (needUs) {
    usPromise ??= loadUsStates().catch((err) => {
      usPromise = null
      throw err
    })
  }
  const [world, japan, us] = await Promise.all([
    worldPromise,
    japanPromise ?? Promise.resolve(null),
    usPromise ?? Promise.resolve(null),
  ])
  datasetCache = {
    world,
    japanCoast: japan?.[0] ?? datasetCache.japanCoast,
    japanPrefectures: japan?.[1] ?? datasetCache.japanPrefectures,
    japanWater: japan?.[2] ?? datasetCache.japanWater,
    usStates: us ?? datasetCache.usStates,
  }
  return datasetCache
}

export const EMPTY_MAP_DATASETS: MapDatasets = {
  world: [],
  japanCoast: [],
  japanPrefectures: [],
  japanWater: [],
  usStates: [],
}
