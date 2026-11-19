import { describe, expect, it } from 'vitest'
import {
  buildFlightCode,
  defaultCropFrame,
  defaultOverlayStyle,
  defaultTransportInfo,
  normalizeOverlayStyle,
  summaryLabel,
} from '@/types'
import { computeCropRect, effectiveAspect } from '@/lib/export'
import { buildRows, hitTest, overlayUnits } from '@/lib/renderer'

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

describe('overlayUnits', () => {
  it('compounds the global scale with each element percentage', () => {
    const units = overlayUnits(
      {
        ...defaultOverlayStyle,
        textScale: 2,
        titleScale: 0.5,
        subtitleScale: 1.25,
        locationScale: 1.5,
        dateScale: 0.75,
      },
      10,
    )
    expect(units.title).toBe(10)
    expect(units.subtitle).toBe(25)
    expect(units.location).toBe(30)
    expect(units.date).toBe(15)
  })
  it('defaults to 100% and clamps persisted values to 50–250%', () => {
    expect(overlayUnits(defaultOverlayStyle, 10).title).toBe(10)
    expect(overlayUnits({ ...defaultOverlayStyle, titleScale: 0.1 }, 10).title).toBe(5)
    expect(overlayUnits({ ...defaultOverlayStyle, titleScale: 9 }, 10).title).toBe(25)
    expect(overlayUnits({ ...defaultOverlayStyle, titleScale: Number.NaN }, 10).title).toBe(10)
    expect(overlayUnits({ ...defaultOverlayStyle, textScale: 9 }, 10).title).toBe(25)
    expect(overlayUnits({ ...defaultOverlayStyle, textScale: Number.POSITIVE_INFINITY }, 10).title).toBe(10)
    const elementKeys = [
      ['subtitleScale', 'subtitle'],
      ['locationScale', 'location'],
      ['dateScale', 'date'],
    ] as const
    for (const [key, unit] of elementKeys) {
      expect(overlayUnits({ ...defaultOverlayStyle, [key]: 0.1 }, 10)[unit]).toBe(5)
      expect(overlayUnits({ ...defaultOverlayStyle, [key]: 9 }, 10)[unit]).toBe(25)
      expect(overlayUnits({ ...defaultOverlayStyle, [key]: Number.NaN }, 10)[unit]).toBe(10)
    }
  })
})

describe('normalizeOverlayStyle', () => {
  it('fills all per-element scales for legacy persisted styles', () => {
    const legacy = {
      font: 'barlow',
      textScale: 1.4,
      mapBoxScale: 0.8,
    }
    const style = normalizeOverlayStyle(legacy)
    expect(style.font).toBe('barlow')
    expect(style.textScale).toBe(1.4)
    expect(style.mapBoxScale).toBe(0.8)
    expect(style.titleScale).toBe(1)
    expect(style.subtitleScale).toBe(1)
    expect(style.locationScale).toBe(1)
    expect(style.dateScale).toBe(1)
    expect(normalizeOverlayStyle(null)).toEqual(defaultOverlayStyle)
  })
})

describe('buildRows', () => {
  const ctx = {
    save() {},
    restore() {},
    font: '',
    measureText(text: string) {
      return { width: text.length * 10 }
    },
  } as unknown as CanvasRenderingContext2D

  it('keeps a dual flight row tall enough for an enlarged subtitle', () => {
    const rows = buildRows(
      ctx,
      {
        ...defaultTransportInfo,
        airlineCode: 'NH',
        flightNumber: '1731',
        registrationNumber: 'JA123A',
      },
      { ...defaultOverlayStyle, titleScale: 0.5, subtitleScale: 2.5 },
      1,
      1000,
    )
    const dual = rows[0]
    expect(dual.kind).toBe('dual')
    expect(dual.height).toBeGreaterThanOrEqual(54 * 2.5)
    expect(dual.width).toBeGreaterThan(0)
  })

  it('applies location and date percentages to their own rows', () => {
    const rows = buildRows(
      ctx,
      {
        ...defaultTransportInfo,
        mode: 'SPOT',
        spotLine1: 'Spot',
        locationDisplayMode: 'NAME',
        photoLocationName: 'Osaka',
        dateTimeDisplayMode: 'DATE_ONLY',
      },
      { ...defaultOverlayStyle, locationScale: 1.5, dateScale: 0.5 },
      1,
      1000,
    )
    const location = rows.find((row) => row.kind === 'location')
    const date = rows.find((row) => row.kind === 'simple' && row.text.includes('/'))
    expect(location?.kind === 'location' ? location.size : 0).toBe(42)
    expect(date?.kind === 'simple' ? date.size : 0).toBe(12.5)
  })

  it('preserves 48px spot rows at 100% and groups the first visible line as title', () => {
    const rows = buildRows(
      ctx,
      {
        ...defaultTransportInfo,
        mode: 'SPOT',
        spotLine1: '',
        spotLine2: 'Second',
        spotLine3: 'Third',
        locationDisplayMode: 'NONE',
        dateTimeDisplayMode: 'NONE',
      },
      { ...defaultOverlayStyle, titleScale: 2, subtitleScale: 0.5 },
      1,
      1000,
    )
    expect(rows).toHaveLength(2)
    expect(rows[0].height).toBe(56 * 2)
    expect(rows[1].height).toBe(56 * 0.5)
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
