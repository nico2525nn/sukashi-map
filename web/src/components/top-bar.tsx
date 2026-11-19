import { Camera, Images, Settings } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useAppStore } from '@/store/app-store'
import { useUiStore } from '@/store/ui-store'
import { useIsCoarsePointer, useIsMobile, useIsShortLandscape } from '@/hooks/use-media-query'
import { cn } from '@/lib/utils'

interface TopBarProps {
  onPick: () => void
  onCamera: () => void
}

export function TopBar({ onPick, onCamera }: TopBarProps) {
  const media = useAppStore((s) => s.media)
  const openSheet = useUiStore((s) => s.openSheet)
  const sheet = useUiStore((s) => s.sheet)
  const isMobile = useIsMobile()
  const shortLandscape = useIsShortLandscape()
  const coarse = useIsCoarsePointer()
  const compactSideOpen = shortLandscape && coarse && sheet != null
  const sideSheetOpen = sheet != null && (!isMobile || shortLandscape)

  return (
    <header className="sticky top-0 z-40 border-b border-border/50 bg-background/80 pt-[env(safe-area-inset-top)] backdrop-blur-xl">
      <div
        data-testid="app-bar-inner"
        className="mx-auto flex h-16 w-full max-w-7xl items-center justify-between px-4 sm:px-6"
        style={{
          paddingLeft: 'max(1rem, env(safe-area-inset-left))',
          paddingRight: sideSheetOpen
            ? compactSideOpen
              ? 'calc(min(20rem, 70vw) + 1rem)'
              : 'calc(25rem + 1rem)'
            : 'max(1rem, env(safe-area-inset-right))',
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
          <h1 className={cn(
            'whitespace-nowrap text-base leading-5 font-bold tracking-tight min-[360px]:text-[22px] min-[360px]:leading-7',
            compactSideOpen && 'hidden',
          )}>Sukashi Map</h1>
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
