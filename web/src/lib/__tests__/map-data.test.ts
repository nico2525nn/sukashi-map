import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const requested: string[] = []

beforeEach(() => {
  vi.resetModules()
  requested.length = 0
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      requested.push(String(url))
      if (String(url).includes('japan_waterbodies')) {
        return Promise.reject(new Error('water unavailable'))
      }
      return Promise.resolve({ ok: true, json: async () => [] })
    }),
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('ensureMapDatasetsFor', () => {
  it('does not fetch Japanese layers for a European point', async () => {
    const { ensureMapDatasetsFor } = await import('@/lib/map-data')
    await ensureMapDatasetsFor([{ lon: 2.35, lat: 48.85 }])
    expect(requested.some((url) => url.includes('world_map'))).toBe(true)
    expect(requested.some((url) => url.includes('japan_'))).toBe(false)
    expect(requested.some((url) => url.includes('us_states'))).toBe(false)
  })

  it('loads Japan for Tokyo and tolerates an optional lake failure', async () => {
    const { ensureMapDatasetsFor } = await import('@/lib/map-data')
    const datasets = await ensureMapDatasetsFor([{ lon: 139.767, lat: 35.681 }])
    expect(requested.some((url) => url.includes('japan_coastline'))).toBe(true)
    expect(requested.some((url) => url.includes('japan_prefectures'))).toBe(true)
    expect(datasets.japanWater).toEqual([])
  })
})
