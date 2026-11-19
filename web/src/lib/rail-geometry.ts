import type { Coord } from '@/types'
import { loadRailLines } from '@/lib/data'

const PRECISION = 1e5
const cache = new Map<string, Promise<Coord[][]>>()

export function decodePolyline(encoded: string, precision = PRECISION): Coord[] {
  const points: Coord[] = []
  let index = 0
  let lat = 0
  let lon = 0
  while (index < encoded.length) {
    for (const axis of [0, 1]) {
      let shift = 0
      let result = 0
      let byte: number
      do {
        if (index >= encoded.length) return points
        byte = encoded.charCodeAt(index++) - 63
        result |= (byte & 0x1f) << shift
        shift += 5
      } while (byte >= 0x20)
      const delta = result & 1 ? ~(result >> 1) : result >> 1
      if (axis === 0) lat += delta
      else lon += delta
    }
    points.push({ lon: lon / precision, lat: lat / precision })
  }
  return points
}

async function fetchGeometry(key: string): Promise<Coord[][]> {
  const cached = cache.get(key)
  if (cached) return cached
  const promise = (async () => {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 5000)
    try {
      const res = await fetch(
        `${import.meta.env.BASE_URL}data/rail_geometry/${key}.json`,
        { signal: controller.signal },
      )
      if (!res.ok) throw new Error(`rail geometry ${key}: ${res.status}`)
      const data = (await res.json()) as { chains?: string[] }
      return (data.chains ?? []).map((chain) => decodePolyline(chain)).filter((c) => c.length >= 2)
    } finally {
      clearTimeout(timer)
    }
  })()
  cache.set(key, promise)
  promise.catch(() => cache.delete(key))
  return promise
}

function nearestIndex(chain: Coord[], point: Coord): { index: number; distance: number } {
  let best = 0
  let bestD = Number.POSITIVE_INFINITY
  for (let i = 0; i < chain.length; i++) {
    const dy = chain[i].lat - point.lat
    const dx = (chain[i].lon - point.lon) * Math.cos((point.lat * Math.PI) / 180)
    const d = dx * dx + dy * dy
    if (d < bestD) {
      bestD = d
      best = i
    }
  }
  return { index: best, distance: Math.sqrt(bestD) }
}

function sliceChain(chain: Coord[], start: number, end: number): Coord[] {
  if (start <= end) return chain.slice(start, end + 1)
  return chain.slice(end, start + 1).reverse()
}

/**
 * Returns the actual track polyline between the two stations for a line.
 * Files are tiny (typically a few hundred bytes) and fetched only for the
 * selected train line; no 14 MB database is downloaded.
 */
export async function loadTrainRoute(
  lineName: string,
  departure: Coord | null,
  arrival: Coord | null,
): Promise<Coord[][]> {
  if (!lineName.trim() || !departure || !arrival) return []
  const lines = await loadRailLines()
  const line = lines.find((entry) => entry.line === lineName && entry.geometryKey)
  if (!line?.geometryKey) return []
  const chains = await fetchGeometry(line.geometryKey)
  const candidates = chains
    .map((chain) => {
      const from = nearestIndex(chain, departure)
      const to = nearestIndex(chain, arrival)
      return { chain, from, to, score: from.distance + to.distance }
    })
    .filter((c) => c.from.distance < 0.22 && c.to.distance < 0.22)
    .sort((a, b) => a.score - b.score)
  if (candidates.length) {
    const best = candidates[0]
    return [sliceChain(best.chain, best.from.index, best.to.index)]
  }
  // Branches: if no single chain holds both stations, return the closest
  // chain slices rather than inventing a straight line across the country.
  const fromCandidate = chains
    .map((chain) => nearestIndex(chain, departure))
    .sort((a, b) => a.distance - b.distance)[0]
  const toCandidate = chains
    .map((chain) => nearestIndex(chain, arrival))
    .sort((a, b) => a.distance - b.distance)[0]
  if (fromCandidate && toCandidate) {
    return chains.map((chain) => chain)
  }
  return []
}
