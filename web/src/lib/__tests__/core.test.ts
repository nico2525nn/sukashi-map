import { describe, expect, it } from 'vitest'
import { buildFlightCode, defaultCropFrame, defaultTransportInfo, summaryLabel } from '@/types'
import { computeCropRect, effectiveAspect } from '@/lib/export'
import { hitTest } from '@/lib/renderer'

describe('effectiveAspect', () => {
  it('keeps original media aspect', () => {
    expect(effectiveAspect(defaultCropFrame, 1600, 1200)).toBeCloseTo(4 / 3)
  })
  it('supports fixed portrait and landscape ratios', () => {
    expect(effectiveAspect({ ...defaultCropFrame, aspectPreset: 'RATIO_4_5' }, 1600, 1200)).toBeCloseTo(0.8)
    expect(
      effectiveAspect(
        { ...defaultCropFrame, aspectPreset: 'RATIO_4_5', isLandscapeOrientation: true },
        1600,
        1200,
      ),
    ).toBeCloseTo(1.25)
    expect(effectiveAspect({ ...defaultCropFrame, aspectPreset: 'RATIO_1_1' }, 1600, 1200)).toBe(1)
  })
  it('clamps free ratios', () => {
    expect(effectiveAspect({ ...defaultCropFrame, aspectPreset: 'FREE', freeAspectRatio: 4 }, 1600, 1200)).toBe(2.5)
    expect(effectiveAspect({ ...defaultCropFrame, aspectPreset: 'FREE', freeAspectRatio: 0.1 }, 1600, 1200)).toBe(0.4)
  })
})

describe('computeCropRect', () => {
  it('centers and respects zoom', () => {
    const r = computeCropRect(1600, 1200, { ...defaultCropFrame, cropZoom: 2 })
    expect(r.width).toBeCloseTo(800)
    expect(r.height).toBeCloseTo(600)
    expect(r.x).toBeCloseTo(400)
    expect(r.y).toBeCloseTo(300)
  })
  it('clamps the center to the source bounds', () => {
    const r = computeCropRect(1600, 1200, { ...defaultCropFrame, cropCenterX: 2, cropCenterY: -1 })
    expect(r.x).toBe(0)
    expect(r.y).toBe(0)
  })
})

describe('labels', () => {
  it('builds flight codes without duplicating the carrier', () => {
    expect(buildFlightCode({ ...defaultTransportInfo, airlineCode: 'NH', flightNumber: '1731' })).toBe('NH 1731')
    expect(buildFlightCode({ ...defaultTransportInfo, airlineCode: 'NH', flightNumber: 'NH1731' })).toBe('NH 1731')
  })
  it('summarizes every mode', () => {
    expect(summaryLabel({ ...defaultTransportInfo, mode: 'AIRPLANE' })).toContain('KIX')
    expect(summaryLabel({ ...defaultTransportInfo, mode: 'TRAIN' })).toContain('北陸新幹線')
    expect(summaryLabel({ ...defaultTransportInfo, mode: 'OTHER' })).toContain('湘南ドライブ')
    expect(summaryLabel({ ...defaultTransportInfo, mode: 'SPOT' })).toBe('SHIBUYA SKY')
  })
})

describe('hitTest', () => {
  const layout = {
    textBounds: { x: 100, y: 100, width: 200, height: 80 },
    mapBounds: { x: 10, y: 10, width: 50, height: 50 },
  }
  it('detects text and map', () => {
    expect(hitTest(layout, 150, 120)).toBe('text')
    expect(hitTest(layout, 20, 20)).toBe('map')
    expect(hitTest(layout, 500, 500)).toBeNull()
  })
})
