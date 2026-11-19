import type { CropFrameSettings, OverlayStyle, TransportInfo } from '@/types'
import type { MapDatasets } from '@/lib/map-data'
import { renderOverlay } from '@/lib/renderer'

export interface CompositeInput {
  source: CanvasImageSource
  sourceWidth: number
  sourceHeight: number
  info: TransportInfo
  style: OverlayStyle
  crop: CropFrameSettings
  datasets: MapDatasets
}

export function computeCropRect(
  srcW: number,
  srcH: number,
  crop: CropFrameSettings,
): { x: number; y: number; width: number; height: number } {
  const targetAspect = effectiveAspect(crop, srcW, srcH)
  const zoom = Math.min(5, Math.max(1, crop.cropZoom))
  const srcAspect = srcW / srcH
  let baseW: number
  let baseH: number
  if (srcAspect > targetAspect) {
    baseH = srcH
    baseW = baseH * targetAspect
  } else {
    baseW = srcW
    baseH = baseW / targetAspect
  }
  const w = Math.max(32, baseW / zoom)
  const h = Math.max(32, baseH / zoom)
  const maxX = srcW - w
  const maxY = srcH - h
  const x = Math.min(Math.max(0, crop.cropCenterX * srcW - w / 2), maxX)
  const y = Math.min(Math.max(0, crop.cropCenterY * srcH - h / 2), maxY)
  return { x, y, width: w, height: h }
}

export function effectiveAspect(crop: CropFrameSettings, srcW: number, srcH: number): number {
  if (crop.aspectPreset === 'FREE') return Math.min(2.5, Math.max(0.4, crop.freeAspectRatio))
  const map: Record<string, number | null> = {
    ORIGINAL: null,
    FREE: null,
    RATIO_4_5: 4 / 5,
    RATIO_9_16: 9 / 16,
    RATIO_2_3: 2 / 3,
    RATIO_1_1: 1,
  }
  const base = map[crop.aspectPreset] ?? null
  if (base == null) return srcW / Math.max(1, srcH)
  return crop.isLandscapeOrientation && base !== 1 ? 1 / base : base
}

export function createOverlayCanvas(
  width: number,
  height: number,
  input: Omit<CompositeInput, 'source' | 'sourceWidth' | 'sourceHeight'>,
): HTMLCanvasElement {
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(width))
  canvas.height = Math.max(1, Math.round(height))
  const ctx = canvas.getContext('2d')
  if (!ctx) return canvas
  renderOverlay({
    ctx,
    width: canvas.width,
    height: canvas.height,
    info: input.info,
    style: input.style,
    datasets: input.datasets,
  })
  return canvas
}

export function compositeToCanvas(input: CompositeInput): HTMLCanvasElement {
  const crop = computeCropRect(input.sourceWidth, input.sourceHeight, input.crop)
  const outW = Math.max(1, Math.round(crop.width))
  const outH = Math.max(1, Math.round(crop.height))
  const canvas = document.createElement('canvas')
  canvas.width = outW
  canvas.height = outH
  const ctx = canvas.getContext('2d')
  if (!ctx) return canvas
  ctx.imageSmoothingQuality = 'high'
  const margin = Math.min(0.49, Math.max(0, input.crop.frameMarginRatio))
  if (margin > 0.001) {
    ctx.fillStyle = input.crop.frameColor
    ctx.fillRect(0, 0, outW, outH)
    const innerW = outW * (1 - margin)
    const innerH = outH * (1 - margin)
    ctx.drawImage(
      input.source,
      crop.x,
      crop.y,
      crop.width,
      crop.height,
      (outW - innerW) / 2,
      (outH - innerH) / 2,
      innerW,
      innerH,
    )
  } else {
    ctx.drawImage(input.source, crop.x, crop.y, crop.width, crop.height, 0, 0, outW, outH)
  }

  const overlay = createOverlayCanvas(outW, outH, {
    info: input.info,
    style: input.style,
    crop: input.crop,
    datasets: input.datasets,
  })
  ctx.drawImage(overlay, 0, 0)
  return canvas
}

export function canvasToBlob(
  canvas: HTMLCanvasElement,
  format: 'JPEG_95' | 'PNG_LOSSLESS',
): Promise<Blob | null> {
  return new Promise((resolve) => {
    canvas.toBlob(
      (blob) => resolve(blob),
      format === 'PNG_LOSSLESS' ? 'image/png' : 'image/jpeg',
      0.95,
    )
  })
}

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1500)
}

export function exportFilename(ext: string): string {
  const d = new Date()
  const pad = (v: number) => String(v).padStart(2, '0')
  return `SukashiMap_${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}.${ext}`
}

export interface VideoExportOptions {
  video: HTMLVideoElement
  info: TransportInfo
  style: OverlayStyle
  crop: CropFrameSettings
  datasets: MapDatasets
  onProgress?: (p: number) => void
}

function pickMime(): string {
  const candidates = [
    'video/webm;codecs=vp9,opus',
    'video/webm;codecs=vp9',
    'video/webm;codecs=vp8,opus',
    'video/webm;codecs=vp8',
    'video/webm',
  ]
  return candidates.find((m) => MediaRecorder.isTypeSupported(m)) ?? 'video/webm'
}

export async function exportVideo(options: VideoExportOptions): Promise<Blob> {
  const { video, info, style, crop, datasets, onProgress } = options
  const width = video.videoWidth
  const height = video.videoHeight
  if (!width || !height) throw new Error('動画のサイズを取得できませんでした')

  const cropRect = computeCropRect(width, height, crop)
  const outW = Math.max(1, Math.round(cropRect.width))
  const outH = Math.max(1, Math.round(cropRect.height))
  const overlay = createOverlayCanvas(outW, outH, { info, style, crop, datasets })
  const canvas = document.createElement('canvas')
  canvas.width = outW
  canvas.height = outH
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Canvas 2D context unavailable')
  const margin = Math.min(0.49, Math.max(0, crop.frameMarginRatio))
  const drawFrame = () => {
    ctx.clearRect(0, 0, outW, outH)
    if (margin > 0.001) {
      ctx.fillStyle = crop.frameColor
      ctx.fillRect(0, 0, outW, outH)
      const innerW = outW * (1 - margin)
      const innerH = outH * (1 - margin)
      ctx.drawImage(
        video,
        cropRect.x,
        cropRect.y,
        cropRect.width,
        cropRect.height,
        (outW - innerW) / 2,
        (outH - innerH) / 2,
        innerW,
        innerH,
      )
    } else {
      ctx.drawImage(video, cropRect.x, cropRect.y, cropRect.width, cropRect.height, 0, 0, outW, outH)
    }
    ctx.drawImage(overlay, 0, 0)
  }

  const stream = canvas.captureStream(30)
  try {
    const source = (video as HTMLVideoElement & { captureStream?: () => MediaStream }).captureStream?.()
    source?.getAudioTracks().forEach((track: MediaStreamTrack) => stream.addTrack(track))
  } catch {
    // Audio capture is best-effort in browsers.
  }

  const mime = pickMime()
  const recorder = new MediaRecorder(stream, { mimeType: mime, videoBitsPerSecond: 8_000_000 })
  const chunks: Blob[] = []
  recorder.ondataavailable = (e) => {
    if (e.data.size) chunks.push(e.data)
  }
  const done = new Promise<Blob>((resolve, reject) => {
    recorder.onstop = () => resolve(new Blob(chunks, { type: mime }))
    recorder.onerror = () => reject(new Error('動画の書き出しに失敗しました'))
  })

  const draw = () => {
    drawFrame()
    onProgress?.(video.duration ? Math.min(1, video.currentTime / video.duration) : 0)
  }

  video.pause()
  video.currentTime = 0
  await new Promise<void>((resolve) => {
    const onSeeked = () => {
      video.removeEventListener('seeked', onSeeked)
      resolve()
    }
    video.addEventListener('seeked', onSeeked)
  })

  recorder.start(250)
  await video.play()
  let raf = 0
  const loop = () => {
    draw()
    if (!video.ended && !video.paused) raf = requestAnimationFrame(loop)
  }
  loop()
  await new Promise<void>((resolve) => {
    const onEnded = () => {
      video.removeEventListener('ended', onEnded)
      resolve()
    }
    video.addEventListener('ended', onEnded)
  })
  cancelAnimationFrame(raf)
  recorder.stop()
  video.pause()
  onProgress?.(1)
  return done
}

export async function shareOrDownload(blob: Blob, filename: string, mime: string) {
  const file = new File([blob], filename, { type: mime })
  if (navigator.canShare?.({ files: [file] })) {
    await navigator.share({ files: [file], title: 'Sukashi Map' })
  } else {
    downloadBlob(blob, filename)
  }
}
