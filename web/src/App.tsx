import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { Toaster, toast } from 'sonner'
import { useMediaQuery } from '@/hooks/use-media-query'
const Editor = lazy(() => import('@/components/editor/editor').then((m) => ({ default: m.Editor })))
import { EmptyState } from '@/components/empty-state'
const CameraDialog = lazy(() =>
  import('@/components/sheets/camera-dialog').then((m) => ({ default: m.CameraDialog })),
)
const SettingsSheet = lazy(() =>
  import('@/components/sheets/settings-sheet').then((m) => ({ default: m.SettingsSheet })),
)
import { TopBar } from '@/components/top-bar'
import { TooltipProvider } from '@/components/ui/tooltip'
import { initGeoWasm } from '@/lib/geo'
import { isMediaFile, mediaFromFile } from '@/lib/media'
import { ensureMapDatasetsFor, type MapDatasets } from '@/lib/map-data'
import { formatLatLon } from '@/lib/geo'
import { reverseGeocode } from '@/lib/data'
import { type TransportInfo } from '@/types'
import { loadOverlayFont, overlayText } from '@/lib/fonts'
import { useAppStore } from '@/store/app-store'
import { useUiStore } from '@/store/ui-store'

function App() {
  const media = useAppStore((s) => s.media)
  const info = useAppStore((s) => s.info)
  const font = useAppStore((s) => s.style.font)
  const setMedia = useAppStore((s) => s.setMedia)
  const settings = useAppStore((s) => s.settings)
  const updateInfo = useAppStore((s) => s.updateInfo)
  const updateInfoIfRevision = useAppStore((s) => s.updateInfoIfRevision)
  const resetCrop = useAppStore((s) => s.resetCrop)
  const systemDark = useMediaQuery('(prefers-color-scheme: dark)')
  const setCameraOpen = useUiStore((s) => s.setCameraOpen)
  const cameraOpen = useUiStore((s) => s.cameraOpen)
  const sheet = useUiStore((s) => s.sheet)
  const inputRef = useRef<HTMLInputElement>(null)
  const [datasets, setDatasets] = useState<MapDatasets | null>(null)
  const [dragging, setDragging] = useState(false)
  const [wasmReady, setWasmReady] = useState(false)
  const [fontVersion, setFontVersion] = useState(0)
  const mediaToken = useRef(0)
  const [mapAttempt, setMapAttempt] = useState(0)

  useEffect(() => {
    void loadOverlayFont(font, overlayText(info))
      .then(() => setFontVersion((v) => v + 1))
      .catch(() => undefined)
  }, [font, info])

  useEffect(() => {
    void initGeoWasm()
      .then(() => setWasmReady(true))
      .catch((err) => {
        console.error(err)
        toast.error('Wasm モジュールの読み込みに失敗しました。簡易計算で続行します')
      })
  }, [])

  useEffect(() => {
    if (!media && !cameraOpen) return
    const points = [
      info.photoLat != null && info.photoLon != null ? { lat: info.photoLat, lon: info.photoLon } : null,
      info.departureLat != null && info.departureLon != null ? { lat: info.departureLat, lon: info.departureLon } : null,
      info.arrivalLat != null && info.arrivalLon != null ? { lat: info.arrivalLat, lon: info.arrivalLon } : null,
    ].filter((p): p is { lat: number; lon: number } => p != null)
    ensureMapDatasetsFor(points)
      .then(setDatasets)
      .catch((err) => {
        console.error(err)
        setDatasets(null)
        toast.error('地図データの読み込みに失敗しました')
      })
  }, [media, cameraOpen, mapAttempt, info.photoLat, info.photoLon, info.departureLat, info.departureLon, info.arrivalLat, info.arrivalLon])

  const dark =
    settings.themeMode === 'DARK' || (settings.themeMode === 'SYSTEM' && systemDark)

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark)
    if (settings.dynamicColor) {
      document.documentElement.style.setProperty(
        '--primary',
        dark ? 'oklch(0.78 0.1 265)' : 'oklch(0.48 0.1 265)',
      )
      document.documentElement.style.setProperty(
        '--ring',
        dark ? 'oklch(0.7 0.1 265)' : 'oklch(0.55 0.1 265)',
      )
    } else {
      document.documentElement.style.removeProperty('--primary')
      document.documentElement.style.removeProperty('--ring')
    }
  }, [dark, settings.dynamicColor])

  async function openFiles(files: FileList | File[]) {
    const file = Array.from(files).find(isMediaFile)
    if (!file) {
      toast.error('画像または動画を選択してください')
      return
    }
    const token = ++mediaToken.current
    try {
      const asset = await mediaFromFile(file)
      if (token !== mediaToken.current) {
        URL.revokeObjectURL(asset.url)
        return
      }
      const now = new Date()
      const patch: Partial<TransportInfo> = {
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
      }

      if (file.type.startsWith('image/') || (!file.type && /\.(jpe?g|png|webp|gif|heic|heif|avif|bmp)$/i.test(file.name))) {
        const exifr = await import('exifr')
        let gps: { latitude: number; longitude: number } | null = null
        try {
          gps = await exifr.gps(file)
        } catch {
          // GPS is optional.
        }
        try {
          const meta = await exifr.parse(file, { pick: ['DateTimeOriginal'] })
          const date = meta?.DateTimeOriginal
          if (date instanceof Date && !Number.isNaN(date.getTime())) {
            patch.year = date.getFullYear()
            patch.month = date.getMonth() + 1
            patch.day = date.getDate()
            patch.hour = date.getHours()
            patch.minute = date.getMinutes()
          }
        } catch {
          // Date is optional.
        }
        if (token !== mediaToken.current) return
        if (gps?.latitude != null && gps?.longitude != null) {
          setMedia(asset)
          resetCrop()
          patch.photoLat = gps.latitude
          patch.photoLon = gps.longitude
          patch.photoLocationName = formatLatLon(gps.latitude, gps.longitude, 5)
          updateInfo(patch)
          const revision = useAppStore.getState().infoRevision
          const label = await reverseGeocode(gps.latitude, gps.longitude)
          if (token === mediaToken.current && label) {
            updateInfoIfRevision(revision, { photoLocationName: label })
          }
          return
        }
      }
      if (token !== mediaToken.current) return
      setMedia(asset)
      resetCrop()
      updateInfo(patch)
    } catch (err) {
      if (token === mediaToken.current) {
        toast.error(err instanceof Error ? err.message : 'メディアを読み込めませんでした')
      }
    }
  }

  return (
    <TooltipProvider delayDuration={250}>
      <div
        className="flex min-h-dvh flex-col"
        onDragEnter={(e) => {
          e.preventDefault()
          setDragging(true)
        }}
        onDragOver={(e) => e.preventDefault()}
        onDragLeave={(e) => {
          if (e.currentTarget === e.target) setDragging(false)
        }}
        onDrop={(e) => {
          e.preventDefault()
          setDragging(false)
          if (e.dataTransfer.files.length) void openFiles(e.dataTransfer.files)
        }}
      >
        <input
          ref={inputRef}
          type="file"
          accept="image/*,video/*"
          className="hidden"
          onChange={(e) => {
            if (e.target.files?.length) void openFiles(e.target.files)
            e.target.value = ''
          }}
        />
        <TopBar onPick={() => inputRef.current?.click()} onCamera={() => setCameraOpen(true)} />
        <main
          className="flex min-h-0 flex-1 flex-col"
          style={{
            paddingLeft: 'env(safe-area-inset-left)',
            paddingRight: 'env(safe-area-inset-right)',
          }}
        >
          {media ? (
            <Editor
              datasets={datasets}
              renderKey={fontVersion * 2 + (wasmReady ? 1 : 0)}
              onRetryMap={() => setMapAttempt((v) => v + 1)}
            />
          ) : (
            <EmptyState
              onPick={() => inputRef.current?.click()}
              onCamera={() => setCameraOpen(true)}
            />
          )}
        </main>
        {dragging && (
          <div className="pointer-events-none fixed inset-3 z-[60] flex items-center justify-center rounded-3xl border-2 border-dashed border-primary bg-background/85 text-sm font-semibold text-primary backdrop-blur">
            ドロップして読み込む
          </div>
        )}
        {cameraOpen && (
          <Suspense fallback={null}>
            <CameraDialog datasets={datasets} />
          </Suspense>
        )}
        {sheet === 'settings' && (
          <Suspense fallback={null}>
            <SettingsSheet />
          </Suspense>
        )}
        <Toaster
          position="top-center"
          richColors
          closeButton
          theme={dark ? 'dark' : 'light'}
          containerAriaLabel="通知"
        />
      </div>
    </TooltipProvider>
  )
}

export default App
