import { Camera, Images, Settings } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useAppStore } from '@/store/app-store'
import { useUiStore } from '@/store/ui-store'

interface TopBarProps {
  onPick: () => void
  onCamera: () => void
}

export function TopBar({ onPick, onCamera }: TopBarProps) {
  const media = useAppStore((s) => s.media)
  const openSheet = useUiStore((s) => s.openSheet)

  return (
    <header className="sticky top-0 z-40 border-b border-border/50 bg-background/80 pt-[env(safe-area-inset-top)] backdrop-blur-xl">
      <div
        className="mx-auto flex h-14 w-full max-w-7xl items-center justify-between sm:h-16 sm:px-6"
        style={{
          paddingLeft: 'max(0.75rem, env(safe-area-inset-left))',
          paddingRight: 'max(0.75rem, env(safe-area-inset-right))',
        }}
      >
        <div className="flex min-w-0 flex-1 items-center gap-1">
          {media && (
            <>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button variant="ghost" size="icon-lg" onClick={onPick} aria-label="別の写真・動画を選択">
                    <Images className="size-5" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>別の写真・動画を選択</TooltipContent>
              </Tooltip>
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button variant="ghost" size="icon-lg" onClick={onCamera} aria-label="カメラ">
                    <Camera className="size-5" />
                  </Button>
                </TooltipTrigger>
                <TooltipContent>カメラ</TooltipContent>
              </Tooltip>
            </>
          )}
        </div>
        <div className="flex flex-1 items-center justify-center">
          <h1 className="whitespace-nowrap text-base font-bold tracking-tight sm:text-xl">Sukashi Map</h1>
        </div>
        <div className="flex flex-1 items-center justify-end">
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon-lg"
                onClick={() => openSheet('settings')}
                aria-label="設定"
              >
                <Settings className="size-5" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>設定</TooltipContent>
          </Tooltip>
        </div>
      </div>
    </header>
  )
}
