import {
  currentGpsCoord,
  endCoord,
  startCoord,
  type Coord,
  type OverlayCorner,
  type OverlayStyle,
  type TransportInfo,
} from '@/types'
import { formatLatLon, greatCircle } from '@/lib/geo'
import {
  boundsAround,
  findEnclosingRing,
  isInJapan,
  isInUs,
  ringBounds,
  type Bounds,
  type MapDatasets,
  type Ring,
} from '@/lib/map-data'

export interface BoundsRect {
  x: number
  y: number
  width: number
  height: number
}

export interface OverlayLayout {
  textBounds: BoundsRect
  mapBounds: BoundsRect | null
}

export interface RenderOptions {
  width: number
  height: number
  info: TransportInfo
  style: OverlayStyle
  datasets: MapDatasets
  /** When true the source image is already drawn; map/text use this context. */
  ctx: CanvasRenderingContext2D
  selected?: 'text' | 'map' | null
}

interface RowBase {
  height: number
  width: number
}

interface SimpleRow extends RowBase {
  kind: 'simple'
  text: string
  size: number
  alpha: number
  bold?: boolean
}

interface DualRow extends RowBase {
  kind: 'dual'
  primary: string
  secondary: string
  primarySize: number
  secondarySize: number
  gap: number
}

interface IataRow extends RowBase {
  kind: 'iata'
  dep: string
  arr: string
  size: number
  iconSize: number
  gap: number
}

interface ArrowRow extends RowBase {
  kind: 'arrow'
  left: string | null
  right: string
  size: number
  arrowWidth: number
  gap: number
}

interface AirportRow extends RowBase {
  kind: 'airport'
  name: string
  withPlane: boolean
  size: number
}

interface LocationRow extends RowBase {
  kind: 'location'
  text: string
  size: number
  pinSize: number
}

type Row = SimpleRow | DualRow | IataRow | ArrowRow | AirportRow | LocationRow

const MERCATOR_MAX = 85.05112878

function mercatorY(lat: number): number {
  const clamped = Math.max(-MERCATOR_MAX, Math.min(MERCATOR_MAX, lat))
  return Math.log(Math.tan(Math.PI / 4 + (clamped * Math.PI) / 360))
}

function normalizeLon(lon: number, center: number): number {
  let value = lon
  while (value - center > 180) value -= 360
  while (value - center < -180) value += 360
  return value
}

function focusPoints(info: TransportInfo): Coord[] {
  const start = startCoord(info)
  const end = endCoord(info)
  const cur = currentGpsCoord(info)
  const points: Coord[] = []
  if (info.mode === 'AIRPLANE' && start && end) {
    points.push(start, ...greatCircle(start.lat, start.lon, end.lat, end.lon, 64), end)
    return points
  }
  if (info.mode !== 'SPOT') {
    for (const polyline of info.routePolylines) points.push(...polyline)
    if (start) points.push(start)
    if (end) points.push(end)
    if (!info.routePolylines.length && start && end) {
      points.push({ lon: (start.lon + end.lon) / 2, lat: Math.max(start.lat, end.lat) + 0.18 })
    }
  }
  if (!points.length && cur) points.push(cur)
  return points
}

function anchorPoints(info: TransportInfo): Coord[] {
  return [startCoord(info), endCoord(info), currentGpsCoord(info)].filter(
    (p): p is Coord => p != null,
  )
}

function singlePointBounds(
  point: Coord,
  datasets: MapDatasets,
): Bounds | null {
  if (isInJapan(point.lon, point.lat)) {
    const ring = findEnclosingRing(point.lon, point.lat, datasets.japanPrefectures)
    if (ring) {
      const b = ringBounds(ring)
      if (b) {
        const cx = (b.minLon + b.maxLon) / 2
        const cy = (b.minLat + b.maxLat) / 2
        const halfW = Math.max((b.maxLon - b.minLon) / 2, 0.8)
        const halfH = Math.max((b.maxLat - b.minLat) / 2, 0.42)
        return { minLon: cx - halfW, maxLon: cx + halfW, minLat: cy - halfH, maxLat: cy + halfH }
      }
    }
    return boundsAround(point.lon, point.lat, 3, 2.4)
  }
  if (isInUs(point.lon, point.lat)) {
    const ring = findEnclosingRing(point.lon, point.lat, datasets.usStates)
    if (ring) {
      const b = ringBounds(ring)
      if (b) return b
    }
    return boundsAround(point.lon, point.lat, 8, 6)
  }
  return boundsAround(point.lon, point.lat, 4, 3.2)
}

function mapCornerPosition(
  corner: OverlayCorner,
  width: number,
  height: number,
  boxW: number,
  boxH: number,
  margin: number,
): { left: number; top: number } {
  const left =
    corner === 'TOP_LEFT' || corner === 'BOTTOM_LEFT' ? margin : Math.max(0, width - margin - boxW)
  const top =
    corner === 'TOP_LEFT' || corner === 'TOP_RIGHT' ? margin : Math.max(0, height - margin - boxH)
  return { left, top }
}

function drawPlaneGlyph(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  size: number,
  color: string,
  shadow: string,
  unit: number,
  rotate = 90,
) {
  ctx.save()
  ctx.translate(cx, cy)
  ctx.rotate((rotate * Math.PI) / 180)
  ctx.scale(size / 21, size / 21)
  ctx.beginPath()
  ctx.moveTo(9, 4)
  ctx.lineTo(9, 2)
  ctx.lineTo(1, -3)
  ctx.lineTo(1, -8.5)
  ctx.bezierCurveTo(1, -9.33, 0.33, -10, -0.5, -10)
  ctx.bezierCurveTo(-1.33, -10, -2, -9.33, -2, -8.5)
  ctx.lineTo(-2, -3)
  ctx.lineTo(-10, 2)
  ctx.lineTo(-10, 4)
  ctx.lineTo(-2, 1.5)
  ctx.lineTo(-2, 7)
  ctx.lineTo(-4, 8.5)
  ctx.lineTo(-4, 10)
  ctx.lineTo(-0.5, 9)
  ctx.lineTo(3, 10)
  ctx.lineTo(3, 8.5)
  ctx.lineTo(1, 7)
  ctx.lineTo(1, 1.5)
  ctx.closePath()
  ctx.shadowColor = shadow
  ctx.shadowBlur = 6 * unit
  ctx.shadowOffsetY = unit
  ctx.fillStyle = color
  ctx.fill()
  ctx.restore()
}

function drawPlaneDisc(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  r: number,
  angle: number,
) {
  ctx.save()
  ctx.shadowColor = 'rgba(0,0,0,0.35)'
  ctx.shadowBlur = r * 0.7
  ctx.fillStyle = '#ffffff'
  ctx.beginPath()
  ctx.arc(cx, cy, r, 0, Math.PI * 2)
  ctx.fill()
  ctx.shadowColor = 'transparent'
  drawPlaneGlyph(ctx, cx, cy, r * 1.45, '#263238', 'rgba(0,0,0,0.35)', r / 10, angle + 90)
  ctx.restore()
}

function drawBullseye(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  r: number,
  color: string,
) {
  ctx.save()
  ctx.shadowColor = 'rgba(0,0,0,0.35)'
  ctx.shadowBlur = r * 0.7
  ctx.fillStyle = 'rgba(255,255,255,0.9)'
  ctx.beginPath()
  ctx.arc(x, y, r, 0, Math.PI * 2)
  ctx.fill()
  ctx.shadowColor = 'transparent'
  ctx.lineWidth = Math.max(2, r * 0.32)
  ctx.strokeStyle = color
  ctx.beginPath()
  ctx.arc(x, y, r * 0.62, 0, Math.PI * 2)
  ctx.stroke()
  ctx.fillStyle = color
  ctx.beginPath()
  ctx.arc(x, y, r * 0.2, 0, Math.PI * 2)
  ctx.fill()
  ctx.restore()
}

function drawPin(ctx: CanvasRenderingContext2D, x: number, y: number, r: number) {
  ctx.save()
  ctx.shadowColor = 'rgba(0,0,0,0.35)'
  ctx.shadowBlur = r * 0.6
  ctx.fillStyle = '#e53935'
  const path = new Path2D()
  path.moveTo(x, y)
  path.bezierCurveTo(x - r * 0.9, y - r * 1.1, x - r * 0.95, y - r * 2.2, x, y - r * 2.2)
  path.bezierCurveTo(x + r * 0.95, y - r * 2.2, x + r * 0.9, y - r * 1.1, x, y)
  path.closePath()
  ctx.fill(path)
  ctx.shadowColor = 'transparent'
  ctx.lineWidth = Math.max(1.2, r * 0.18)
  ctx.strokeStyle = '#ffffff'
  ctx.stroke(path)
  ctx.fillStyle = '#ffffff'
  ctx.beginPath()
  ctx.arc(x, y - r * 1.45, r * 0.32, 0, Math.PI * 2)
  ctx.fill()
  ctx.restore()
}

function drawPushpin(ctx: CanvasRenderingContext2D, cx: number, bottomY: number, size: number) {
  ctx.save()
  const needleTop = bottomY - size * 0.55
  ctx.strokeStyle = '#cfd8dc'
  ctx.lineWidth = Math.max(1.2, size * 0.11)
  ctx.lineCap = 'round'
  ctx.beginPath()
  ctx.moveTo(cx, needleTop)
  ctx.lineTo(cx, bottomY)
  ctx.stroke()
  ctx.fillStyle = '#e53935'
  ctx.shadowColor = 'rgba(0,0,0,0.3)'
  ctx.shadowBlur = size * 0.18
  ctx.beginPath()
  ctx.arc(cx, bottomY - size * 0.72, size * 0.28, 0, Math.PI * 2)
  ctx.fill()
  ctx.restore()
}

function drawText(
  ctx: CanvasRenderingContext2D,
  text: string,
  x: number,
  y: number,
  style: OverlayStyle,
  size: number,
  alpha = 1,
  bold = true,
) {
  const lum = luminance(style.textColor)
  ctx.save()
  ctx.font = `${bold ? 700 : 400} ${size}px ${fontFamily(style.font)}`
  ctx.fillStyle = withAlpha(style.textColor, alpha)
  ctx.shadowColor = lum < 0.25 ? 'rgba(255,255,255,0.48)' : 'rgba(0,0,0,0.7)'
  ctx.shadowBlur = size * 0.28
  ctx.shadowOffsetY = size * 0.045
  ctx.textBaseline = 'alphabetic'
  ctx.fillText(text, x, y)
  ctx.restore()
}

function fontFamily(key: string): string {
  switch (key) {
    case 'barlow':
      return '"Barlow", system-ui, sans-serif'
    case 'jetbrains-mono':
      return '"JetBrains Mono", ui-monospace, monospace'
    case 'dm-sans':
      return '"DM Sans", system-ui, sans-serif'
    case 'kaisei-tokumin':
      return '"Kaisei Tokumin", serif'
    case 'artemis':
      return '"Artemis Inter", "Outfit", system-ui, sans-serif'
    case 'default':
      return 'system-ui, sans-serif'
    default:
      return '"Outfit", system-ui, sans-serif'
  }
}

function withAlpha(hex: string, alpha: number): string {
  const clean = hex.replace('#', '')
  if (clean.length !== 6) return hex
  const r = parseInt(clean.slice(0, 2), 16)
  const g = parseInt(clean.slice(2, 4), 16)
  const b = parseInt(clean.slice(4, 6), 16)
  return `rgba(${r},${g},${b},${Math.max(0, Math.min(1, alpha))})`
}

function luminance(hex: string): number {
  const clean = hex.replace('#', '')
  if (clean.length !== 6) return 1
  const r = parseInt(clean.slice(0, 2), 16) / 255
  const g = parseInt(clean.slice(2, 4), 16) / 255
  const b = parseInt(clean.slice(4, 6), 16) / 255
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function buildRows(
  ctx: CanvasRenderingContext2D,
  info: TransportInfo,
  style: OverlayStyle,
  unit: number,
  W: number,
): Row[] {
  const rows: Row[] = []
  const u = unit * style.textScale
  const measure = (text: string, size: number, bold = true) => {
    ctx.save()
    ctx.font = `${bold ? 700 : 400} ${size}px ${fontFamily(style.font)}`
    const width = ctx.measureText(text).width
    ctx.restore()
    return width
  }

  if (info.mode === 'AIRPLANE') {
    const code = `${info.airlineCode.trim()} ${info.flightNumber.trim()}`.trim()
    if (code || info.registrationNumber) {
      if (code && info.registrationNumber) {
        const p = measure(code, 46 * u)
        const s = measure(info.registrationNumber, 39 * u)
        rows.push({
          kind: 'dual',
          primary: code,
          secondary: info.registrationNumber,
          primarySize: 46 * u,
          secondarySize: 39 * u,
          gap: 12 * u,
          width: p + 12 * u + s,
          height: 54 * u,
        })
      } else {
        const text = code || info.registrationNumber
        rows.push({ kind: 'simple', text, size: 46 * u, alpha: 1, width: measure(text, 46 * u), height: 54 * u })
      }
    }
    const dep = info.departureAirportCode.trim().toUpperCase()
    const arr = info.arrivalAirportCode.trim().toUpperCase()
    if (dep && arr) {
      const size = 52 * u
      const iconSize = 42 * u
      const gap = 15 * u
      const w = measure(dep, size) + gap + iconSize + gap + measure(arr, size)
      rows.push({ kind: 'iata', dep, arr, size, iconSize, gap, width: w, height: 62 * u })
    } else if (dep || arr) {
      const only = dep || arr
      const size = 52 * u
      rows.push({ kind: 'simple', text: only, size, alpha: 1, width: measure(only, size), height: 62 * u })
    }
    if (info.departureAirportName.trim()) {
      const text = info.departureAirportName.trim()
      rows.push({ kind: 'airport', name: text, withPlane: false, size: 24 * u, width: measure(text, 24 * u), height: 30 * u })
    }
    if (info.arrivalAirportName.trim()) {
      const text = info.arrivalAirportName.trim()
      rows.push({
        kind: 'airport',
        name: text,
        withPlane: true,
        size: 24 * u,
        width: measure(text, 24 * u) + 28 * u,
        height: 32 * u,
      })
    }
  } else if (info.mode === 'TRAIN') {
    if (info.railwayLineName.trim()) {
      const text = info.railwayLineName.trim()
      rows.push({ kind: 'simple', text, size: 46 * u, alpha: 1, width: measure(text, 46 * u), height: 54 * u })
    }
    const name = info.trainName.trim()
    const model = info.vehicleModel.trim()
    if (name || model) {
      if (name && model) {
        const p = measure(name, 45 * u)
        const s = measure(model, 39 * u)
        rows.push({
          kind: 'dual',
          primary: name,
          secondary: model,
          primarySize: 45 * u,
          secondarySize: 39 * u,
          gap: 12 * u,
          width: p + 12 * u + s,
          height: 52 * u,
        })
      } else {
        const text = name || model
        rows.push({ kind: 'simple', text, size: 45 * u, alpha: 1, width: measure(text, 45 * u), height: 52 * u })
      }
    }
    addArrowRow(rows, info.departureStationName, info.arrivalStationName, u, measure, 46 * u, W)
  } else if (info.mode === 'OTHER') {
    if (info.transportName.trim()) {
      const text = info.transportName.trim()
      rows.push({ kind: 'simple', text, size: 46 * u, alpha: 1, width: measure(text, 46 * u), height: 54 * u })
    }
    addArrowRow(rows, info.departurePlaceName, info.destinationPlaceName, u, measure, 46 * u, W)
  } else {
    for (const raw of [info.spotLine1, info.spotLine2, info.spotLine3]) {
      const text = raw.trim()
      if (!text) continue
      rows.push({ kind: 'simple', text, size: 48 * u, alpha: 1, width: measure(text, 48 * u), height: 56 * u })
    }
  }

  const locationText =
    info.locationDisplayMode === 'NONE'
      ? null
      : info.locationDisplayMode === 'COORDINATES'
        ? info.photoLat != null && info.photoLon != null
          ? formatLatLon(info.photoLat, info.photoLon, 5)
          : null
        : info.photoLocationName.trim() ||
          (info.photoLat != null && info.photoLon != null ? formatLatLon(info.photoLat, info.photoLon, 5) : '')
  if (locationText) {
    const size = 28 * u
    rows.push({
      kind: 'location',
      text: locationText,
      size,
      pinSize: 26 * u,
      width: 26 * u + 8 * u + measure(locationText, size, false),
      height: 38 * u,
    })
  }

  if (info.dateTimeDisplayMode !== 'NONE') {
    const text =
      info.dateTimeDisplayMode === 'DATE_AND_TIME'
        ? `${String(info.year).padStart(4, '0')}/${String(info.month).padStart(2, '0')}/${String(info.day).padStart(2, '0')} ${String(info.hour).padStart(2, '0')}:${String(info.minute).padStart(2, '0')}`
        : `${String(info.year).padStart(4, '0')}/${String(info.month).padStart(2, '0')}/${String(info.day).padStart(2, '0')}`
    rows.push({
      kind: 'simple',
      text,
      size: 25 * u,
      alpha: 0.82,
      width: measure(text, 25 * u),
      height: 34 * u,
    })
  }

  return rows
}

function addArrowRow(
  rows: Row[],
  depRaw: string,
  arrRaw: string,
  u: number,
  measure: (text: string, size: number, bold?: boolean) => number,
  size: number,
  W: number,
) {
  const dep = depRaw.trim()
  const arr = arrRaw.trim()
  if (!dep && !arr) return
  const arrowWidth = 34 * u
  const gap = 12 * u
  if (!dep || !arr) {
    const text = dep || arr
    rows.push({ kind: 'simple', text, size, alpha: 1, width: measure(text, size), height: 54 * u })
    return
  }
  const singleWidth = measure(dep, size) + gap + arrowWidth + gap + measure(arr, size)
  if (singleWidth > W * 0.5) {
    rows.push({ kind: 'simple', text: dep, size, alpha: 1, width: measure(dep, size), height: 52 * u })
    rows.push({
      kind: 'arrow',
      left: null,
      right: arr,
      size,
      arrowWidth,
      gap,
      width: arrowWidth + gap + measure(arr, size),
      height: 54 * u,
    })
    return
  }
  rows.push({
    kind: 'arrow',
    left: dep,
    right: arr,
    size,
    arrowWidth,
    gap,
    width: singleWidth,
    height: 54 * u,
  })
}

function drawRows(
  ctx: CanvasRenderingContext2D,
  rows: Row[],
  left: number,
  top: number,
  alignRight: boolean,
  style: OverlayStyle,
  blockWidth: number,
  unit: number,
) {
  let y = top
  for (const row of rows) {
    const rowLeft = alignRight ? left + blockWidth - row.width : left
    const baseline = y + row.height * 0.78
    if (row.kind === 'simple') {
      drawText(ctx, row.text, rowLeft, baseline, style, row.size, row.alpha)
    } else if (row.kind === 'dual') {
      drawText(ctx, row.primary, rowLeft, baseline, style, row.primarySize)
      const x = rowLeft + measureCtx(ctx, row.primary, style, row.primarySize) + row.gap
      drawText(ctx, row.secondary, x, baseline, style, row.secondarySize, 0.92)
    } else if (row.kind === 'iata') {
      drawText(ctx, row.dep, rowLeft, baseline, style, row.size)
      const depW = measureCtx(ctx, row.dep, style, row.size)
      const iconCx = rowLeft + depW + row.gap + row.iconSize * 0.5
      drawPlaneGlyph(ctx, iconCx, baseline - row.iconSize * 0.4, row.iconSize, style.textColor, 'rgba(0,0,0,0.5)', unit, 90)
      const arrX = rowLeft + depW + row.gap * 2 + row.iconSize
      drawText(ctx, row.arr, arrX, baseline, style, row.size)
    } else if (row.kind === 'arrow') {
      let x = rowLeft
      if (row.left) {
        drawText(ctx, row.left, x, baseline, style, row.size)
        x += measureCtx(ctx, row.left, style, row.size) + row.gap
      }
      const cy = baseline - row.size * 0.35
      const rightX = x + row.arrowWidth
      const head = row.arrowWidth * 0.34
      ctx.save()
      ctx.strokeStyle = style.textColor
      ctx.lineWidth = Math.max(2, 4.4 * unit)
      ctx.lineCap = 'round'
      ctx.lineJoin = 'round'
      ctx.shadowColor = luminance(style.textColor) < 0.25 ? 'rgba(255,255,255,0.5)' : 'rgba(0,0,0,0.6)'
      ctx.shadowBlur = 6 * unit
      ctx.beginPath()
      ctx.moveTo(x, cy)
      ctx.lineTo(rightX - row.arrowWidth * 0.08, cy)
      ctx.moveTo(rightX - head, cy - head * 0.78)
      ctx.lineTo(rightX, cy)
      ctx.lineTo(rightX - head, cy + head * 0.78)
      ctx.stroke()
      ctx.restore()
      x += row.arrowWidth + row.gap
      drawText(ctx, row.right, x, baseline, style, row.size)
    } else if (row.kind === 'airport') {
      if (row.withPlane) {
        const small = 19 * unit
        drawPlaneGlyph(ctx, rowLeft + small * 0.5, baseline - small * 0.42, small, style.textColor, 'rgba(0,0,0,0.45)', unit * 0.6, 90)
        drawText(ctx, row.name, rowLeft + 28 * unit, baseline, style, row.size, 0.88, false)
      } else {
        drawText(ctx, row.name, rowLeft, baseline, style, row.size, 0.88, false)
      }
    } else if (row.kind === 'location') {
      drawPushpin(ctx, rowLeft + row.pinSize * 0.45, baseline + 2 * unit, row.pinSize)
      drawText(ctx, row.text, rowLeft + row.pinSize + 8 * unit, baseline, style, row.size, 1)
    }
    y += row.height
  }
}

function measureCtx(ctx: CanvasRenderingContext2D, text: string, style: OverlayStyle, size: number): number {
  ctx.save()
  ctx.font = `700 ${size}px ${fontFamily(style.font)}`
  const w = ctx.measureText(text).width
  ctx.restore()
  return w
}

interface MapRenderResult {
  rect: BoundsRect
}

function renderMap(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
  info: TransportInfo,
  style: OverlayStyle,
  datasets: MapDatasets,
): MapRenderResult | null {
  const points = focusPoints(info)
  if (!points.length) return null
  const start = info.mode === 'SPOT' ? null : startCoord(info)
  const end = info.mode === 'SPOT' ? null : endCoord(info)
  const rawCur = currentGpsCoord(info)

  const anchors = anchorPoints(info)
  const selectionPoints = anchors.length ? anchors : points
  const allJapan = selectionPoints.every(
    (p) => findEnclosingRing(p.lon, p.lat, datasets.japanPrefectures) != null,
  )
  const allUs = selectionPoints.every(
    (p) => findEnclosingRing(p.lon, p.lat, datasets.usStates) != null,
  )
  const primary = allJapan ? datasets.japanCoast : allUs ? datasets.usStates : datasets.world
  const borders = allJapan ? datasets.japanPrefectures : []
  const water = allJapan ? datasets.japanWater : []
  // Japan's coast + prefectures are drawn on their own; drawing the coarse world
  // underneath ghosts neighboring coastlines at small scales.
  const context = allJapan ? [] : []

  const centerRef = points[0].lon
  const normalized = points.map((p) => normalizeLon(p.lon, centerRef))
  let minLon = Math.min(...normalized)
  let maxLon = Math.max(...normalized)
  let minLat = Math.min(...points.map((p) => p.lat))
  let maxLat = Math.max(...points.map((p) => p.lat))

  if (points.length === 1) {
    const b = singlePointBounds(points[0], datasets)
    if (b) {
      minLon = normalizeLon(b.minLon, centerRef)
      maxLon = normalizeLon(b.maxLon, centerRef)
      minLat = b.minLat
      maxLat = b.maxLat
    }
  }

  if (maxLon - minLon < 0.01) {
    const mid = (minLon + maxLon) / 2
    minLon = mid - 0.5
    maxLon = mid + 0.5
  }
  if (maxLat - minLat < 0.01) {
    const mid = (minLat + maxLat) / 2
    minLat = mid - 0.4
    maxLat = mid + 0.4
  }

  const padX = (maxLon - minLon) * 0.24
  const padTop = (maxLat - minLat) * (info.mode === 'AIRPLANE' ? 0.3 : 0.24)
  const padBottom = (maxLat - minLat) * 0.24
  minLon -= padX
  maxLon += padX
  minLat -= padBottom
  maxLat += padTop

  const centerLon = (minLon + maxLon) / 2
  const toRad = Math.PI / 180
  const minX = minLon * toRad
  const maxX = maxLon * toRad
  const minY = mercatorY(minLat)
  const maxY = mercatorY(maxLat)
  const centerXProj = (minX + maxX) / 2
  const centerY = (minY + maxY) / 2
  const spanX = Math.max(0.008, maxX - minX)
  const spanY = Math.max(0.008, maxY - minY)

  const landscape = width > height
  const minBound = 0.03 * width * 0.4
  const scale = style.mapBoxScale
  const maxW = Math.min(
    ((landscape ? 0.33 : 0.45) * width * (landscape ? 0.85 : 1)) * scale,
    width - minBound * 2,
  )
  const maxH = Math.min(
    ((landscape ? 0.45 : 0.33) * height * (landscape ? 0.85 : 1)) * scale,
    height - minBound * 2,
  )
  const aspect = Math.max(spanX / spanY, 0.85)
  let boxW = maxW
  let boxH = boxW / aspect
  if (boxH > maxH) {
    boxH = maxH
    boxW = boxH * aspect
  }
  if (points.length === 1) {
    boxH = Math.max(boxH, 0.45 * maxH)
    boxW = Math.min(Math.max(boxW, boxH * 0.85), maxW)
  }

  const mapMargin = 0.03 * width * (landscape ? 0.85 : 1)
  const pos =
    style.mapFreePos != null
      ? {
          left: Math.min(
            Math.max(style.mapFreePos.x * width - boxW / 2, minBound),
            Math.max(minBound, width - boxW - minBound),
          ),
          top: Math.min(
            Math.max(style.mapFreePos.y * height - boxH / 2, minBound),
            Math.max(minBound, height - boxH - minBound),
          ),
        }
      : mapCornerPosition(style.mapCorner, width, height, boxW, boxH, mapMargin)
  const rect: BoundsRect = { x: pos.left, y: pos.top, width: boxW, height: boxH }

  const zoom = Math.max(0.5, Math.min(5, style.mapInternalZoom))
  const fitScale = Math.min(boxW / spanX, boxH / spanY) * zoom
  const centerX = centerXProj - (style.mapInternalPanX * spanX) / zoom
  const screenCx = rect.x + rect.width / 2
  const screenCy = rect.y + rect.height / 2
  const projectX = (lon: number) =>
    screenCx + (normalizeLon(lon, centerLon) * toRad - centerX) * fitScale
  const projectY = (lat: number) =>
    screenCy -
    (mercatorY(lat) - centerY + (style.mapInternalPanY * spanY) / zoom) * fitScale

  const borderColor = style.borderColor
  const routeColor = style.routeColor
  const routeStroke = Math.max(2.2, 0.01 * boxW)
  const markerR = Math.max(6, 0.04 * boxW)

  ctx.save()
  ctx.beginPath()
  roundRectPath(ctx, rect.x, rect.y, rect.width, rect.height, Math.min(18, boxW * 0.06))
  ctx.clip()

  const viewCenterLon = centerLon - (style.mapInternalPanX * spanX / zoom) / toRad
  const cullHalf = ((spanX / zoom) / toRad) * 1.25 + 6
  const viewMinLon = viewCenterLon - cullHalf
  const viewMaxLon = viewCenterLon + cullHalf

  const drawRings = (
    rings: Ring[],
    lineWidth: number,
    color: string,
    halo: boolean,
    alpha = 1,
  ) => {
    ctx.lineJoin = 'round'
    ctx.lineCap = 'round'
    const path = new Path2D()
    for (const ring of rings) {
      const b = ringBounds(ring)
      if (!b) continue
      const ringMin = normalizeLon(b.minLon, viewCenterLon)
      const ringMax = normalizeLon(b.maxLon, viewCenterLon)
      if (ringMax < viewMinLon || ringMin > viewMaxLon) continue
      let started = false
      let prevX = 0
      for (let i = 0; i < ring.length; i++) {
        const [lon, lat] = ring[i]
        const x = projectX(lon)
        const y = projectY(lat)
        if (!started) {
          path.moveTo(x, y)
          started = true
        } else if (Math.abs(x - prevX) > rect.width * 0.5) {
          path.moveTo(x, y)
        } else {
          path.lineTo(x, y)
        }
        prevX = x
      }
    }
    if (halo) {
      ctx.strokeStyle = luminance(color) < 0.5 ? 'rgba(255,255,255,0.55)' : 'rgba(0,0,0,0.55)'
      ctx.lineWidth = lineWidth * 1.65
      ctx.globalAlpha = alpha
      ctx.stroke(path)
    }
    ctx.strokeStyle = color
    ctx.lineWidth = lineWidth
    ctx.globalAlpha = alpha
    ctx.stroke(path)
    ctx.globalAlpha = 1
  }

  if (context.length) {
    drawRings(context, Math.max(0.8, borderStroke(boxW)), withAlpha(borderColor, 0.5), true, 0.7)
  }
  if (allJapan) {
    const visibleSpanLon = spanX / zoom
    if (visibleSpanLon >= 2.5) {
      drawRings(borders, Math.max(0.7, 0.0015 * boxW), withAlpha(borderColor, 0.38), false)
      drawRings(primary, Math.max(1.2, 0.005 * boxW), borderColor, true)
    } else {
      drawRings(borders, Math.max(1.1, 0.004 * boxW), borderColor, true)
    }
    if (water.length) {
      const path = new Path2D()
      for (const lake of water) {
        const b = ringBounds(lake)
        if (!b) continue
        const ringMin = normalizeLon(b.minLon, viewCenterLon)
        const ringMax = normalizeLon(b.maxLon, viewCenterLon)
        if (ringMax < viewMinLon || ringMin > viewMaxLon) continue
        for (let i = 0; i < lake.length; i++) {
          const [lon, lat] = lake[i]
          const x = projectX(lon)
          const y = projectY(lat)
          if (i === 0) path.moveTo(x, y)
          else path.lineTo(x, y)
        }
        path.closePath()
      }
      ctx.strokeStyle = withAlpha(borderColor, 0.75)
      ctx.lineWidth = Math.max(0.9, 0.004 * boxW)
      ctx.stroke(path)
    }
  } else {
    drawRings(primary, Math.max(1.1, 0.005 * boxW), borderColor, true)
  }

  // Route
  const hasGround = info.mode !== 'AIRPLANE' && info.mode !== 'SPOT' && info.routePolylines.length > 0
  if (hasGround) {
    ctx.strokeStyle = withAlpha(routeColor, 0.5)
    ctx.lineWidth = routeStroke * 1.65
    ctx.lineJoin = 'round'
    ctx.lineCap = 'round'
    for (const poly of info.routePolylines) {
      if (poly.length < 2) continue
      ctx.beginPath()
      let prevX = 0
      poly.forEach((p, i) => {
        const x = projectX(p.lon)
        const y = projectY(p.lat)
        if (i === 0 || Math.abs(x - prevX) > rect.width * 0.5) ctx.moveTo(x, y)
        else ctx.lineTo(x, y)
        prevX = x
      })
      ctx.stroke()
    }
    ctx.strokeStyle = routeColor
    ctx.lineWidth = routeStroke
    for (const poly of info.routePolylines) {
      if (poly.length < 2) continue
      ctx.beginPath()
      let prevX = 0
      poly.forEach((p, i) => {
        const x = projectX(p.lon)
        const y = projectY(p.lat)
        if (i === 0 || Math.abs(x - prevX) > rect.width * 0.5) ctx.moveTo(x, y)
        else ctx.lineTo(x, y)
        prevX = x
      })
      ctx.stroke()
    }
  } else if (start && end) {
    const arc =
      info.mode === 'AIRPLANE'
        ? greatCircle(start.lat, start.lon, end.lat, end.lon, 96)
        : [
            start,
            { lon: (start.lon + end.lon) / 2, lat: Math.max(start.lat, end.lat) + 0.18 },
            end,
          ]
    ctx.beginPath()
    let arcPrevX = 0
    arc.forEach((p, i) => {
      const x = projectX(p.lon)
      const y = projectY(p.lat)
      if (i === 0 || Math.abs(x - arcPrevX) > rect.width * 0.5) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
      arcPrevX = x
    })
    ctx.setLineDash([routeStroke * 0.85, routeStroke * 2.15])
    ctx.strokeStyle = withAlpha(routeColor, 0.45)
    ctx.lineWidth = routeStroke * 1.6
    ctx.stroke()
    ctx.strokeStyle = routeColor
    ctx.lineWidth = routeStroke
    ctx.stroke()
    ctx.setLineDash([])
  }

  // Markers
  if (info.mode === 'SPOT' || (!start && !end)) {
    const p = rawCur ?? points[0]
    drawBullseye(ctx, projectX(p.lon), projectY(p.lat), markerR, '#4fc3f7')
  } else if (start && end) {
    const sx = projectX(start.lon)
    const sy = projectY(start.lat)
    const ex = projectX(end.lon)
    const ey = projectY(end.lat)
    drawBullseye(ctx, ex, ey, markerR, '#ff7043')
    if (info.mode === 'AIRPLANE') {
      if (style.showOriginPlaneBadge) {
        const angle = (Math.atan2(ey - sy, ex - sx) * 180) / Math.PI
        drawPlaneDisc(ctx, sx, sy, markerR * 1.25, angle)
      } else {
        drawBullseye(ctx, sx, sy, markerR, '#4fc3f7')
      }
    } else {
      drawBullseye(ctx, sx, sy, markerR, '#4fc3f7')
      if (rawCur) drawPin(ctx, projectX(rawCur.lon), projectY(rawCur.lat), markerR * 1.2)
    }
  }

  ctx.restore()
  return { rect }
}

function borderStroke(boxW: number): number {
  return Math.max(1.2, 0.005 * boxW)
}

function roundRectPath(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
) {
  const radius = Math.min(r, w / 2, h / 2)
  ctx.moveTo(x + radius, y)
  ctx.arcTo(x + w, y, x + w, y + h, radius)
  ctx.arcTo(x + w, y + h, x, y + h, radius)
  ctx.arcTo(x, y + h, x, y, radius)
  ctx.arcTo(x, y, x + w, y, radius)
  ctx.closePath()
}

export function renderOverlay(options: RenderOptions): OverlayLayout {
  const { ctx, width, height, info, style, datasets } = options
  const landscape = width > height
  const landscapeScale = landscape ? 0.85 : 1
  const unit = (width / 1000) * landscapeScale
  const textMargin = 0.05 * width * landscapeScale

  ctx.save()
  ctx.clearRect(0, 0, width, height)
  const map = style.showMap ? renderMap(ctx, width, height, info, style, datasets) : null

  const rows = buildRows(ctx, info, style, unit, width)
  if (!rows.length) {
    ctx.restore()
    return { textBounds: { x: textMargin, y: textMargin, width: 100, height: 50 }, mapBounds: map?.rect ?? null }
  }
  const blockW = Math.max(...rows.map((r) => r.width))
  const blockH = rows.reduce((sum, r) => sum + r.height, 0)
  const alignRight = style.textCorner === 'TOP_RIGHT' || style.textCorner === 'BOTTOM_RIGHT'
  const minBound = textMargin * 0.3
  let left: number
  let top: number
  if (style.textFreePos) {
    left = Math.min(
      Math.max(style.textFreePos.x * width - blockW / 2, minBound),
      Math.max(minBound, width - blockW - minBound),
    )
    top = Math.min(
      Math.max(style.textFreePos.y * height - blockH / 2, minBound),
      Math.max(minBound, height - blockH - minBound),
    )
  } else {
    left = alignRight ? Math.max(minBound, width - textMargin - blockW) : textMargin
    top =
      style.textCorner === 'TOP_LEFT' || style.textCorner === 'TOP_RIGHT'
        ? textMargin
        : Math.max(minBound, height - textMargin - blockH)
  }

  drawRows(ctx, rows, left, top, alignRight, style, blockW, unit)
  ctx.restore()
  return {
    textBounds: { x: left, y: top, width: blockW, height: blockH },
    mapBounds: map?.rect ?? null,
  }
}

export function hitTest(layout: OverlayLayout, x: number, y: number): 'text' | 'map' | null {
  const pad = 8
  const t = layout.textBounds
  if (x >= t.x - pad && x <= t.x + t.width + pad && y >= t.y - pad && y <= t.y + t.height + pad) {
    return 'text'
  }
  const m = layout.mapBounds
  if (m && x >= m.x && x <= m.x + m.width && y >= m.y && y <= m.y + m.height) return 'map'
  return null
}

export { drawPlaneGlyph, drawBullseye, drawPin }
