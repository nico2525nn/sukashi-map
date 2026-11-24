import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { decodePolyline, selectRouteFromChains } from '@/lib/rail-geometry'

const dataDir = fileURLToPath(new URL('../../../public/data/', import.meta.url))

interface RailLine {
  line: string
  company: string
  geometryKey?: string
}

interface RailGeometry {
  line?: string
  company?: string
  chains: string[]
}

function distanceKm(a: { lon: number; lat: number }, b: { lon: number; lat: number }) {
  return (
    Math.hypot(
      (a.lon - b.lon) * Math.cos(((a.lat + b.lat) / 2) * (Math.PI / 180)),
      a.lat - b.lat,
    ) * 111
  )
}

function routeFor(lineName: string, company: string, dep: { lon: number; lat: number }, arr: { lon: number; lat: number }) {
  const lines = JSON.parse(readFileSync(`${dataDir}rail_lines.json`, 'utf8')) as RailLine[]
  const line = lines.find((entry) => entry.line === lineName && entry.company === company)
  expect(line?.geometryKey, `${company} ${lineName}`).toBeTruthy()
  const geometry = JSON.parse(
    readFileSync(`${dataDir}rail_geometry/${line!.geometryKey}.json`, 'utf8'),
  ) as RailGeometry
  return selectRouteFromChains(
    geometry.chains.map((chain) => decodePolyline(chain)),
    dep,
    arr,
  ).flat()
}

describe('selectRouteFromChains', () => {
  const tokyo = { lon: 139.767, lat: 35.681 }
  const yokohama = { lon: 139.622, lat: 35.444 }
  const atami = { lon: 139.078, lat: 35.104 }

  it('slices a single chain between both stations', () => {
    const chain = [tokyo, yokohama, atami]
    const route = selectRouteFromChains([chain], tokyo, atami)
    expect(route).toHaveLength(1)
    expect(route[0][0]).toBe(tokyo)
    expect(route[0][route[0].length - 1]).toBe(atami)
  })

  it('stitches the fragments on the shortest path instead of returning every branch', () => {
    const tokyoToYokohama = [tokyo, yokohama]
    const yokohamaToAtami = [yokohama, atami]
    const unrelatedBranch = [
      { lon: 139.4, lat: 35.33 },
      { lon: 139.34, lat: 35.32 },
    ]
    const route = selectRouteFromChains(
      [tokyoToYokohama, yokohamaToAtami, unrelatedBranch],
      tokyo,
      atami,
    )
    const points = route.flat()
    expect(points.some((p) => p.lon === 139.34)).toBe(false)
    expect(points[0]).toEqual(tokyo)
    expect(points[points.length - 1]).toEqual(atami)
  })

  it('returns nothing when the stations are nowhere near the line', () => {
    const chain = [
      { lon: 135.5, lat: 34.7 },
      { lon: 135.6, lat: 34.8 },
    ]
    expect(selectRouteFromChains([chain], tokyo, atami)).toEqual([])
  })
})

describe('generated rail data', () => {
  const lines = JSON.parse(readFileSync(`${dataDir}rail_lines.json`, 'utf8')) as RailLine[]

  it('has a matching geometry file for every manifest key', () => {
    for (const entry of lines) {
      if (!entry.geometryKey) continue
      const path = `${dataDir}rail_geometry/${entry.geometryKey}.json`
      expect(existsSync(path), `${entry.company} ${entry.line}`).toBe(true)
      const geometry = JSON.parse(readFileSync(path, 'utf8')) as RailGeometry
      expect([geometry.line, geometry.company]).toEqual([entry.line, entry.company])
    }
  })

  it('does not draw JR Central or JR West when JR East Tokyo -> Atami is selected', () => {
    const tokyo = { lon: 139.766709, lat: 35.681247 }
    const atami = { lon: 139.077942, lat: 35.10376 }
    const points = routeFor('東海道線', 'JR東日本', tokyo, atami)
    expect(points.length).toBeGreaterThan(20)
    expect(distanceKm(points[0], tokyo)).toBeLessThan(0.6)
    expect(distanceKm(points[points.length - 1], atami)).toBeLessThan(0.6)
    let length = 0
    let maxGap = 0
    for (let i = 1; i < points.length; i++) {
      const gap = distanceKm(points[i - 1], points[i])
      length += gap
      maxGap = Math.max(maxGap, gap)
    }
    expect(length).toBeGreaterThan(90)
    expect(length).toBeLessThan(130)
    // Missing source sections may be bridged, but never by more than LINK_DEG.
    expect(maxGap).toBeLessThan(10)
    expect(points.some((point) => point.lon < 139.0 || point.lon > 140.0)).toBe(false)
    expect(points.some((point) => point.lat < 34.9 || point.lat > 35.8)).toBe(false)
  })

  it('returns a drawable Sobu route once the merged geometry is repaired', () => {
    const tsudanuma = { lon: 140.020276, lat: 35.691324 }
    const akihabara = { lon: 139.773215, lat: 35.69832 }
    const points = routeFor('総武線', 'JR東日本', tsudanuma, akihabara)
    expect(points.length).toBeGreaterThan(2)
    expect(distanceKm(points[0], tsudanuma)).toBeLessThan(0.6)
    expect(distanceKm(points[points.length - 1], akihabara)).toBeLessThan(0.6)
    let maxGap = 0
    for (let i = 1; i < points.length; i++) {
      maxGap = Math.max(maxGap, distanceKm(points[i - 1], points[i]))
    }
    expect(maxGap).toBeLessThan(10)
  })

  it('follows the Tokaido track between Tokyo and Shinagawa instead of taking a far point', () => {
    const points = routeFor(
      '東海道線',
      'JR東日本',
      { lon: 139.766709, lat: 35.681247 },
      { lon: 139.73902, lat: 35.628549 },
    )
    expect(points.length).toBeGreaterThan(10)
    let maxGap = 0
    let length = 0
    for (let i = 1; i < points.length; i++) {
      const gap = distanceKm(points[i - 1], points[i])
      maxGap = Math.max(maxGap, gap)
      length += gap
    }
    expect(maxGap).toBeLessThan(1.2)
    expect(length).toBeGreaterThan(5)
    expect(length).toBeLessThan(10)
  })

  it('follows the Yamanote track between Shinjuku and Shibuya', () => {
    const points = routeFor(
      '山手線',
      'JR東日本',
      { lon: 139.700643, lat: 35.689874 },
      { lon: 139.701724, lat: 35.658082 },
    )
    expect(points.length).toBeGreaterThan(5)
    let maxGap = 0
    for (let i = 1; i < points.length; i++) {
      maxGap = Math.max(maxGap, distanceKm(points[i - 1], points[i]))
    }
    expect(maxGap).toBeLessThan(1.2)
  })

  it('keeps short lines such as Hakone Tozan instead of dropping them', () => {
    const points = routeFor(
      '鉄道線',
      '小田急箱根',
      { lon: 139.10395, lat: 35.23354 },
      { lon: 139.048257, lat: 35.250893 },
    )
    expect(points.length).toBeGreaterThan(10)
    expect(distanceKm(points[0], { lon: 139.10395, lat: 35.23354 })).toBeLessThan(0.6)
    expect(distanceKm(points[points.length - 1], { lon: 139.048257, lat: 35.250893 })).toBeLessThan(0.6)
  })

  it('takes the short 3.9 km Sanyo segment instead of the 250 km chain excursion', () => {    const kurashiki = { lon: 133.765555, lat: 34.601665 }
    const nishiahati = { lon: 133.72792, lat: 34.58579 }
    const points = routeFor('山陽線', 'JR西日本', kurashiki, nishiahati)
    expect(points.length).toBeGreaterThanOrEqual(2)
    expect(distanceKm(points[0], kurashiki)).toBeLessThan(0.6)
    expect(distanceKm(points[points.length - 1], nishiahati)).toBeLessThan(0.6)
    let length = 0
    for (let i = 1; i < points.length; i++) length += distanceKm(points[i - 1], points[i])
    expect(length).toBeLessThan(10)
  })
})
