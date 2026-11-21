import { useEffect, useRef, useState } from 'react'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { FloatingToolbar } from '@/components/editor/floating-toolbar'
import { PreviewStage } from '@/components/editor/preview-stage'
import { RouteChip } from '@/components/editor/route-chip'
import { CropSheet } from '@/components/sheets/crop-sheet'
import { InfoSheet } from '@/components/sheets/info-sheet'
import { StyleSheet } from '@/components/sheets/style-sheet'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { useMediaElement } from '@/hooks/use-media-element'
import { useIsCoarsePointer, useIsMobile, useIsShortLandscape } from '@/hooks/use-media-query'
import {
  canExportVideo,
  canvasToBlob,
  compositeToCanvas,
  downloadBlob,
  effectiveAspect,
  exportFilename,
  exportVideo,
  shareOrDownload,
} from '@/lib/export'
import { EMPTY_MAP_DATASETS, type MapDatasets } from '@/lib/map-data'
import { cn } from '@/lib/utils'
import { useAppStore } from '@/store/app-store'
import { useUiStore } from '@/store/ui-store'
import { FONT_OPTIONS, type ExportFormat } from '@/types'

interface EditorProps {
  datasets: MapDatasets | null
  renderKey?: number
  onRetryMap?: () => void
}

export function Editor({ datasets, renderKey = 0, onRetryMap }: EditorProps) {
  const media = useAppStore((s) => s.media)
  const info = useAppStore((s) => s.info)
  const style = useAppStore((s) => s.style)
  const crop = useAppStore((s) => s.crop)
  const settings = useAppStore((s) => s.settings)
  const openSheet = useUiStore((s) => s.openSheet)
  const sheet = useUiStore((s) => s.sheet)
  const isMobile = useIsMobile()
  const shortLandscape = useIsShortLandscape()
  const coarse = useIsCoarsePointer()
  const compactSide = shortLandscape && coarse
  const sideSheet = !isMobile || shortLandscape
  const sheetShift = sideSheet && sheet
    ? compactSide
      ? 'pr-[min(20rem,70vw)]'
      : 'md:pr-[25rem]'
    : ''
  const stageShift = sideSheet && sheet
    ? compactSide
      ? 'right-[min(20rem,70vw)] md:right-[min(20rem,70vw)]'
      : 'md:right-[25rem]'
    : ''
  const compactSheetOpen = isMobile && !shortLandscape && (sheet === 'style' || sheet === 'crop')
  const { image, video, error: mediaError } = useMediaElement(media)
  useEffect(() => {
    if (mediaError) toast.error(mediaError)
  }, [mediaError])
  const [exporting, setExporting] = useState(false)
  const abortRef = useRef<AbortController | null>(null)
  const [progress, setProgress] = useState(0)

  if (!media) return null

  const asset = media
  const mapDatasets = datasets ?? EMPTY_MAP_DATASETS
  const source = asset.type === 'image' ? image : video
  const aspect = effectiveAspect(crop, asset.width, asset.height)

  async function loadImage(url: string): Promise<HTMLImageElement> {
    const img = new Image()
    img.src = url
    await img.decode()
    return img
  }

  async function exportCurrent(format: ExportFormat, share: boolean) {
    if (asset.type === 'video' && !canExportVideo()) {
      toast.error('このブラウザは動画の書き出しに対応していません')
      return
    }
    setExporting(true)
    setProgress(0)
    const fontFamily = FONT_OPTIONS.find((f) => f.value === style.font)?.family ?? 'system-ui'
    await document.fonts?.load(`700 40px ${fontFamily}`).catch(() => undefined)
    const controller = new AbortController()
    abortRef.current = controller
    let exportUrl: string | null = null
    let exportVideoEl: HTMLVideoElement | null = null
    try {
      if (asset.type === 'video') {
        exportUrl = asset.file ? URL.createObjectURL(asset.file) : null
        const el = document.createElement('video')
        exportVideoEl = el
        el.muted = true
        el.playsInline = true
        el.preload = 'auto'
        el.style.position = 'fixed'
        el.style.left = '-10000px'
        el.style.width = '1px'
        el.style.height = '1px'
        el.style.opacity = '0'
        document.body.appendChild(el)
        const ready = new Promise<void>((resolve, reject) => {
          const timer = window.setTimeout(() => reject(new Error('動画の読み込みがタイムアウトしました')), 10_000)
          el.onloadedmetadata = () => {
            window.clearTimeout(timer)
            resolve()
          }
          el.onerror = () => {
            window.clearTimeout(timer)
            reject(new Error('動画を読み込めませんでした'))
          }
        })
        el.src = exportUrl ?? asset.url
        await ready
        const result = await exportVideo({
          video: el,
          info,
          style,
          crop,
          datasets: mapDatasets,
          onProgress: setProgress,
          signal: controller.signal,
        })
        const name = exportFilename(result.extension)
        if (share) {
          const status = await shareOrDownload(result.blob, name, result.mime)
          toast.success(status === 'shared' ? '共有メニューを開きました' : status === 'cancelled' ? '共有をキャンセルしました' : `${result.extension.toUpperCase()} を保存しました`)
        } else {
          downloadBlob(result.blob, name)
          toast.success(`${result.extension.toUpperCase()} を保存しました`)
        }
      } else {
        const img = await loadImage(asset.url)
        const canvas = compositeToCanvas({
          source: img,
          sourceWidth: img.naturalWidth,
          sourceHeight: img.naturalHeight,
          background: format === 'JPEG_95' ? '#ffffff' : null,
          info,
          style,
          crop,
          datasets: mapDatasets,
        })
        const blob = await canvasToBlob(canvas, format)
        if (!blob) throw new Error('画像を書き出せませんでした')
        const ext = format === 'PNG_LOSSLESS' ? 'png' : 'jpg'
        const mime = format === 'PNG_LOSSLESS' ? 'image/png' : 'image/jpeg'
        const name = exportFilename(ext)
        if (share) {
          const status = await shareOrDownload(blob, name, mime)
          toast.success(status === 'shared' ? '共有メニューを開きました' : status === 'cancelled' ? '共有をキャンセルしました' : `${ext.toUpperCase()} を保存しました`)
        } else {
          downloadBlob(blob, name)
          toast.success(`${ext.toUpperCase()} を保存しました`)
        }
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        toast.info('書き出しをキャンセルしました')
      } else {
        console.error(err)
        toast.error(err instanceof Error ? err.message : '書き出しに失敗しました')
      }
    } finally {
      exportVideoEl?.remove()
      if (exportUrl) URL.revokeObjectURL(exportUrl)
      abortRef.current = null
      setExporting(false)
      setProgress(0)
    }
  }

  return (
    <div className={cn('flex min-h-0 flex-1 flex-col', compactSide && sheet ? 'pb-0' : 'pb-24')}>
      <RouteChip info={info} onClick={() => openSheet('info')} className={sheetShift} />
      {!datasets && (
        <div className="mx-auto mt-1 flex items-center gap-2 rounded-full border border-border/70 bg-card/80 px-3 py-1 text-xs text-muted-foreground">
          地図データを読み込めませんでした
          {onRetryMap && (
            <Button variant="ghost" size="sm" className="px-3 rounded-full" onClick={onRetryMap}>
              再試行
            </Button>
          )}
        </div>
      )}
      <div
        className={cn(
          "relative flex min-h-0 flex-1 items-center justify-center px-3 pb-2 sm:px-6",
          sheetShift,
          compactSheetOpen &&
            (datasets
              ? "max-md:max-h-[calc(50dvh-8.5rem)]"
              : "max-md:max-h-[calc(50dvh-11rem)]"),
        )}
      >
        <PreviewStage
          key={media.url}
          media={media}
          source={source}
          aspect={aspect}
          datasets={mapDatasets}
          renderKey={renderKey}
          className={stageShift}
        />
      </div>
      {!compactSheetOpen && (
        <div className={cn("hidden flex-col items-center gap-2 px-6 pb-4 text-center [@media(min-height:520px)]:flex", sheetShift)}>
          <p className="text-xs leading-5 text-muted-foreground">
            テキスト・地図をドラッグ / 右上のハンドルでサイズ変更
          </p>
          <p className="text-xs leading-5 text-muted-foreground">
            元画像 {asset.width} × {asset.height}
          </p>
        </div>
      )}
      {!(compactSide && sheet) && (
        <FloatingToolbar
          isVideo={asset.type === 'video'}
          onOpenInfo={() => openSheet('info')}
          onOpenStyle={() => openSheet('style')}
          onOpenCrop={() => openSheet('crop')}
          onShare={() => void exportCurrent(settings.exportFormat, true)}
          onSave={() => void exportCurrent(settings.exportFormat, false)}
          onSaveFormat={(format) => void exportCurrent(format, false)}
          onSaveVideo={() => void exportCurrent(settings.exportFormat, false)}
          onResetLayout={() => useAppStore.getState().resetLayout()}
          disabled={exporting}
          className={sheetShift}
        />
      )}
      <InfoSheet />
      <StyleSheet />
      <CropSheet />
      {exporting && (
        <div role="status" aria-live="polite" aria-busy="true" className="fixed inset-0 z-[70] flex items-center justify-center bg-background/70 backdrop-blur-sm">
          <Card className="w-[min(20rem,calc(100vw-2rem))] items-center gap-4 rounded-3xl border-0 p-6 shadow-2xl">
            <Loader2 className="size-10 animate-spin text-primary" />
            <div className="w-full space-y-2 text-center">
              <p className="text-sm font-semibold">
                {asset.type === 'video' ? '動画を書き出し中…' : '画像を保存中…'}
              </p>
              {asset.type === 'video' && <Progress value={progress * 100} />}
              <p className="text-xs leading-5 text-muted-foreground">
                このままタブを閉じずにお待ちください。タブを切り替えると動画が乱れる場合があります。
              </p>
              {asset.type === 'video' && (
                <Button variant="outline" size="sm" className="rounded-full" onClick={() => abortRef.current?.abort()}>
                  キャンセル
                </Button>
              )}
            </div>
          </Card>
        </div>
      )}
    </div>
  )
}
