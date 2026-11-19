import { Globe2, Info, Moon, Sun, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { SectionCard } from '@/components/section-card'
import { Segmented } from '@/components/segmented'
import { SheetShell } from '@/components/sheets/sheet-shell'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { useAppStore } from '@/store/app-store'
import { useUiStore } from '@/store/ui-store'
import type { ExportFormat, ThemeMode } from '@/types'

export function SettingsSheet() {
  const sheet = useUiStore((s) => s.sheet)
  const closeSheet = useUiStore((s) => s.closeSheet)
  const open = sheet === 'settings'
  const settings = useAppStore((s) => s.settings)
  const updateSettings = useAppStore((s) => s.updateSettings)
  const clearHistory = useAppStore((s) => s.clearHistory)

  return (
    <SheetShell
      open={open}
      onOpenChange={(next) => (next ? undefined : closeSheet())}
      title="設定"
      description="Sukashi Map Web"
    >
      <div className="space-y-4 pb-4">
        <SectionCard title="外観">
          <Segmented
            options={[
              { value: 'SYSTEM', label: 'システム', icon: <Globe2 className="size-3.5" /> },
              { value: 'LIGHT', label: 'ライト', icon: <Sun className="size-3.5" /> },
              { value: 'DARK', label: 'ダーク', icon: <Moon className="size-3.5" /> },
            ]}
            value={settings.themeMode}
            onValueChange={(themeMode) => updateSettings({ themeMode: themeMode as ThemeMode })}
          />
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-medium">ダイナミックカラー</p>
              <p className="text-xs text-muted-foreground">アクセントカラーを端末に合わせます</p>
            </div>
            <Switch
              checked={settings.dynamicColor}
              onCheckedChange={(dynamicColor) => updateSettings({ dynamicColor })}
            />
          </div>
        </SectionCard>

        <SectionCard title="書き出し">
          <p className="text-sm font-medium">デフォルト形式</p>
          <Segmented
            options={[
              { value: 'JPEG_95', label: 'JPEG 95%' },
              { value: 'PNG_LOSSLESS', label: 'PNG 無劣化' },
            ]}
            value={settings.exportFormat}
            onValueChange={(exportFormat) =>
              updateSettings({ exportFormat: exportFormat as ExportFormat })
            }
          />
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-medium">HDR情報を保持</p>
              <p className="text-xs text-muted-foreground">
                ブラウザではPNG/JPEGのHDRゲインマップ保持は未対応です
              </p>
            </div>
            <Switch checked={settings.preserveHdr} disabled />
          </div>
          <p className="text-xs text-muted-foreground">
            動画は WebM（VP9/VP8）で書き出します。MP4書き出しはブラウザの対応状況に依存します。
          </p>
        </SectionCard>

        <SectionCard title="位置情報">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-medium">カメラ撮影時に現在地を使う</p>
              <p className="text-xs text-muted-foreground">
                写真にGPSがない場合、端末の最終位置を撮影地にします
              </p>
            </div>
            <Switch
              checked={settings.autoLocationEnabled}
              onCheckedChange={(autoLocationEnabled) => updateSettings({ autoLocationEnabled })}
            />
          </div>
          <p className="text-xs text-muted-foreground">
            写真のEXIF位置情報はブラウザが取得できる場合のみ読み取ります。取得できない場合は透かし情報シートから手動設定できます。
          </p>
        </SectionCard>

        <SectionCard title="データ">
          <Button
            variant="outline"
            className="w-full rounded-xl text-destructive"
            onClick={() => {
              clearHistory()
              toast.success('履歴を削除しました')
            }}
          >
            <Trash2 className="size-4" />
            透かし履歴をすべて削除
          </Button>
        </SectionCard>

        <SectionCard title="このアプリについて">
          <div className="flex items-start gap-3">
            <Info className="mt-0.5 size-4 shrink-0 text-primary" />
            <div className="space-y-1 text-xs leading-relaxed text-muted-foreground">
              <p>
                Sukashi Map Web v0.1.0 — 写真・動画にフライト、鉄道、移動ルート、スポットの透かしを合成します。
              </p>
              <p>
                地図: 国土交通省 国土数値情報 (N02/N03)、国土地理院、Natural Earth、U.S. Census Bureau
              </p>
              <p>地名検索: Photon / © OpenStreetMap contributors</p>
              <p>経路: Project OSRM / © OpenStreetMap contributors</p>
              <p>逆ジオコーディング: Nominatim / © OpenStreetMap contributors</p>
              <p>すべての画像処理はブラウザ内で完結します。</p>
            </div>
          </div>
        </SectionCard>
      </div>
    </SheetShell>
  )
}
