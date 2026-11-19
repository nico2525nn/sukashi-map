import { Camera, Images, Map } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'

interface EmptyStateProps {
  onPick: () => void
  onCamera: () => void
}

export function EmptyState({ onPick, onCamera }: EmptyStateProps) {
  return (
    <div className="flex flex-1 items-center justify-center px-4 py-8 [@media(max-height:520px)]:py-4">
      <Card
        data-testid="empty-card"
        className="w-full max-w-md items-center gap-0 rounded-[2rem] border-0 bg-card/80 p-6 text-center shadow-xl shadow-primary/5 ring-1 ring-border/60 [@media(max-height:520px)]:p-4 sm:p-8"
      >
        <div className="mb-5 flex size-16 items-center justify-center rounded-[20px] bg-primary/12 text-primary [@media(max-height:520px)]:mb-2 [@media(max-height:520px)]:size-10">
          <Map className="size-8 [@media(max-height:520px)]:size-5" strokeWidth={2.2} />
        </div>
        <h2 className="text-[24px] leading-8 font-bold tracking-tight [@media(max-height:520px)]:text-[20px] [@media(max-height:520px)]:leading-6">
          写真や動画を選択
        </h2>
        <p className="mt-3 max-w-[22rem] text-sm leading-5 text-pretty text-muted-foreground [@media(max-height:520px)]:hidden">
          フライト・鉄道・移動ルートや撮影地の地図透かしを合成します
        </p>
        <div className="mt-6 flex w-full flex-col gap-4 [@media(max-height:520px)]:mt-3 [@media(max-height:520px)]:flex-row [@media(max-height:520px)]:gap-2">
          <Button
            onClick={onPick}
            size="lg"
            className="h-12 w-full rounded-full text-sm font-semibold [@media(max-height:520px)]:h-11 [@media(max-height:520px)]:flex-1 [@media(max-height:520px)]:px-2 [@media(max-height:520px)]:text-xs"
          >
            <Images className="size-5 [@media(max-height:520px)]:size-4" />
            写真・動画を選ぶ
          </Button>
          <Button
            onClick={onCamera}
            variant="secondary"
            size="lg"
            className="h-11 w-full rounded-full text-sm font-semibold [@media(max-height:520px)]:flex-1 [@media(max-height:520px)]:px-2 [@media(max-height:520px)]:text-xs"
          >
            <Camera className="size-5 [@media(max-height:520px)]:size-4" />
            カメラで撮影する
          </Button>
        </div>
        <p className="mt-5 text-xs leading-5 text-muted-foreground [@media(max-height:520px)]:hidden">
          画像はブラウザ内だけで処理されます。撮影地の座標のみ、地名検索・経路取得のため公開APIに送信されます。
        </p>
      </Card>
    </div>
  )
}
