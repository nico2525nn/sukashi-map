import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Pause, Play, Volume2, VolumeX } from 'lucide-react'
import type { MediaAsset, OverlayStyle } from '@/types'
import type { MapDatasets } from '@/lib/map-data'
import { computeCropRect } from '@/lib/export'
import { createMapLayerCache, hitTest, renderOverlay, type OverlayLayout } from '@/lib/renderer'
import { useAppStore } from '@/store/app-store'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface PreviewStageProps {
  className?: string
  media: MediaAsset
  source: HTMLImageElement | HTMLVideoElement | null
  aspect: number
  datasets: MapDatasets | null
  renderKey?: number
}

type DragMode = 'text-move' | 'text-resize' | 'map-move' | 'map-resize' | 'map-pinch' | null

function drawBackground(
  canvas: HTMLCanvasElement,
  source: CanvasImageSource,
  sourceWidth: number,
  sourceHeight: number,
  crop: ReturnType<typeof useAppStore.getState>['crop'],
) {
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const rect = computeCropRect(sourceWidth, sourceHeight, crop)
  const w = canvas.width
  const h = canvas.height
  ctx.clearRect(0, 0, w, h)
  const margin = Math.min(0.49, Math.max(0, crop.frameMarginRatio))
  if (margin > 0.001) {
    ctx.fillStyle = crop.frameColor
    ctx.fillRect(0, 0, w, h)
    const innerW = w * (1 - margin)
    const innerH = h * (1 - margin)
    ctx.drawImage(
      source,
      rect.x,
      rect.y,
      rect.width,
      rect.height,
      (w - innerW) / 2,
      (h - innerH) / 2,
      innerW,
      innerH,
    )
  } else {
    ctx.drawImage(source, rect.x, rect.y, rect.width, rect.height, 0, 0, w, h)
  }
}

function drawSelection(
  ctx: CanvasRenderingContext2D,
  layout: OverlayLayout,
  target: 'text' | 'map',
  dpr: number,
) {
  const b = target === 'text' ? layout.textBounds : layout.mapBounds
  if (!b) return
  const pad = 6 * dpr
  ctx.save()
  ctx.strokeStyle = 'rgba(255,255,255,0.9)'
  ctx.lineWidth = 2 * dpr
  ctx.setLineDash([8 * dpr, 6 * dpr])
  ctx.beginPath()
  ctx.roundRect(b.x - pad, b.y - pad, b.width + pad * 2, b.height + pad * 2, 10 * dpr)
  ctx.stroke()
  ctx.setLineDash([])
  ctx.fillStyle = '#6b7cff'
  ctx.beginPath()
  ctx.arc(b.x + b.width, b.y, 8 * dpr, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = '#ffffff'
  ctx.beginPath()
  ctx.arc(b.x + b.width, b.y, 3.5 * dpr, 0, Math.PI * 2)
  ctx.fill()
  ctx.restore()
}

export function PreviewStage({
  media,
  source,
  aspect,
  datasets,
  renderKey = 0,
  className,
}: PreviewStageProps) {
  const info = useAppStore((s) => s.info)
  const style = useAppStore((s) => s.style)
  const crop = useAppStore((s) => s.crop)
  const updateStyle = useAppStore((s) => s.updateStyle)
  const stageSlotRef = useRef<HTMLDivElement>(null)
  const bgRef = useRef<HTMLCanvasElement>(null)
  const overlayRef = useRef<HTMLCanvasElement>(null)
  const layoutRef = useRef<OverlayLayout | null>(null)
  const mapLayerRef = useRef(createMapLayerCache())
  const [slot, setSlot] = useState({ w: 1, h: 1 })
  const [active, setActive] = useState<'text' | 'map' | null>(null)
  const [playing, setPlaying] = useState(true)
  const [muted, setMuted] = useState(true)
  const [dragStyle, setDragStyle] = useState<OverlayStyle | null>(null)
  const [mapIdleTick, setMapIdleTick] = useState(0)
  const dragRef = useRef<{
    mode: DragMode
    pointerId: number
    startX: number
    startY: number
    initStyle: OverlayStyle
    initCenter: { x: number; y: number }
    initDist: number
    pointers: Map<number, { x: number; y: number }>
    pinchDist: number
    pinchCenter: { x: number; y: number }
    pinchZoom: number
  } | null>(null)
  const styleRef = useRef(style)
  styleRef.current = style
  const effectiveStyle = dragStyle ?? style
  const dpr = Math.min(2, window.devicePixelRatio || 1)
  const lastMoveRef = useRef(0)
  useEffect(() => {
    setPlaying(true)
    setMuted(true)
  }, [media.url])

  const activeSource =
    media.type === 'image'
      ? source instanceof HTMLImageElement
        ? source
        : null
      : source instanceof HTMLVideoElement
        ? source
        : null

  useEffect(() => {
    const video = activeSource
    if (!(video instanceof HTMLVideoElement)) return
    const onPlay = () => setPlaying(true)
    const onPause = () => setPlaying(false)
    const onEnded = () => setPlaying(false)
    video.addEventListener('play', onPlay)
    video.addEventListener('pause', onPause)
    video.addEventListener('ended', onEnded)
    return () => {
      video.removeEventListener('play', onPlay)
      video.removeEventListener('pause', onPause)
      video.removeEventListener('ended', onEnded)
    }
  }, [activeSource])


  useLayoutEffect(() => {
    const el = stageSlotRef.current
    if (!el) return
    const update = () => {
      const r = el.getBoundingClientRect()
      setSlot({ w: Math.max(1, r.width), h: Math.max(1, r.height) })
    }
    update()
    const ro = new ResizeObserver(update)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const stageW = Math.max(1, Math.min(slot.w, slot.h * aspect))
  const stageH = Math.max(1, stageW / aspect)
  const canvasW = Math.max(1, Math.round(stageW * dpr))
  const canvasH = Math.max(1, Math.round(stageH * dpr))

  useEffect(() => {
    if (!bgRef.current || !activeSource) return
    const canvas = bgRef.current
    canvas.width = canvasW
    canvas.height = canvasH
    if (activeSource instanceof HTMLVideoElement) {
      let raf = 0
      const loop = () => {
        if (!activeSource.paused && !activeSource.ended) {
          drawBackground(canvas, activeSource, activeSource.videoWidth || media.width, activeSource.videoHeight || media.height, crop)
        }
        raf = requestAnimationFrame(loop)
      }
      loop()
      return () => cancelAnimationFrame(raf)
    }
    drawBackground(canvas, activeSource, activeSource.naturalWidth || media.width, activeSource.naturalHeight || media.height, crop)
  }, [activeSource, canvasW, canvasH, crop, media.width, media.height])

  const lastStyleChangeRef = useRef(0)
  useLayoutEffect(() => {
    lastStyleChangeRef.current = performance.now()
    const timer = window.setTimeout(() => setMapIdleTick((value) => value + 1), 160)
    return () => window.clearTimeout(timer)
  }, [effectiveStyle, info])

  useLayoutEffect(() => {
    const canvas = overlayRef.current
    if (!canvas) return
    if (canvas.width !== canvasW) canvas.width = canvasW
    if (canvas.height !== canvasH) canvas.height = canvasH
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    if (!datasets) {
      ctx.clearRect(0, 0, canvas.width, canvas.height)
      layoutRef.current = null
      return
    }
    const maxDimension = Math.max(canvasW, canvasH)
    const fullMapScale = Math.min(1, 1200 / maxDimension)
    const fastMap = performance.now() - lastStyleChangeRef.current < 160
    const mapScale =
      fastMap && maxDimension > 1000 ? Math.min(fullMapScale, 650 / maxDimension) : fullMapScale
    const layout = renderOverlay({
      ctx,
      width: canvasW,
      height: canvasH,
      info,
      style: effectiveStyle,
      datasets,
      mapLayer: mapLayerRef.current,
      mapScale,
    })
    layoutRef.current = layout
    if (active) drawSelection(ctx, layout, active, dpr)
  }, [canvasW, canvasH, info, effectiveStyle, datasets, active, dpr, renderKey, mapIdleTick])

  function toCanvas(e: React.PointerEvent<HTMLCanvasElement>) {
    const canvas = overlayRef.current
    if (!canvas) return { x: 0, y: 0 }
    const rect = canvas.getBoundingClientRect()
    return {
      x: ((e.clientX - rect.left) / rect.width) * canvas.width,
      y: ((e.clientY - rect.top) / rect.height) * canvas.height,
    }
  }

  function onPointerDown(e: React.PointerEvent<HTMLCanvasElement>) {
    const existing = dragRef.current
    if (existing) {
      existing.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY })
      if (existing.pointers.size >= 2) {
        const pts = Array.from(existing.pointers.values())
        existing.mode = 'map-pinch'
        existing.initStyle = styleRef.current
        existing.pinchDist = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y)
        existing.pinchCenter = { x: (pts[0].x + pts[1].x) / 2, y: (pts[0].y + pts[1].y) / 2 }
        existing.pinchZoom = styleRef.current.mapInternalZoom
        setActive('map')
        setDragStyle({ ...styleRef.current })
      }
      e.currentTarget.setPointerCapture(e.pointerId)
      return
    }
    const layout = layoutRef.current
    if (!layout) return
    const p = toCanvas(e)
    const hit = hitTest(layout, p.x, p.y)
    const handleHit = 28 * dpr
    const tHandle = { x: layout.textBounds.x + layout.textBounds.width, y: layout.textBounds.y }
    const mHandle = layout.mapBounds
      ? { x: layout.mapBounds.x + layout.mapBounds.width, y: layout.mapBounds.y }
      : null
    let mode: DragMode = null
    let initCenter = { x: 0, y: 0 }
    let initDist = 1
    if (Math.hypot(p.x - tHandle.x, p.y - tHandle.y) <= handleHit) {
      mode = 'text-resize'
      initCenter = {
        x: layout.textBounds.x + layout.textBounds.width / 2,
        y: layout.textBounds.y + layout.textBounds.height / 2,
      }
      initDist = Math.max(20 * dpr, Math.hypot(p.x - initCenter.x, p.y - initCenter.y))
    } else if (mHandle && Math.hypot(p.x - mHandle.x, p.y - mHandle.y) <= handleHit) {
      mode = 'map-resize'
      initCenter = {
        x: layout.mapBounds!.x + layout.mapBounds!.width / 2,
        y: layout.mapBounds!.y + layout.mapBounds!.height / 2,
      }
      initDist = Math.max(20 * dpr, Math.hypot(p.x - initCenter.x, p.y - initCenter.y))
    } else if (hit === 'text') {
      mode = 'text-move'
    } else if (hit === 'map') {
      mode = 'map-move'
    }
    if (!mode) {
      setActive(null)
      return
    }
    e.currentTarget.setPointerCapture(e.pointerId)
    setActive(mode.startsWith('text') ? 'text' : 'map')
    setDragStyle({ ...styleRef.current })
    const pointers = new Map<number, { x: number; y: number }>()
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY })
    dragRef.current = {
      mode,
      pointerId: e.pointerId,
      startX: e.clientX,
      startY: e.clientY,
      initStyle: { ...styleRef.current },
      initCenter,
      initDist,
      pointers,
      pinchDist: 0,
      pinchCenter: { x: 0, y: 0 },
      pinchZoom: styleRef.current.mapInternalZoom,
    }
  }

  function onPointerMove(e: React.PointerEvent<HTMLCanvasElement>) {
    const drag = dragRef.current
    const canvas = overlayRef.current
    if (!drag || !canvas) return
    const now = performance.now()
    if (now - lastMoveRef.current < 14 && drag.pointers.size < 2) return
    lastMoveRef.current = now
    drag.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY })
    if (drag.pointers.size >= 2) {
      const pts = Array.from(drag.pointers.values())
      const dist = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y)
      const center = { x: (pts[0].x + pts[1].x) / 2, y: (pts[0].y + pts[1].y) / 2 }
      if (drag.mode !== 'map-pinch') {
        drag.mode = 'map-pinch'
        drag.pinchDist = dist
        drag.pinchCenter = center
        drag.pinchZoom = drag.initStyle.mapInternalZoom
        setActive('map')
      }
      const zoom = Math.min(5, Math.max(0.5, drag.pinchZoom * (dist / Math.max(1, drag.pinchDist))))
      const mapBounds = layoutRef.current?.mapBounds
      const panX = mapBounds
        ? drag.initStyle.mapInternalPanX + ((center.x - drag.pinchCenter.x) * dpr) / mapBounds.width
        : drag.initStyle.mapInternalPanX
      const panY = mapBounds
        ? drag.initStyle.mapInternalPanY - ((center.y - drag.pinchCenter.y) * dpr) / mapBounds.height
        : drag.initStyle.mapInternalPanY
      setDragStyle({
        ...drag.initStyle,
        mapInternalZoom: zoom,
        mapInternalPanX: Math.min(1.5, Math.max(-1.5, panX)),
        mapInternalPanY: Math.min(1.5, Math.max(-1.5, panY)),
      })
      return
    }
    const rect = canvas.getBoundingClientRect()
    const dx = ((e.clientX - drag.startX) / rect.width) * canvas.width
    const dy = ((e.clientY - drag.startY) / rect.height) * canvas.height
    if (drag.mode === 'text-move' || drag.mode === 'map-move') {
      const b = drag.mode === 'text-move' ? layoutRef.current?.textBounds : layoutRef.current?.mapBounds
      if (!b) return
      const init = drag.mode === 'text-move' ? drag.initStyle.textFreePos : drag.initStyle.mapFreePos
      const centerX = init?.x ?? (b.x + b.width / 2) / canvas.width
      const centerY = init?.y ?? (b.y + b.height / 2) / canvas.height
      const nx = Math.min(0.94, Math.max(0.06, centerX + dx / canvas.width))
      const ny = Math.min(0.94, Math.max(0.06, centerY + dy / canvas.height))
      if (drag.mode === 'text-move') {
        setDragStyle({ ...drag.initStyle, textFreePos: { x: nx, y: ny } })
      } else {
        setDragStyle({ ...drag.initStyle, mapFreePos: { x: nx, y: ny } })
      }
    } else if (drag.mode === 'text-resize' || drag.mode === 'map-resize') {
      const p = toCanvas(e)
      const dist = Math.max(1, Math.hypot(p.x - drag.initCenter.x, p.y - drag.initCenter.y))
      const scale = Math.min(2.5, Math.max(0.5, (drag.mode === 'text-resize' ? drag.initStyle.textScale : drag.initStyle.mapBoxScale) * (dist / drag.initDist)))
      if (drag.mode === 'text-resize') {
        setDragStyle({ ...drag.initStyle, textScale: scale })
      } else {
        setDragStyle({ ...drag.initStyle, mapBoxScale: scale })
      }
    }
  }

  function endDrag(e: React.PointerEvent<HTMLCanvasElement>) {
    const drag = dragRef.current
    if (drag) {
      const current = dragStyle ?? drag.initStyle
      updateStyle({
        textFreePos: current.textFreePos,
        mapFreePos: current.mapFreePos,
        textScale: current.textScale,
        mapBoxScale: current.mapBoxScale,
        mapInternalZoom: current.mapInternalZoom,
        mapInternalPanX: current.mapInternalPanX,
        mapInternalPanY: current.mapInternalPanY,
      })
    }
    if (drag?.pointers.has(e.pointerId)) drag.pointers.delete(e.pointerId)
    if (!drag?.pointers.size) {
      dragRef.current = null
      setDragStyle(null)
      setActive(null)
    }
  }

  useEffect(() => {
    const canvas = overlayRef.current
    if (!canvas) return
    const handler = (event: WheelEvent) => {
      const layout = layoutRef.current
      if (!layout?.mapBounds) return
      const rect = canvas.getBoundingClientRect()
      const x = ((event.clientX - rect.left) / rect.width) * canvas.width
      const y = ((event.clientY - rect.top) / rect.height) * canvas.height
      const b = layout.mapBounds
      if (x < b.x || x > b.x + b.width || y < b.y || y > b.y + b.height) return
      event.preventDefault()
      const next = Math.min(5, Math.max(0.5, styleRef.current.mapInternalZoom * Math.exp(-event.deltaY * 0.0015)))
      updateStyle({ mapInternalZoom: next })
    }
    canvas.addEventListener('wheel', handler, { passive: false })
    return () => canvas.removeEventListener('wheel', handler)
  }, [updateStyle])

  return (
    <div
      ref={stageSlotRef}
      data-testid="stage-slot"
      className={cn('absolute inset-4 flex items-center justify-center sm:inset-6', className)}>
    <div
      className="relative select-none overflow-hidden rounded-[2rem] bg-muted shadow-2xl shadow-primary/10 ring-1 ring-border/70 checkerboard"
      style={{ width: stageW, height: stageH }}
    >
      <canvas ref={bgRef} className="absolute inset-0 size-full" aria-hidden />
      <canvas
        ref={overlayRef}
        className="absolute inset-0 size-full touch-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70 focus-visible:ring-offset-2 focus-visible:ring-offset-background"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
        aria-label="透かしプレビュー。ドラッグでテキストと地図を移動できます。矢印キーで移動、+/-で拡大縮小できます。"
        tabIndex={0}
        onFocus={() => setActive((current) => current ?? 'text')}
        onKeyDown={(e) => {
          const target = active ?? 'text'
          const b = target === 'text' ? layoutRef.current?.textBounds : layoutRef.current?.mapBounds
          if (!b) return
          const step = e.shiftKey ? 0.02 : 0.006
          const init = target === 'text' ? styleRef.current.textFreePos : styleRef.current.mapFreePos
          let cx = init?.x ?? (b.x + b.width / 2) / canvasW
          let cy = init?.y ?? (b.y + b.height / 2) / canvasH
          if (e.key === 'ArrowLeft') cx -= step
          else if (e.key === 'ArrowRight') cx += step
          else if (e.key === 'ArrowUp') cy -= step
          else if (e.key === 'ArrowDown') cy += step
          else if (e.key === '+' || e.key === '=') {
            e.preventDefault()
            const style = styleRef.current
            updateStyle(
              target === 'text'
                ? { textScale: Math.min(2.5, style.textScale + 0.05) }
                : { mapBoxScale: Math.min(2.5, style.mapBoxScale + 0.05) },
            )
            return
          } else if (e.key === '-') {
            e.preventDefault()
            const style = styleRef.current
            updateStyle(
              target === 'text'
                ? { textScale: Math.max(0.5, style.textScale - 0.05) }
                : { mapBoxScale: Math.max(0.5, style.mapBoxScale - 0.05) },
            )
            return
          } else {
            return
          }
          e.preventDefault()
          cx = Math.min(0.94, Math.max(0.06, cx))
          cy = Math.min(0.94, Math.max(0.06, cy))
          updateStyle(target === 'text' ? { textFreePos: { x: cx, y: cy } } : { mapFreePos: { x: cx, y: cy } })
        }}
      />
      {activeSource instanceof HTMLVideoElement && (
        <div className="absolute top-3 right-3 z-20 flex gap-2">
          <Button
            variant="secondary"
            size="icon"
            className="rounded-full bg-background/80 backdrop-blur"
            onClick={() => {
              if (activeSource.paused) {
                void activeSource.play().catch(() => setPlaying(false))
              } else {
                activeSource.pause()
                setPlaying(false)
              }
            }}
            aria-label={playing ? '一時停止' : '再生'}
          >
            {playing ? <Pause className="size-4" /> : <Play className="size-4" />}
          </Button>
          <Button
            variant="secondary"
            size="icon"
            className="rounded-full bg-background/80 backdrop-blur"
            onClick={() => {
              activeSource.muted = !activeSource.muted
              setMuted(activeSource.muted)
            }}
            aria-label={muted ? 'ミュート解除' : 'ミュート'}
          >
            {muted ? <VolumeX className="size-4" /> : <Volume2 className="size-4" />}
          </Button>
        </div>
      )}
      {!activeSource && (
        <div className="absolute inset-0 flex items-center justify-center bg-muted/60 text-sm text-muted-foreground">
          メディアを読み込み中…
        </div>
      )}
    </div>
    </div>
  )
}
