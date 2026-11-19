import { useEffect, useRef, useState } from 'react'
import * as exifr from 'exifr'
import { Toaster, toast } from 'sonner'
import { useMediaQuery } from '@/hooks/use-media-query'
import { Editor } from '@/components/editor/editor'
import { EmptyState } from '@/components/empty-state'
import { CameraDialog } from '@/components/sheets/camera-dialog'
import { TopBar } from '@/components/top-bar'
import { TooltipProvider } from '@/components/ui/tooltip'
import { initGeoWasm } from '@/lib/geo'
import { mediaFromFile } from '@/lib/media'
import { loadAllMapDatasets, type MapDatasets } from '@/lib/map-data'
import { formatLatLon } from '@/lib/geo'
import { reverseGeocode } from '@/lib/data'
import type { TransportInfo } from '@/types'
import { useAppStore } from '@/store/app-store'
import { useUiStore } from '@/store/ui-store'

function App() {
  const media = useAppStore((s) => s.media)
  const setMedia = useAppStore((s) => s.setMedia)
  const settings = useAppStore((s) => s.settings)
  const updateInfo = useAppStore((s) => s.updateInfo)
  const updateInfoIfRevision = useAppStore((s) => s.updateInfoIfRevision)
  const resetCrop = useAppStore((s) => s.resetCrop)
  const systemDark = useMediaQuery('(prefers-color-scheme: dark)')
  const setCameraOpen = useUiStore((s) => s.setCameraOpen)
  const cameraOpen = useUiStore((s) => s.cameraOpen)
  const inputRef = useRef<HTMLInputElement>(null)
  const [datasets, setDatasets] = useState<MapDatasets | null>(null)
  const [dragging, setDragging] = useState(false)
  const [wasmReady, setWasmReady] = useState(false)
  const mediaToken = useRef(0)
  const mapRequested = useRef(false)

  useEffect(() => {
    void initGeoWasm()
      .then(() => setWasmReady(true))
      .catch((err) => {
        console.error(err)
        toast.error('Wasm モジュールの読み込みに失敗しました。簡易計算で続行します')
      })
  }, [])

  useEffect(() => {
    if ((!media && !cameraOpen) || datasets || mapRequested.current) return
    mapRequested.current = true
    loadAllMapDatasets()
      .then(setDatasets)
      .catch((err) => {
        console.error(err)
        setDatasets(null)
        toast.error('地図データの読み込みに失敗しました')
      })
  }, [media, cameraOpen, datasets])

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
    const file = Array.from(files).find(
      (f) => f.type.startsWith('image/') || f.type.startsWith('video/'),
    )
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
      setMedia(asset)
      resetCrop()

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

      if (file.type.startsWith('image/')) {
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
        <main className="flex min-h-0 flex-1 flex-col">
          {media ? (
            <Editor datasets={datasets} renderKey={wasmReady ? 1 : 0} />
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
        <CameraDialog datasets={datasets} />
        <Toaster position="top-center" richColors closeButton />
      </div>
    </TooltipProvider>
  )
}

export default App
