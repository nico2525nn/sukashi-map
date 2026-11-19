import { useEffect, useRef, useState } from 'react'
import { Check } from 'lucide-react'
import { SectionCard } from '@/components/section-card'
import { SheetShell } from '@/components/sheets/sheet-shell'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Slider } from '@/components/ui/slider'
import { Switch } from '@/components/ui/switch'
import { useAppStore } from '@/store/app-store'
import { computeCropRect, effectiveAspect } from '@/lib/export'
import { useUiStore } from '@/store/ui-store'
import {
  ASPECT_LABELS,
  type CropAspectPreset,
  type CropFrameSettings,
} from '@/types'

function aspectLabel(preset: CropAspectPreset, landscape: boolean): string {
  if (!landscape) return ASPECT_LABELS[preset]
  if (preset === 'RATIO_4_5') return '5:4'
  if (preset === 'RATIO_9_16') return '16:9'
  if (preset === 'RATIO_2_3') return '3:2'
  return ASPECT_LABELS[preset]
}

export function CropSheet() {
  const sheet = useUiStore((s) => s.sheet)
  const closeSheet = useUiStore((s) => s.closeSheet)
  const open = sheet === 'crop'
  const crop = useAppStore((s) => s.crop)
  const media = useAppStore((s) => s.media)
  const updateCrop = useAppStore((s) => s.updateCrop)
  const [draft, setDraft] = useState<CropFrameSettings>(crop)

  useEffect(() => {
    if (open) setDraft(crop)
  }, [open, crop])

  const update = (patch: Partial<CropFrameSettings>) => setDraft((d) => ({ ...d, ...patch }))

  return (
    <SheetShell
      open={open}
      onOpenChange={(next) => (next ? undefined : closeSheet())}
      title="切り抜き・余白枠"
      compact
      action={
        <Button
          size="sm"
          className="rounded-full px-4 font-semibold"
          onClick={() => {
            updateCrop(draft)
            closeSheet()
          }}
        >
          <Check className="size-4" />
          適用
        </Button>
      }
    >
      <div className="space-y-4 pb-4">
        <CropPreview media={media} draft={draft} />
        <SectionCard title="アスペクト比">
          <div className="grid grid-cols-3 gap-2">
            {(Object.keys(ASPECT_LABELS) as CropAspectPreset[]).map((preset) => (
              <Button
                key={preset}
                type="button"
                variant={draft.aspectPreset === preset ? 'default' : 'outline'}
                className="h-11 rounded-xl text-xs"
                onClick={() => update({ aspectPreset: preset })}
              >
                {aspectLabel(preset, draft.isLandscapeOrientation)}
              </Button>
            ))}
          </div>
          {draft.aspectPreset !== 'ORIGINAL' && draft.aspectPreset !== 'FREE' && draft.aspectPreset !== 'RATIO_1_1' && (
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-medium">横向き</p>
              <Switch
                aria-label="横向き"
                checked={draft.isLandscapeOrientation}
                onCheckedChange={(isLandscapeOrientation) => update({ isLandscapeOrientation })}
              />
            </div>
          )}
          {draft.aspectPreset === 'FREE' && (
            <div className="space-y-2">
              <div className="flex items-center justify-between text-sm">
                <Label>フリー比率</Label>
                <span className="tabular-nums text-muted-foreground">{draft.freeAspectRatio.toFixed(2)}</span>
              </div>
              <Slider
                aria-label="フリー比率"
                value={[draft.freeAspectRatio]}
                min={0.4}
                max={2.5}
                step={0.01}
                onValueChange={([freeAspectRatio]) => update({ freeAspectRatio })}
              />
            </div>
          )}
        </SectionCard>

        <SectionCard title="位置とズーム">
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <Label>ズーム</Label>
              <span className="tabular-nums text-muted-foreground">{draft.cropZoom.toFixed(2)}×</span>
            </div>
            <Slider
              aria-label="ズーム"
              value={[draft.cropZoom]}
              min={1}
              max={5}
              step={0.01}
              onValueChange={([cropZoom]) => update({ cropZoom })}
            />
          </div>
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <Label>横位置</Label>
              <span className="tabular-nums text-muted-foreground">{Math.round(draft.cropCenterX * 100)}%</span>
            </div>
            <Slider
              aria-label="横位置"
              value={[draft.cropCenterX]}
              min={0}
              max={1}
              step={0.01}
              onValueChange={([cropCenterX]) => update({ cropCenterX })}
            />
          </div>
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <Label>縦位置</Label>
              <span className="tabular-nums text-muted-foreground">{Math.round(draft.cropCenterY * 100)}%</span>
            </div>
            <Slider
              aria-label="縦位置"
              value={[draft.cropCenterY]}
              min={0}
              max={1}
              step={0.01}
              onValueChange={([cropCenterY]) => update({ cropCenterY })}
            />
          </div>
        </SectionCard>

        <SectionCard title="余白枠">
          <div className="space-y-2">
            <div className="flex items-center justify-between text-sm">
              <Label>枠の太さ</Label>
              <span className="tabular-nums text-muted-foreground">{Math.round(draft.frameMarginRatio * 100)}%</span>
            </div>
            <Slider
              aria-label="枠の太さ"
              value={[draft.frameMarginRatio]}
              min={0}
              max={0.49}
              step={0.01}
              onValueChange={([frameMarginRatio]) => update({ frameMarginRatio })}
            />
          </div>
          <div className="flex items-center justify-between gap-3">
            <Label htmlFor="frame-color">枠の色</Label>
            <Input
              id="frame-color"
              type="color"
              value={draft.frameColor}
              onChange={(e) => update({ frameColor: e.target.value })}
              className="color-swatch size-11 min-h-0! cursor-pointer rounded-full border border-border/70 p-0"
            />
          </div>
          <div className="flex flex-wrap gap-1.5">
            {['#ffffff', '#000000', '#f5f5f0', '#1a1a1a', '#e7dfd2', '#4e5a8a'].map((c) => (
              <button
                key={c}
                type="button"
                className={`size-11 min-h-0! rounded-full border ${draft.frameColor === c ? 'ring-2 ring-primary ring-offset-2 ring-offset-card' : 'border-border/70'}`}
                style={{ background: c }}
                aria-pressed={draft.frameColor === c}
                onClick={() => update({ frameColor: c })}
                aria-label={`枠の色 ${c}`}
              />
            ))}
          </div>
        </SectionCard>
      </div>
    </SheetShell>
  )
}

function CropPreview({
  media,
  draft,
}: {
  media: ReturnType<typeof useAppStore.getState>['media']
  draft: CropFrameSettings
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [source, setSource] = useState<HTMLImageElement | HTMLVideoElement | null>(null)

  useEffect(() => {
    setSource(null)
    if (!media) return
    if (media.type === 'image') {
      const img = new Image()
      img.onload = () => setSource(img)
      img.src = media.url
      return () => {
        img.onload = null
      }
    }
    const video = document.createElement('video')
    video.src = media.url
    video.muted = true
    video.playsInline = true
    video.preload = 'auto'
    video.onloadeddata = () => {
      video.currentTime = Math.min(0.1, video.duration || 0)
      setSource(video)
    }
    return () => {
      video.onloadeddata = null
      video.removeAttribute('src')
      video.load()
    }
  }, [media])

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas || !source) return
    const srcW = source instanceof HTMLVideoElement ? source.videoWidth : source.naturalWidth
    const srcH = source instanceof HTMLVideoElement ? source.videoHeight : source.naturalHeight
    if (!srcW || !srcH) return
    const aspect = effectiveAspect(draft, srcW, srcH)
    const outW = 640
    const outH = Math.round(outW / aspect)
    canvas.width = outW
    canvas.height = outH
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const crop = computeCropRect(srcW, srcH, draft)
    const margin = Math.min(0.49, Math.max(0, draft.frameMarginRatio))
    ctx.clearRect(0, 0, outW, outH)
    if (margin > 0.001) {
      ctx.fillStyle = draft.frameColor
      ctx.fillRect(0, 0, outW, outH)
      const innerW = outW * (1 - margin)
      const innerH = outH * (1 - margin)
      ctx.drawImage(
        source,
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
      ctx.drawImage(source, crop.x, crop.y, crop.width, crop.height, 0, 0, outW, outH)
    }
  }, [source, draft])

  return (
    <div className="flex justify-center overflow-hidden rounded-2xl border border-border/70 bg-muted">
      {source ? (
        <canvas ref={canvasRef} className="mx-auto block max-h-44 w-auto max-w-full object-contain" aria-label="切り抜きプレビュー" />
      ) : (
        <div className="flex aspect-[4/3] items-center justify-center text-xs text-muted-foreground">
          プレビューを読み込み中…
        </div>
      )}
    </div>
  )
}
