import fixWebmDuration from 'fix-webm-duration'
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
  /** JPEG has no alpha; fill this colour before drawing. */
  background?: string | null
}

const MAX_EXPORT_PIXELS = 16_000_000

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
  const raw = base == null ? srcW / Math.max(1, srcH) : crop.isLandscapeOrientation && base !== 1 ? 1 / base : base
  return Math.min(3.2, Math.max(0.3, raw))
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
  const rawW = Math.max(1, Math.round(crop.width))
  const rawH = Math.max(1, Math.round(crop.height))
  const scale = Math.min(1, Math.sqrt(MAX_EXPORT_PIXELS / (rawW * rawH)))
  const outW = Math.max(1, Math.round(rawW * scale))
  const outH = Math.max(1, Math.round(rawH * scale))
  const canvas = document.createElement('canvas')
  canvas.width = outW
  canvas.height = outH
  const ctx = canvas.getContext('2d')
  if (!ctx) return canvas
  ctx.imageSmoothingQuality = 'high'
  if (input.background) {
    ctx.fillStyle = input.background
    ctx.fillRect(0, 0, outW, outH)
  }
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
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
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
  signal?: AbortSignal
}

export function canExportVideo(): boolean {
  return (
    typeof MediaRecorder !== 'undefined' &&
    typeof HTMLCanvasElement !== 'undefined' &&
    typeof HTMLCanvasElement.prototype.captureStream === 'function'
  )
}

function pickMime(): string {
  if (typeof MediaRecorder === 'undefined' || typeof MediaRecorder.isTypeSupported !== 'function') {
    return 'video/webm'
  }
  const candidates = [
    'video/mp4;codecs=avc1.42E01E,mp4a.40.2',
    'video/mp4;codecs=avc1.42E01E',
    'video/mp4',
    'video/webm;codecs=vp9,opus',
    'video/webm;codecs=vp9',
    'video/webm;codecs=vp8,opus',
    'video/webm;codecs=vp8',
    'video/webm',
  ]
  try {
    return candidates.find((m) => MediaRecorder.isTypeSupported(m)) ?? 'video/webm'
  } catch {
    return 'video/webm'
  }
}

export interface VideoExportResult {
  blob: Blob
  mime: string
  extension: 'webm' | 'mp4'
  hasAudio: boolean
  audioCaptureFailed: boolean
}

export async function exportVideo(options: VideoExportOptions): Promise<VideoExportResult> {
  const { video, info, style, crop, datasets, onProgress, signal } = options
  if (!canExportVideo()) {
    throw new Error('このブラウザは動画の書き出しに対応していません')
  }
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
  let recorder: MediaRecorder | null = null
  let raf = 0
  let audioCaptureFailed = false
  let timeoutTimer: ReturnType<typeof setTimeout> | undefined
  let abortHandler: (() => void) | undefined
  const startedAt = performance.now()
  try {
    const captureStream = (video as HTMLVideoElement & {
      captureStream?: () => MediaStream
      mozCaptureStream?: () => MediaStream
    }).captureStream ?? (video as HTMLVideoElement & {
      mozCaptureStream?: () => MediaStream
    }).mozCaptureStream
    if (!captureStream) {
      audioCaptureFailed = true
    } else {
      try {
        const source = captureStream.call(video)
        source.getAudioTracks().forEach((track: MediaStreamTrack) => stream.addTrack(track))
      } catch {
        audioCaptureFailed = true
      }
    }
    const hasAudio = stream.getAudioTracks().length > 0

    const mime = pickMime()
    recorder = new MediaRecorder(stream, {
      mimeType: mime,
      videoBitsPerSecond: 8_000_000,
    })
    const chunks: Blob[] = []
    recorder.ondataavailable = (e) => {
      if (e.data.size) chunks.push(e.data)
    }
    let rejectRecording: ((error: Error) => void) | null = null
    const recordingFailed = new Promise<never>((_, reject) => {
      rejectRecording = reject
    })
    // The failure promise is raced below; a late rejection after a normal stop
    // must not surface as an unhandled rejection.
    recordingFailed.catch(() => undefined)
    const done = new Promise<Blob>((resolve, reject) => {
      recorder!.onstop = () => resolve(new Blob(chunks, { type: recorder!.mimeType || mime }))
      recorder!.onerror = (event) => {
        const error = new Error(
          event.error?.message
            ? `動画の書き出しに失敗しました: ${event.error.message}`
            : '動画の書き出しに失敗しました',
        )
        rejectRecording?.(error)
        reject(error)
      }
    })
    done.catch(() => undefined)

    const draw = () => {
      drawFrame()
      if (Number.isFinite(video.duration) && video.duration > 0) {
        onProgress?.(Math.min(1, video.currentTime / video.duration))
      }
    }

    if (video.currentTime > 0.02) {
      video.currentTime = 0
      await new Promise<void>((resolve, reject) => {
        const timer = setTimeout(() => {
          video.removeEventListener('seeked', onSeeked)
          reject(new Error('動画のシークがタイムアウトしました'))
        }, 3000)
        const onSeeked = () => {
          clearTimeout(timer)
          video.removeEventListener('seeked', onSeeked)
          resolve()
        }
        video.addEventListener('seeked', onSeeked)
      })
    }

    let endedResolve: () => void = () => undefined
    let endedReject: (err: Error) => void = () => undefined
    const ended = new Promise<void>((resolve, reject) => {
      endedResolve = resolve
      endedReject = reject
    })
    const onEnded = () => endedResolve()
    const onVideoError = () => endedReject(new Error('動画の再生に失敗しました'))
    video.addEventListener('ended', onEnded)
    video.addEventListener('error', onVideoError)

    const abort = new Promise<never>((_, reject) => {
      if (!signal) return
      if (signal.aborted) {
        reject(new DOMException('Aborted', 'AbortError'))
        return
      }
      abortHandler = () => reject(new DOMException('Aborted', 'AbortError'))
      signal.addEventListener('abort', abortHandler, { once: true })
    })
    const timeout = new Promise<never>((_, reject) => {
      const seconds = Number.isFinite(video.duration) ? video.duration : 30
      timeoutTimer = setTimeout(
        () => reject(new Error('動画の書き出しがタイムアウトしました')),
        Math.max(15_000, seconds * 2_000 + 10_000),
      )
    })
    timeout.catch(() => undefined)

    try {
      recorder.start(250)
      // A stalled play() must not sit outside the cancellation/timeout race.
      await Promise.race([video.play(), abort, timeout, recordingFailed])
      const loop = () => {
        if (video.ended) {
          draw()
          return
        }
        if (!video.paused) draw()
        raf = requestAnimationFrame(loop)
      }
      loop()
      await Promise.race([ended, abort, timeout, recordingFailed])
    } finally {
      cancelAnimationFrame(raf)
      video.removeEventListener('ended', onEnded)
      video.removeEventListener('error', onVideoError)
      if (recorder && recorder.state !== 'inactive') {
        try {
          recorder.stop()
        } catch {
          // A recorder that already errored may reject stop; the failure is
          // surfaced through recordingFailed/done.
        }
      }
    }

    // Stopping the recorder is asynchronous too, so the final blob must stay
    // inside the same cancellation and timeout boundary.
    const blob = await Promise.race([done, abort, timeout, recordingFailed])
    if (blob.size === 0) throw new Error('動画データが空です')
    const durationMs =
      Number.isFinite(video.duration) && video.duration > 0
        ? video.duration * 1000
        : performance.now() - startedAt
    const finalBlob =
      (recorder.mimeType || mime).includes('webm')
        ? await fixWebmDuration(blob, durationMs, { logger: false }).catch(() => blob)
        : blob
    onProgress?.(1)
    return {
      blob: finalBlob,
      mime: finalBlob.type || recorder.mimeType || mime,
      extension: (recorder.mimeType || mime).includes('mp4') ? 'mp4' : 'webm',
      hasAudio,
      audioCaptureFailed,
    }
  } finally {
    if (timeoutTimer) clearTimeout(timeoutTimer)
    if (signal && abortHandler) signal.removeEventListener('abort', abortHandler)
    cancelAnimationFrame(raf)
    video.pause()
    if (recorder && recorder.state !== 'inactive') {
      try {
        recorder.stop()
      } catch {
        // Ignore: cleanup must not mask the original export error.
      }
    }
    stream.getTracks().forEach((track) => track.stop())
  }
}

export async function shareOrDownload(
  blob: Blob,
  filename: string,
  mime: string,
): Promise<'shared' | 'downloaded' | 'cancelled'> {
  const file = new File([blob], filename, { type: mime })
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title: 'Sukashi Map' })
      return 'shared'
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return 'cancelled'
    }
  }
  downloadBlob(blob, filename)
  return 'downloaded'
}
