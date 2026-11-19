import { useState } from 'react'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { FloatingToolbar } from '@/components/editor/floating-toolbar'
import { PreviewStage } from '@/components/editor/preview-stage'
import { RouteChip } from '@/components/editor/route-chip'
import { CropSheet } from '@/components/sheets/crop-sheet'
import { InfoSheet } from '@/components/sheets/info-sheet'
import { SettingsSheet } from '@/components/sheets/settings-sheet'
import { StyleSheet } from '@/components/sheets/style-sheet'
import { Card } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { useMediaElement } from '@/hooks/use-media-element'
import {
  canvasToBlob,
  compositeToCanvas,
  downloadBlob,
  effectiveAspect,
  exportFilename,
  exportVideo,
  shareOrDownload,
} from '@/lib/export'
import type { MapDatasets } from '@/lib/map-data'
import { useAppStore } from '@/store/app-store'
import { useUiStore } from '@/store/ui-store'
import type { ExportFormat } from '@/types'

interface EditorProps {
  datasets: MapDatasets | null
  renderKey?: number
}

export function Editor({ datasets, renderKey = 0 }: EditorProps) {
  const media = useAppStore((s) => s.media)
  const info = useAppStore((s) => s.info)
  const style = useAppStore((s) => s.style)
  const crop = useAppStore((s) => s.crop)
  const settings = useAppStore((s) => s.settings)
  const openSheet = useUiStore((s) => s.openSheet)
  const { image, video } = useMediaElement(media)
  const [exporting, setExporting] = useState(false)
  const [progress, setProgress] = useState(0)

  if (!media) return null

  const asset = media
  const source = asset.type === 'image' ? image : video
  const aspect = Math.min(
    3.2,
    Math.max(0.3, effectiveAspect(crop, asset.width, asset.height)),
  )

  async function loadImage(url: string): Promise<HTMLImageElement> {
    const img = new Image()
    img.src = url
    await img.decode()
    return img
  }

  async function exportCurrent(format: ExportFormat, share: boolean) {
    if (!datasets) {
      toast.error('地図データを読み込み中です。少し待ってから再度お試しください')
      return
    }
    setExporting(true)
    setProgress(0)
    try {
      if (asset.type === 'video') {
        const el = document.createElement('video')
        el.src = asset.url
        el.muted = true
        el.playsInline = true
        el.preload = 'auto'
        await new Promise<void>((resolve, reject) => {
          el.onloadedmetadata = () => resolve()
          el.onerror = () => reject(new Error('動画を読み込めませんでした'))
        })
        const blob = await exportVideo({
          video: el,
          info,
          style,
          crop,
          datasets,
          onProgress: setProgress,
        })
        const name = exportFilename('webm')
        if (share) await shareOrDownload(blob, name, 'video/webm')
        else downloadBlob(blob, name)
        toast.success(share ? '共有メニューを開きました' : 'WebM を保存しました')
      } else {
        const img = await loadImage(asset.url)
        const canvas = compositeToCanvas({
          source: img,
          sourceWidth: img.naturalWidth,
          sourceHeight: img.naturalHeight,
          info,
          style,
          crop,
          datasets,
        })
        const blob = await canvasToBlob(canvas, format)
        if (!blob) throw new Error('画像を書き出せませんでした')
        const ext = format === 'PNG_LOSSLESS' ? 'png' : 'jpg'
        const mime = format === 'PNG_LOSSLESS' ? 'image/png' : 'image/jpeg'
        const name = exportFilename(ext)
        if (share) await shareOrDownload(blob, name, mime)
        else downloadBlob(blob, name)
        toast.success(share ? '共有メニューを開きました' : `${ext.toUpperCase()} を保存しました`)
      }
    } catch (err) {
      console.error(err)
      toast.error(err instanceof Error ? err.message : '書き出しに失敗しました')
    } finally {
      setExporting(false)
      setProgress(0)
    }
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col pb-28">
      <RouteChip info={info} onClick={() => openSheet('info')} />
      <div className="flex min-h-0 flex-1 items-center justify-center px-3 pb-2 sm:px-6">
        <PreviewStage key={media.url} media={media} source={source} aspect={aspect} datasets={datasets} renderKey={renderKey} />
      </div>
      <div className="flex flex-col items-center gap-1 px-4 pb-3 text-center">
        <p className="text-[11px] text-muted-foreground sm:text-xs">
          テキスト・地図をドラッグ / 右上のハンドルでサイズ変更
        </p>
        <p className="text-[10px] text-muted-foreground/70">
          元画像 {asset.width} × {asset.height}
        </p>
      </div>
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
      />
      <InfoSheet />
      <StyleSheet />
      <CropSheet />
      <SettingsSheet />
      {exporting && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center bg-background/70 backdrop-blur-sm">
          <Card className="w-[min(20rem,calc(100vw-2rem))] items-center gap-4 rounded-3xl border-0 p-6 shadow-2xl">
            <Loader2 className="size-10 animate-spin text-primary" />
            <div className="w-full space-y-2 text-center">
              <p className="text-sm font-semibold">
                {asset.type === 'video' ? '動画を書き出し中…' : '画像を保存中…'}
              </p>
              {asset.type === 'video' && <Progress value={progress * 100} />}
              <p className="text-xs text-muted-foreground">
                このままタブを閉じずにお待ちください
              </p>
            </div>
          </Card>
        </div>
      )}
    </div>
  )
}
