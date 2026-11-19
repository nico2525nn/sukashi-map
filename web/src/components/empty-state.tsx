import { Camera, Images, Map } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'

interface EmptyStateProps {
  onPick: () => void
  onCamera: () => void
}

export function EmptyState({ onPick, onCamera }: EmptyStateProps) {
  return (
    <div className="flex flex-1 items-center justify-center px-4 py-10">
      <Card className="w-full max-w-md items-center gap-0 rounded-[2rem] border-0 bg-card/80 p-5 text-center shadow-xl shadow-primary/5 ring-1 ring-border/60 sm:p-7">
        <div className="mb-5 flex size-16 items-center justify-center rounded-[1.35rem] bg-primary/12 text-primary">
          <Map className="size-8" strokeWidth={2.2} />
        </div>
        <h2 className="text-2xl font-bold tracking-tight">写真や動画を選択</h2>
        <p className="mt-2 max-w-[20rem] text-sm leading-relaxed text-pretty text-muted-foreground">
          フライト・鉄道・移動ルートや撮影地の地図透かしを合成します
        </p>
        <div className="mt-7 flex w-full flex-col gap-3">
          <Button onClick={onPick} size="lg" className="h-12 w-full rounded-full text-sm font-semibold">
            <Images className="size-5" />
            写真・動画を選ぶ
          </Button>
          <Button
            onClick={onCamera}
            variant="secondary"
            size="lg"
            className="h-11 w-full rounded-full text-sm font-semibold"
          >
            <Camera className="size-5" />
            カメラで撮影する
          </Button>
        </div>
        <p className="mt-5 text-[11px] text-muted-foreground">
          画像はブラウザ内だけで処理されます。撮影地の座標のみ、地名検索・経路取得のため公開APIに送信されます。
        </p>
      </Card>
    </div>
  )
}
