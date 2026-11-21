import { useState } from 'react'
import { Check, Pipette, Plus, RotateCcw, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { SectionCard } from '@/components/section-card'
import { Segmented } from '@/components/segmented'
import { SheetShell } from '@/components/sheets/sheet-shell'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Slider } from '@/components/ui/slider'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { useDialogFocusReturn } from '@/hooks/use-dialog-focus'
import { useAppStore } from '@/store/app-store'
import { useUiStore } from '@/store/ui-store'
import {
  COLOR_SWATCHES,
  CORNER_LABELS,
  FONT_OPTIONS,
  type OverlayCorner,
} from '@/types'

export function StyleSheet() {
  const sheet = useUiStore((s) => s.sheet)
  const closeSheet = useUiStore((s) => s.closeSheet)
  const open = sheet === 'style'
  const style = useAppStore((s) => s.style)
  const updateStyle = useAppStore((s) => s.updateStyle)
  const resetLayout = useAppStore((s) => s.resetLayout)
  const presets = useAppStore((s) => s.presets)
  const savePreset = useAppStore((s) => s.savePreset)
  const deletePreset = useAppStore((s) => s.deletePreset)
  const [presetOpen, setPresetOpen] = useState(false)
  const [presetName, setPresetName] = useState('')
  const presetFocus = useDialogFocusReturn()

  return (
    <>
      <SheetShell
        open={open}
        onOpenChange={(next) => (next ? undefined : closeSheet())}
        title="スタイル"
        description="文字・ルート・地図の見た目"
        compact
      >
        <div className="space-y-4 pb-4">
          <SectionCard title="フォント">
            <Select
              value={style.font}
              onValueChange={(value) => updateStyle({ font: value })}
            >
              <SelectTrigger aria-label="フォント" className="w-full rounded-[20px]">
                <SelectValue placeholder="フォントを選択" />
              </SelectTrigger>
              <SelectContent>
                {FONT_OPTIONS.map((font) => (
                  <SelectItem key={font.value} value={font.value}>
                    {font.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </SectionCard>

          <SectionCard title="カラー">
            <ColorField
              label="文字色"
              value={style.textColor}
              onChange={(textColor) => updateStyle({ textColor })}
            />
            <ColorField
              label="ルート線色"
              value={style.routeColor}
              onChange={(routeColor) => updateStyle({ routeColor })}
            />
            <ColorField
              label="海岸線色"
              value={style.borderColor}
              onChange={(borderColor) => updateStyle({ borderColor })}
            />
          </SectionCard>

          <SectionCard title="表示">
            <div className="flex min-h-12 items-center justify-between gap-3">
              <p className="text-sm font-medium">地図透かしを表示</p>
              <Switch
                aria-label="地図透かしを表示"
                checked={style.showMap}
                onCheckedChange={(showMap) => updateStyle({ showMap })}
              />
            </div>
            <div className="flex min-h-12 items-center justify-between gap-3">
              <p className="text-sm font-medium">出発地の飛行機バッジ</p>
              <Switch
                aria-label="出発地の飛行機バッジ"
                checked={style.showOriginPlaneBadge}
                onCheckedChange={(showOriginPlaneBadge) => updateStyle({ showOriginPlaneBadge })}
              />
            </div>
          </SectionCard>

          <SectionCard title="サイズ（%）・配置">
            <p className="text-sm font-medium">テキストの位置</p>
            <Segmented
              ariaLabel="テキストの基準位置"
              options={(Object.keys(CORNER_LABELS) as OverlayCorner[]).map((corner) => ({
                value: corner,
                label: CORNER_LABELS[corner],
              }))}
              value={style.textCorner}
              onValueChange={(corner) =>
                updateStyle({ textCorner: corner, textFreePos: null })
              }
            />
            <p className="text-sm font-medium">地図の位置</p>
            <Segmented
              ariaLabel="地図の基準位置"
              options={(Object.keys(CORNER_LABELS) as OverlayCorner[]).map((corner) => ({
                value: corner,
                label: CORNER_LABELS[corner],
              }))}
              value={style.mapCorner}
              onValueChange={(corner) =>
                updateStyle({ mapCorner: corner, mapFreePos: null })
              }
            />
            <ScaleSlider
              label="文字全体"
              value={style.textScale}
              min={0.5}
              max={2.5}
              percent
              onChange={(textScale) => updateStyle({ textScale })}
            />
            <ScaleSlider
              label="タイトル"
              value={style.titleScale}
              min={0.5}
              max={2.5}
              percent
              onChange={(titleScale) => updateStyle({ titleScale })}
            />
            <ScaleSlider
              label="サブタイトル"
              value={style.subtitleScale}
              min={0.5}
              max={2.5}
              percent
              onChange={(subtitleScale) => updateStyle({ subtitleScale })}
            />
            <ScaleSlider
              label="座標・地名"
              value={style.locationScale}
              min={0.5}
              max={2.5}
              percent
              onChange={(locationScale) => updateStyle({ locationScale })}
            />
            <ScaleSlider
              label="日付"
              value={style.dateScale}
              min={0.5}
              max={2.5}
              percent
              onChange={(dateScale) => updateStyle({ dateScale })}
            />
            <ScaleSlider
              label="地図"
              value={style.mapBoxScale}
              min={0.5}
              max={2.5}
              percent
              onChange={(mapBoxScale) => updateStyle({ mapBoxScale })}
            />
            <ScaleSlider
              label="地図内部ズーム"
              value={style.mapInternalZoom}
              min={0.5}
              max={5}
              onChange={(mapInternalZoom) => updateStyle({ mapInternalZoom })}
            />
            <Button variant="outline" className="w-full rounded-[20px]" onClick={resetLayout}>
              <RotateCcw className="size-4" />
              位置・サイズをリセット
            </Button>
          </SectionCard>

          <SectionCard
            title="プリセット"
            trailing={
              <Button
                variant="ghost"
                size="sm"
                className="rounded-full"
                onClick={(event) => {
                  presetFocus.capture(event.currentTarget)
                  setPresetName('')
                  setPresetOpen(true)
                }}
              >
                <Plus className="size-4" />
                保存
              </Button>
            }
          >
            <div className="grid grid-cols-2 gap-2">
              {presets.map((preset) => {
                const active = JSON.stringify(preset.style) === JSON.stringify(style)
                return (
                  <div
                    key={preset.id}
                    data-testid="preset-card" className="relative rounded-[28px] border border-border/70 bg-background/60 p-3"
                  >
                    <button
                      type="button"
                      className="w-full text-left"
                      aria-pressed={active}
                      onClick={() => updateStyle(preset.style)}
                    >
                      <span className="mb-2 block text-sm leading-5 font-semibold text-foreground">
                        {preset.name}
                      </span>
                      <span className="flex gap-1">
                        <i className="size-3 rounded-full" style={{ background: preset.style.textColor }} />
                        <i className="size-3 rounded-full" style={{ background: preset.style.routeColor }} />
                        <i className="size-3 rounded-full" style={{ background: preset.style.borderColor }} />
                      </span>
                    </button>
                    {active && <Check className="absolute top-2 right-2 size-4 text-primary" />}
                    {preset.id.startsWith('custom_') && (
                      <Button
                        variant="ghost"
                        size="sm"
                        className="mt-2 h-11 w-full justify-start rounded-xl px-2 text-muted-foreground"
                        onClick={() => deletePreset(preset.id)}
                        aria-label={`${preset.name}を削除`}
                      >
                        <Trash2 className="size-4" />
                        削除
                      </Button>
                    )}
                  </div>
                )
              })}
            </div>
          </SectionCard>
        </div>
      </SheetShell>

      <Dialog open={presetOpen} onOpenChange={setPresetOpen}>
        <DialogContent className="rounded-[32px]" onCloseAutoFocus={presetFocus.onCloseAutoFocus}>
          <DialogHeader>
            <DialogTitle>プリセットを保存</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1.5">
              <Label htmlFor="preset-name">名前</Label>
              <Input
                id="preset-name"
                value={presetName}
                onChange={(e) => setPresetName(e.target.value)}
                placeholder="My Preset"
                autoFocus
              />
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setPresetOpen(false)}>
                キャンセル
              </Button>
              <Button
                onClick={() => {
                  savePreset(presetName)
                  setPresetOpen(false)
                  toast.success('プリセットを保存しました')
                }}
              >
                保存
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}

function ColorField({
  label,
  value,
  onChange,
}: {
  label: string
  value: string
  onChange: (value: string) => void
}) {
  async function pickFromScreen() {
    const EyeDropperCtor = (
      window as unknown as { EyeDropper?: new () => { open: () => Promise<{ sRGBHex: string }> } }
    ).EyeDropper
    if (!EyeDropperCtor) {
      toast.error('このブラウザはスポイトに対応していません')
      return
    }
    try {
      const result = await new EyeDropperCtor().open()
      onChange(result.sRGBHex)
    } catch {
      // User cancelled.
    }
  }

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium">{label}</p>
        <div className="flex items-center gap-2">
          <button
            type="button"
            className="size-11 min-h-0! rounded-full border border-border"
            style={{ background: value }}
            aria-label={`${label}のスポイト`}
            onClick={() => void pickFromScreen()}
          >
            <Pipette className="mx-auto size-3 text-white mix-blend-difference" />
          </button>
          <input
            type="color"
            value={value}
            onChange={(e) => onChange(e.target.value)}
            className="color-swatch size-11 min-h-0! cursor-pointer rounded-full border border-border/70 bg-transparent p-0"
            aria-label={`${label}を選択`}
          />
        </div>
      </div>
      <div className="flex flex-wrap gap-2">
        {COLOR_SWATCHES.map((swatch) => (
          <button
            key={swatch}
            type="button"
            className={`size-11 min-h-0! rounded-full border ${value.toLowerCase() === swatch ? 'ring-2 ring-primary ring-offset-2 ring-offset-card' : 'border-border/70'}`}
            style={{ background: swatch }}
            aria-pressed={value.toLowerCase() === swatch}
            onClick={() => onChange(swatch)}
            aria-label={`${label} ${swatch}`}
          />
        ))}
      </div>
    </div>
  )
}

function ScaleSlider({
  label,
  value,
  min,
  max,
  percent = false,
  onChange,
}: {
  label: string
  value: number
  min: number
  max: number
  percent?: boolean
  onChange: (value: number) => void
}) {
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between text-sm">
        <p className="font-medium">{label}</p>
        <span className="tabular-nums text-muted-foreground">
          {percent ? `${Math.round(value * 100)}%` : `${value.toFixed(2)}×`}
        </span>
      </div>
      <Slider
        aria-label={label}
        aria-valuetext={percent ? `${Math.round(value * 100)}%` : `${value.toFixed(2)}×`}
        value={[value]}
        min={min}
        max={max}
        step={0.01}
        onValueChange={([next]) => onChange(next)}
      />
    </div>
  )
}
