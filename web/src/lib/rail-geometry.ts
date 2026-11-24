import type { Coord } from '@/types'
import { loadRailLines } from '@/lib/data'

const PRECISION = 1e5
const SNAP_DEG = 0.05 // ~5.5 km: a station must be near the line
const GOOD_SNAP_DEG = 0.02 // ~2.2 km: prefer a chain containing both stations
const LINK_DEG = 0.09 // ~10 km: maximum gap between adjacent fragments
const MAX_STEP_DEG = 0.9 // reject corrupted 100 km jumps inside a chain
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

function distance(a: Coord, b: Coord): number {
  const dx = (a.lon - b.lon) * Math.cos((((a.lat + b.lat) / 2) * Math.PI) / 180)
  const dy = a.lat - b.lat
  return Math.hypot(dx, dy)
}

interface Edge {
  to: number
  cost: number
}

function shortestPath(adjacency: Edge[][], source: number, sink: number): number[] {
  const size = adjacency.length
  const dist = Array<number>(size).fill(Number.POSITIVE_INFINITY)
  const previous = Array<number>(size).fill(-1)
  const used = Array<boolean>(size).fill(false)
  dist[source] = 0
  for (let step = 0; step < size; step++) {
    let current = -1
    for (let i = 0; i < size; i++) {
      if (!used[i] && dist[i] < Number.POSITIVE_INFINITY && (current < 0 || dist[i] < dist[current])) {
        current = i
      }
    }
    if (current < 0) break
    if (current === sink) break
    used[current] = true
    for (const edge of adjacency[current]) {
      if (used[edge.to]) continue
      const candidate = dist[current] + edge.cost
      if (candidate < dist[edge.to]) {
        dist[edge.to] = candidate
        previous[edge.to] = current
      }
    }
  }
  if (dist[sink] === Number.POSITIVE_INFINITY) return []
  const path: number[] = []
  for (let at = sink; at !== -1; at = previous[at]) {
    path.push(at)
    if (at === source) break
  }
  if (path[path.length - 1] !== source) return []
  return path.reverse()
}

/**
 * Pick the actual track between two stations.
 *
 * Chains can contain bad jumps where source sections were stitched in the
 * wrong order, and a line can have several disconnected branches. Treating
 * every point as a graph node and taking the shortest connected path avoids
 * both the "draw the whole line" fallback and the "jump across the country"
 * slice. Station endpoints are connected with a short straight spur when the
 * source geometry stops a few kilometres short.
 */
export function selectRouteFromChains(
  chains: Coord[][],
  departure: Coord,
  arrival: Coord,
): Coord[][] {
  const points: Coord[] = []
  const ranges: Array<{ start: number; end: number }> = []
  for (const chain of chains) {
    if (chain.length < 2) continue
    const start = points.length
    points.push(...chain)
    ranges.push({ start, end: points.length - 1 })
  }
  if (!points.length) return []

  const source = points.length
  const sink = source + 1
  const adjacency: Edge[][] = Array.from({ length: sink + 1 }, () => [])
  const addEdge = (a: number, b: number, cost: number) => {
    if (a === b) return
    adjacency[a].push({ to: b, cost })
    adjacency[b].push({ to: a, cost })
  }

  // If one chain contains both stations, follow that chain. Searching the
  // whole graph first makes a station spur to a far point look cheaper and
  // can skip most of the track between two nearby stations.
  const direct = ranges
    .map((range) => {
      let depIndex = -1
      let arrIndex = -1
      let depDistance = Number.POSITIVE_INFINITY
      let arrDistance = Number.POSITIVE_INFINITY
      for (let i = range.start; i <= range.end; i++) {
        const toDep = distance(points[i], departure)
        if (toDep < depDistance) {
          depDistance = toDep
          depIndex = i
        }
        const toArr = distance(points[i], arrival)
        if (toArr < arrDistance) {
          arrDistance = toArr
          arrIndex = i
        }
      }
      if (depIndex < 0 || arrIndex < 0) return null
      if (depDistance > GOOD_SNAP_DEG || arrDistance > GOOD_SNAP_DEG) return null
      const low = Math.min(depIndex, arrIndex)
      const high = Math.max(depIndex, arrIndex)
      let length = 0
      for (let i = low; i < high; i++) {
        const gap = distance(points[i], points[i + 1])
        if (gap > MAX_STEP_DEG) return null
        length += gap
      }
      return { depIndex, arrIndex, length }
    })
    .filter((candidate): candidate is { depIndex: number; arrIndex: number; length: number } =>
      candidate != null,
    )
    .sort((a, b) => a.length - b.length)
  if (direct.length) {
    const best = direct[0]
    const low = Math.min(best.depIndex, best.arrIndex)
    const high = Math.max(best.depIndex, best.arrIndex)
    const slice =
      best.depIndex <= best.arrIndex
        ? points.slice(low, high + 1)
        : points.slice(low, high + 1).reverse()
    const result: Coord[] = []
    const push = (point: Coord) => {
      const last = result[result.length - 1]
      if (!last || distance(last, point) > 1e-6) result.push(point)
    }
    push(departure)
    for (const point of slice) push(point)
    push(arrival)
    return result.length >= 2 ? [result] : []
  }

  for (const range of ranges) {
    for (let i = range.start; i < range.end; i++) {
      const gap = distance(points[i], points[i + 1])
      // A 100 km jump means the generator stitched unrelated fragments.
      if (gap <= MAX_STEP_DEG) addEdge(i, i + 1, gap)
    }
  }

  const endpoints: Array<{ node: number; chain: number }> = []
  ranges.forEach((range, chain) => {
    endpoints.push({ node: range.start, chain }, { node: range.end, chain })
  })
  for (let i = 0; i < endpoints.length; i++) {
    for (let j = i + 1; j < endpoints.length; j++) {
      if (endpoints[i].chain === endpoints[j].chain) continue
      const gap = distance(points[endpoints[i].node], points[endpoints[j].node])
      if (gap <= LINK_DEG) addEdge(endpoints[i].node, endpoints[j].node, gap)
    }
  }

  for (const range of ranges) {
    let departureBest: { index: number; distance: number } | null = null
    let arrivalBest: { index: number; distance: number } | null = null
    for (let i = range.start; i <= range.end; i++) {
      const toDeparture = distance(points[i], departure)
      if (toDeparture <= SNAP_DEG && (!departureBest || toDeparture < departureBest.distance)) {
        departureBest = { index: i, distance: toDeparture }
      }
      const toArrival = distance(points[i], arrival)
      if (toArrival <= SNAP_DEG && (!arrivalBest || toArrival < arrivalBest.distance)) {
        arrivalBest = { index: i, distance: toArrival }
      }
    }
    if (departureBest) addEdge(source, departureBest.index, departureBest.distance)
    if (arrivalBest) addEdge(arrivalBest.index, sink, arrivalBest.distance)
  }
  if (!adjacency[source].length || !adjacency[sink].length) return []

  const path = shortestPath(adjacency, source, sink)
  if (!path.length) return []
  const trackNodes = path.filter((node) => node < points.length)
  if (!trackNodes.length) return []

  const result: Coord[] = []
  const push = (point: Coord) => {
    const last = result[result.length - 1]
    if (!last || distance(last, point) > 1e-6) result.push(point)
  }
  push(departure)
  for (const node of trackNodes) push(points[node])
  push(arrival)
  return result.length >= 2 ? [result] : []
}

/**
 * Returns the actual track polyline between the two stations for a line.
 * Files are tiny (typically a few hundred bytes) and fetched only for the
 * selected train line; no 14 MB database is downloaded.
 */
export async function loadTrainRoute(
  company: string,
  lineName: string,
  departure: Coord | null,
  arrival: Coord | null,
): Promise<Coord[][]> {
  if (!lineName.trim() || !departure || !arrival) return []
  const lines = await loadRailLines()
  let line = lines.find(
    (entry) => entry.line === lineName && entry.company === company && entry.geometryKey,
  )
  if (!line && !company.trim()) {
    // Old saved entries may not have a company. Only use the line when the
    // name is unambiguous; never merge JR East/Central/West by name alone.
    const matches = lines.filter((entry) => entry.line === lineName && entry.geometryKey)
    if (matches.length === 1) line = matches[0]
  }
  if (!line?.geometryKey) return []
  const chains = await fetchGeometry(line.geometryKey)
  return selectRouteFromChains(chains, departure, arrival)
}
