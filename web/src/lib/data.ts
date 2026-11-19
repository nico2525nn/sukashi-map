import type {
  AirportRecord,
  Coord,
  PlaceCandidate,
  RailLineEntry,
  StationRecord,
} from '@/types'
import { formatLatLon } from '@/lib/geo'

const curatedPlaces: PlaceCandidate[] = [
  ['鎌倉', '神奈川県鎌倉市', 35.3192, 139.5467, 'JP'],
  ['箱根湯本', '神奈川県足柄下郡箱根町', 35.2324, 139.1069, 'JP'],
  ['江ノ島', '神奈川県藤沢市', 35.2998, 139.4803, 'JP'],
  ['熱海', '静岡県熱海市', 35.1036, 139.0777, 'JP'],
  ['河口湖', '山梨県南都留郡富士河口湖町', 35.5171, 138.7518, 'JP'],
  ['軽井沢', '長野県北佐久郡軽井沢町', 36.3427, 138.6352, 'JP'],
  ['日光東照宮', '栃木県日光市', 36.7581, 139.5989, 'JP'],
  ['東京タワー', '東京都港区', 35.6586, 139.7454, 'JP'],
  ['渋谷スカイ', '東京都渋谷区', 35.6585, 139.7022, 'JP'],
  ['横浜みなとみらい', '神奈川県横浜市', 35.455, 139.6313, 'JP'],
  ['京都嵐山', '京都府京都市', 35.0116, 135.6767, 'JP'],
  ['天橋立', '京都府宮津市', 35.57, 135.1917, 'JP'],
  ['淡路島', '兵庫県淡路市', 34.5947, 135.0203, 'JP'],
  ['しまなみ海道（尾道）', '広島県尾道市', 34.4048, 133.1932, 'JP'],
  ['出雲大社', '島根県出雲市', 35.402, 132.6854, 'JP'],
  ['阿蘇草千里', '熊本県阿蘇市', 32.8851, 131.0518, 'JP'],
  ['別府温泉', '大分県別府市', 33.2795, 131.4975, 'JP'],
  ['美ら海水族館', '沖縄県国頭郡本部町', 26.6943, 127.8779, 'JP'],
  ['小樽運河', '北海道小樽市', 43.1994, 141.0018, 'JP'],
  ['知床五湖', '北海道斜里郡斜里町', 44.1228, 145.0805, 'JP'],
  ['Honolulu', 'Hawaii, United States', 21.3069, -157.8583, 'US'],
  ['San Francisco', 'California, United States', 37.7749, -122.4194, 'US'],
  ['New York', 'New York, United States', 40.7128, -74.006, 'US'],
  ['London St Pancras', 'United Kingdom', 51.5319, -0.1263, 'GB'],
  ['Paris Gare du Nord', 'France', 48.8809, 2.3553, 'FR'],
  ['Taipei Main Station', 'Taiwan', 25.0478, 121.517, 'TW'],
  ['Seoul Station', 'South Korea', 37.5547, 126.9707, 'KR'],
].map(([name, subtitle, latitude, longitude, countryCode]) => ({
  name: name as string,
  subtitle: subtitle as string,
  latitude: latitude as number,
  longitude: longitude as number,
  countryCode: countryCode as string,
}))

let airportsPromise: Promise<AirportRecord[]> | null = null
let stationsPromise: Promise<StationRecord[]> | null = null
let linesPromise: Promise<RailLineEntry[]> | null = null

function loadJson<T>(path: string): Promise<T> {
  return fetch(path).then((res) => {
    if (!res.ok) throw new Error(`failed to load ${path}`)
    return res.json() as Promise<T>
  })
}

export function loadAirports(): Promise<AirportRecord[]> {
  airportsPromise ??= loadJson<AirportRecord[]>(`${import.meta.env.BASE_URL}data/airports.json`).catch((err) => {
    airportsPromise = null
    throw err
  })
  return airportsPromise
}

export function loadStations(): Promise<StationRecord[]> {
  stationsPromise ??= loadJson<StationRecord[]>(`${import.meta.env.BASE_URL}data/stations.json`).catch((err) => {
    stationsPromise = null
    throw err
  })
  return stationsPromise
}

export function loadRailLines(): Promise<RailLineEntry[]> {
  linesPromise ??= loadJson<RailLineEntry[]>(`${import.meta.env.BASE_URL}data/rail_lines.json`).catch((err) => {
    linesPromise = null
    throw err
  })
  return linesPromise
}

export async function searchAirports(query: string): Promise<AirportRecord[]> {
  const list = await loadAirports()
  const q = query.trim().toUpperCase()
  if (!q) {
    return list
      .filter((a) => ['HND', 'NRT', 'KIX', 'ITM', 'OKA', 'CTS', 'FUK', 'NGO', 'LAX', 'JFK', 'LHR', 'CDG', 'HNL', 'SIN', 'ICN'].includes(a.iata))
      .slice(0, 20)
  }
  return list
    .filter(
      (a) =>
        a.iata.includes(q) ||
        a.icao.includes(q) ||
        a.name.toUpperCase().includes(q) ||
        a.city.toUpperCase().includes(q),
    )
    .slice(0, 24)
}

export async function searchStations(query: string, lineFilter = ''): Promise<StationRecord[]> {
  const list = await loadStations()
  const q = query.trim()
  const line = lineFilter.trim()
  const base = line ? list.filter((s) => s.line === line) : list
  if (!q) return base.slice(0, 24)
  return base
    .filter((s) => s.name.includes(q) || s.line.includes(q) || s.company.includes(q))
    .slice(0, 24)
}

export async function searchRailLines(query: string): Promise<RailLineEntry[]> {
  const list = await loadRailLines()
  const q = query.trim()
  if (!q) return list.slice(0, 24)
  return list.filter((l) => l.line.includes(q) || l.company.includes(q)).slice(0, 24)
}

export async function searchPlaces(
  query: string,
  biasLat: number | null = null,
  biasLon: number | null = null,
): Promise<PlaceCandidate[]> {
  const q = query.trim()
  const local = q
    ? curatedPlaces.filter((p) => p.name.includes(q) || p.subtitle.includes(q))
    : curatedPlaces.slice(0, 18)
  if (!q) return local

  let timer: ReturnType<typeof setTimeout> | undefined
  try {
    const controller = new AbortController()
    timer = setTimeout(() => controller.abort(), 4500)
    const url = new URL('https://photon.komoot.io/api/')
    url.searchParams.set('q', q)
    url.searchParams.set('limit', '12')
    if (biasLat != null && biasLon != null) {
      url.searchParams.set('lat', String(biasLat))
      url.searchParams.set('lon', String(biasLon))
    }
    const res = await fetch(url, { signal: controller.signal })
    if (!res.ok) throw new Error(String(res.status))
    const json = (await res.json()) as {
      features?: Array<{
        geometry?: { coordinates?: [number, number] }
        properties?: Record<string, string>
      }>
    }
    const remote = (json.features ?? []).flatMap((f) => {
      const coords = f.geometry?.coordinates
      if (!coords) return []
      const props = f.properties ?? {}
      const name = props.name || props.city || ''
      if (!name) return []
      const subtitle = [props.city, props.state, props.country]
        .filter((v, i, arr) => v && v !== name && arr.indexOf(v) === i)
        .join(', ')
      return [{
        name,
        subtitle,
        latitude: coords[1],
        longitude: coords[0],
        countryCode: (props.countrycode ?? '').toUpperCase(),
      }]
    })
    const seen = new Set<string>()
    return [...local, ...remote].filter((p) => {
      const key = `${p.name}:${p.latitude.toFixed(2)}:${p.longitude.toFixed(2)}`
      if (seen.has(key)) return false
      seen.add(key)
      return true
    }).slice(0, 20)
  } catch {
    return local
  } finally {
    if (timer) clearTimeout(timer)
  }
}

export async function reverseGeocode(lat: number, lon: number): Promise<string> {
  let timer: ReturnType<typeof setTimeout> | undefined
  try {
    const controller = new AbortController()
    timer = setTimeout(() => controller.abort(), 4500)
    const url = new URL('https://nominatim.openstreetmap.org/reverse')
    url.searchParams.set('format', 'jsonv2')
    url.searchParams.set('lat', String(lat))
    url.searchParams.set('lon', String(lon))
    url.searchParams.set('zoom', '14')
    url.searchParams.set('addressdetails', '1')
    url.searchParams.set('accept-language', 'ja,en')
    const res = await fetch(url, { signal: controller.signal })
    if (!res.ok) throw new Error(String(res.status))
    const json = (await res.json()) as { display_name?: string; address?: Record<string, string> }
    if (json.address) {
      const a = json.address
      const region = a.state || a.province || a.region || a.city || a.town || a.village
      const country = a.country
      if (region || country) return [region, country].filter(Boolean).join(', ')
    }
    return json.display_name?.split(',').slice(0, 2).join(', ') || formatLatLon(lat, lon, 5)
  } catch {
    return formatLatLon(lat, lon, 5)
  } finally {
    if (timer) clearTimeout(timer)
  }
}

export async function fetchDrivingRoute(
  dep: Coord,
  dest: Coord,
): Promise<Coord[][]> {
  let timer: ReturnType<typeof setTimeout> | undefined
  try {
    const controller = new AbortController()
    timer = setTimeout(() => controller.abort(), 8000)
    const url = `https://router.project-osrm.org/route/v1/driving/${dep.lon},${dep.lat};${dest.lon},${dest.lat}?overview=full&geometries=geojson`
    const res = await fetch(url, { signal: controller.signal })
    if (!res.ok) throw new Error(String(res.status))
    const json = (await res.json()) as {
      routes?: Array<{ geometry?: { coordinates?: [number, number][] } }>
    }
    const coords = json.routes?.[0]?.geometry?.coordinates
    if (!coords?.length) return []
    return [coords.map(([lon, lat]) => ({ lon, lat }))]
  } catch {
    return []
  } finally {
    if (timer) clearTimeout(timer)
  }
}
