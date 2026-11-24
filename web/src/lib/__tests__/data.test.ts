import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchDrivingRoute, searchStations } from '@/lib/data'

const dep = { lat: 35.681, lon: 139.767 }
const dest = { lat: 34.694, lon: 135.502 }

function mockRouteFetch() {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({
      routes: [{ geometry: { coordinates: [[139.767, 35.681], [135.502, 34.694]] } }],
    }),
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('searchStations company filter', () => {
  it('never mixes same-name lines from different operators', async () => {
    const stations = [
      { name: '三島', line: '東海道線', company: 'JR東海', code: '1', lat: 35.126, lon: 138.911 },
      { name: '三島', line: '東海道線', company: 'JR東日本', code: '2', lat: 35.126, lon: 138.911 },
    ]
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => stations }),
    )
    expect(await searchStations('三島', '東海道線', 'JR東日本')).toHaveLength(1)
    expect((await searchStations('三島', '東海道線', 'JR東海'))[0].company).toBe('JR東海')
    expect(await searchStations('三島', '東海道線', 'JR西日本')).toEqual([])
  })
}) 

describe('fetchDrivingRoute photo waypoint', () => {
  it('includes a nearby photo location on the route', async () => {
    const fetchMock = mockRouteFetch()
    const via = { lat: 35.45, lon: 138.2 }
    await fetchDrivingRoute(dep, dest, via)
    const url = String(fetchMock.mock.calls[0][0])
    expect(decodeURIComponent(url)).toContain(`${via.lon},${via.lat}`)
  })

  it('drops a photo location that would create a large detour', async () => {
    const fetchMock = mockRouteFetch()
    const via = { lat: 31.23, lon: 121.47 } // Shanghai
    await fetchDrivingRoute(dep, dest, via)
    const url = String(fetchMock.mock.calls[0][0])
    expect(decodeURIComponent(url)).not.toContain(`${via.lon},${via.lat}`)
  })

  it('matches Android: via plus remaining legs must stay within 145% of the direct chord', async () => {
    const fetchMock = mockRouteFetch()
    const chord = Math.hypot(dest.lat - dep.lat, dest.lon - dep.lon)
    const near = { lat: dep.lat - 0.01, lon: dep.lon - 0.01 }
    const far = { lat: dep.lat - chord * 0.8, lon: dep.lon - chord * 0.8 }
    await fetchDrivingRoute(dep, dest, near)
    expect(decodeURIComponent(String(fetchMock.mock.calls[0][0]))).toContain(`${near.lon},${near.lat}`)
    fetchMock.mockClear()
    await fetchDrivingRoute(dep, dest, far)
    expect(decodeURIComponent(String(fetchMock.mock.calls[0][0]))).not.toContain(`${far.lon},${far.lat}`)
  })
})
