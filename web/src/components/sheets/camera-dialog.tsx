import { useCallback, useEffect, useRef, useState } from 'react'
import { Camera, RefreshCw, X } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { mediaFromFile } from '@/lib/media'
import type { MapDatasets } from '@/lib/map-data'
import { renderOverlay } from '@/lib/renderer'
import { useAppStore } from '@/store/app-store'
import { useUiStore } from '@/store/ui-store'
import { reverseGeocode } from '@/lib/data'

interface CameraDialogProps {
  datasets: MapDatasets | null
}

export function CameraDialog({ datasets }: CameraDialogProps) {
  const open = useUiStore((s) => s.cameraOpen)
  const setOpen = useUiStore((s) => s.setCameraOpen)
  const info = useAppStore((s) => s.info)
  const style = useAppStore((s) => s.style)
  const settings = useAppStore((s) => s.settings)
  const setMedia = useAppStore((s) => s.setMedia)
  const resetCrop = useAppStore((s) => s.resetCrop)
  const updateInfo = useAppStore((s) => s.updateInfo)
  const updateInfoIfRevision = useAppStore((s) => s.updateInfoIfRevision)
  const videoRef = useRef<HTMLVideoElement>(null)
  const overlayRef = useRef<HTMLCanvasElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const [facing, setFacing] = useState<'environment' | 'user'>('environment')
  const [ready, setReady] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [capturing, setCapturing] = useState(false)
  const [retryKey, setRetryKey] = useState(0)
  const [videoAspect, setVideoAspect] = useState(4 / 3)
  const captureId = useRef(0)
  const capturingRef = useRef(false)

  const stop = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
    setReady(false)
  }, [])

  useEffect(() => {
    if (!open) {
      captureId.current++
      stop()
      return
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      setError('このブラウザ/環境ではカメラを利用できません。HTTPS で開いているか確認してください。')
      return
    }
    let cancelled = false
    setError(null)
    setReady(false)
    const timer = window.setTimeout(() => {
      if (!cancelled) setError('カメラの起動がタイムアウトしました。再接続してください。')
    }, 10_000)
    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: facing, width: { ideal: 1920 }, height: { ideal: 1080 } }, audio: false })
      .then((stream) => {
        window.clearTimeout(timer)
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop())
          return
        }
        streamRef.current = stream
        const video = videoRef.current
        if (video) {
          video.onloadedmetadata = () => {
            if (video.videoWidth && video.videoHeight) setVideoAspect(video.videoWidth / video.videoHeight)
            setReady(true)
            void video.play().catch(() => undefined)
          }
          video.srcObject = stream
        }
      })
      .catch((err: unknown) => {
        window.clearTimeout(timer)
        console.error(err)
        const name = err instanceof DOMException ? err.name : ''
        setError(
          name === 'NotAllowedError'
            ? 'カメラの使用が許可されていません。ブラウザの設定から許可してください。'
            : name === 'NotFoundError'
              ? 'カメラが見つかりません。'
              : name === 'NotReadableError'
                ? 'カメラが他のアプリで使用中です。'
                : 'カメラを起動できませんでした。ブラウザのカメラ権限を確認してください。',
        )
      })
    return () => {
      cancelled = true
      window.clearTimeout(timer)
      stop()
    }
  }, [open, facing, stop, retryKey])

  useEffect(() => {
    if (!ready) return
    const video = videoRef.current
    const canvas = overlayRef.current
    if (!video || !canvas) return
    const width = video.videoWidth || 1280
    const height = video.videoHeight || 720
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    if (!ctx || !datasets) return
    renderOverlay({ ctx, width, height, info, style, datasets })
  }, [ready, info, style, datasets])

  async function capture() {
    const video = videoRef.current
    if (!video || capturingRef.current) return
    capturingRef.current = true
    setCapturing(true)
    const token = captureId.current
    try {
      const canvas = document.createElement('canvas')
      canvas.width = video.videoWidth || 1280
      canvas.height = video.videoHeight || 720
      const ctx = canvas.getContext('2d')
      if (!ctx) throw new Error('Canvas unavailable')
      ctx.drawImage(video, 0, 0)
      const blob = await new Promise<Blob>((resolve, reject) => {
        canvas.toBlob(
          (b) => (b ? resolve(b) : reject(new Error('撮影に失敗しました'))),
          'image/jpeg',
          0.95,
        )
      })
      const file = new File([blob], `camera_${Date.now()}.jpg`, { type: 'image/jpeg' })
      const asset = await mediaFromFile(file)
      if (token !== captureId.current) {
        URL.revokeObjectURL(asset.url)
        return
      }
      setMedia(asset)
      resetCrop()
      setOpen(false)
      toast.success('撮影しました')

      const now = new Date()
      updateInfo({
        photoLat: null,
        photoLon: null,
        photoLocationName: '',
        photoCountryCode: '',
        routePolylines: [],
        year: now.getFullYear(),
        month: now.getMonth() + 1,
        day: now.getDate(),
        hour: now.getHours(),
        minute: now.getMinutes(),
      })

      const revision = useAppStore.getState().infoRevision
      if (settings.autoLocationEnabled && navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          async (pos) => {
            const label = await reverseGeocode(pos.coords.latitude, pos.coords.longitude)
            updateInfoIfRevision(revision, {
              photoLat: pos.coords.latitude,
              photoLon: pos.coords.longitude,
              photoLocationName: label,
              photoCountryCode: '',
            })
          },
          () => undefined,
          { timeout: 6000, maximumAge: 120000 },
        )
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '撮影に失敗しました')
    } finally {
      capturingRef.current = false
      setCapturing(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent
        showCloseButton={false}
        className="max-w-xl gap-0 overflow-hidden rounded-3xl border-0 bg-black p-0 text-white"
      >
        <DialogHeader className="sr-only">
          <DialogTitle>カメラ</DialogTitle>
          <DialogDescription>カメラプレビューと透かしの合成</DialogDescription>
        </DialogHeader>
        <div className="relative w-full bg-black" style={{ aspectRatio: videoAspect }}>
          <video
            ref={videoRef}
            className="absolute inset-0 size-full object-cover"
            playsInline
            muted
            autoPlay
          />
          <canvas ref={overlayRef} className="pointer-events-none absolute inset-0 size-full" />
          {!ready && !error && (
            <div className="absolute inset-0 flex items-center justify-center text-sm text-white/70">
              カメラを起動中…
            </div>
          )}
          {error && (
            <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 p-6 text-center text-sm text-white/80">
              <p>{error}</p>
              <div className="flex gap-2">
                <Button variant="secondary" onClick={() => setRetryKey((v) => v + 1)}>
                  再試行
                </Button>
                <Button variant="secondary" onClick={() => setOpen(false)}>
                  閉じる
                </Button>
              </div>
            </div>
          )}
          <div className="absolute inset-x-0 top-0 flex items-center justify-between p-3">
            <Button
              size="icon"
              variant="secondary"
              className="rounded-full bg-white/20 text-white backdrop-blur hover:bg-white/30"
              onClick={() => setOpen(false)}
              aria-label="閉じる"
            >
              <X className="size-5" />
            </Button>
            <Button
              size="icon"
              variant="secondary"
              className="rounded-full bg-white/20 text-white backdrop-blur hover:bg-white/30"
              onClick={() => setFacing((f) => (f === 'environment' ? 'user' : 'environment'))}
              aria-label="カメラ切替"
            >
              <RefreshCw className="size-5" />
            </Button>
          </div>
          <div className="absolute inset-x-0 bottom-0 flex justify-center p-6">
            <Button
              size="icon"
              className="size-16 rounded-full border-4 border-white bg-white/20 text-white shadow-xl backdrop-blur hover:bg-white/30"
              onClick={() => void capture()}
              disabled={!ready || capturing}
              aria-label="撮影"
            >
              <Camera className="size-7" />
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
