import { useEffect, useState } from 'react'
import { Check } from 'lucide-react'
import { SectionCard } from '@/components/section-card'
import { Segmented } from '@/components/segmented'
import { SheetShell } from '@/components/sheets/sheet-shell'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Slider } from '@/components/ui/slider'
import { Switch } from '@/components/ui/switch'
import { useAppStore } from '@/store/app-store'
import { useUiStore } from '@/store/ui-store'
import {
  ASPECT_LABELS,
  type CropAspectPreset,
  type CropFrameSettings,
} from '@/types'

export function CropSheet() {
  const sheet = useUiStore((s) => s.sheet)
  const closeSheet = useUiStore((s) => s.closeSheet)
  const open = sheet === 'crop'
  const crop = useAppStore((s) => s.crop)
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
        <SectionCard title="アスペクト比">
          <Segmented
            options={(Object.keys(ASPECT_LABELS) as CropAspectPreset[]).map((preset) => ({
              value: preset,
              label: ASPECT_LABELS[preset],
            }))}
            value={draft.aspectPreset}
            onValueChange={(aspectPreset) => update({ aspectPreset })}
            itemClassName="text-[10px] sm:text-xs"
          />
          {draft.aspectPreset !== 'ORIGINAL' && draft.aspectPreset !== 'FREE' && draft.aspectPreset !== 'RATIO_1_1' && (
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-medium">横向き</p>
              <Switch
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
              className="h-8 w-16 cursor-pointer p-1"
            />
          </div>
          <div className="flex flex-wrap gap-1.5">
            {['#ffffff', '#000000', '#f5f5f0', '#1a1a1a', '#e7dfd2', '#4e5a8a'].map((c) => (
              <button
                key={c}
                type="button"
                className={`size-7 rounded-full border ${draft.frameColor === c ? 'ring-2 ring-primary ring-offset-2 ring-offset-card' : 'border-border/70'}`}
                style={{ background: c }}
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
