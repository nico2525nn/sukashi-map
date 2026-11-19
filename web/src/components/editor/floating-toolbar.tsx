import type { ReactNode } from 'react'
import {
  Crop,
  Download,
  MoreVertical,
  Palette,
  RotateCcw,
  Route,
  Share2,
  Video,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import type { ExportFormat } from '@/types'

interface FloatingToolbarProps {
  isVideo: boolean
  onOpenInfo: () => void
  onOpenStyle: () => void
  onOpenCrop: () => void
  onShare: () => void
  onSave: () => void
  onSaveFormat: (format: ExportFormat) => void
  onSaveVideo: () => void
  onResetLayout: () => void
  disabled?: boolean
  className?: string
}

export function FloatingToolbar({
  isVideo,
  onOpenInfo,
  onOpenStyle,
  onOpenCrop,
  onShare,
  onSave,
  onSaveFormat,
  onSaveVideo,
  onResetLayout,
  disabled,
  className,
}: FloatingToolbarProps) {
  return (
    <div className={`pointer-events-none fixed inset-x-0 bottom-0 z-30 flex justify-center pb-[max(1rem,env(safe-area-inset-bottom))] ${className ?? ''}`}>
      <div className="pointer-events-auto flex items-center gap-0.5 rounded-full border border-border/60 bg-card/90 p-1.5 shadow-2xl shadow-primary/10 backdrop-blur-xl sm:gap-1 sm:p-2">
        <ToolbarButton label="ルート・透かし情報" onClick={onOpenInfo} disabled={disabled}>
          <Route className="size-5" />
        </ToolbarButton>
        <ToolbarButton label="スタイル" onClick={onOpenStyle} disabled={disabled}>
          <Palette className="size-5" />
        </ToolbarButton>
        <ToolbarButton label="切り抜き・余白枠" onClick={onOpenCrop} disabled={disabled}>
          <Crop className="size-5" />
        </ToolbarButton>
        <ToolbarButton label="保存して共有" onClick={onShare} disabled={disabled}>
          <Share2 className="size-5" />
        </ToolbarButton>
        <DropdownMenu>
          <Tooltip>
            <TooltipTrigger asChild>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon-lg"
                  className="size-10 rounded-full sm:size-11"
                  aria-label="その他の保存メニュー"
                  disabled={disabled}
                >
                  <MoreVertical className="size-5" />
                </Button>
              </DropdownMenuTrigger>
            </TooltipTrigger>
            <TooltipContent>その他</TooltipContent>
          </Tooltip>
          <DropdownMenuContent align="end" side="top" className="w-56">
            {!isVideo && (
              <>
                <DropdownMenuItem onClick={() => onSaveFormat('JPEG_95')}>
                  JPEGで保存
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => onSaveFormat('PNG_LOSSLESS')}>
                  PNG（無劣化）で保存
                </DropdownMenuItem>
              </>
            )}
            {isVideo && (
              <DropdownMenuItem onClick={onSaveVideo}>
                <Video className="size-4" />
                WebM（音声付き）で保存
              </DropdownMenuItem>
            )}
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={onResetLayout}>
              <RotateCcw className="size-4" />
              透かしの位置・拡大率をリセット
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
        <div className="mx-1 h-8 w-px bg-border/70" />
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              size="icon"
              className="size-12 rounded-full shadow-lg shadow-primary/30 sm:size-14"
              onClick={onSave}
              disabled={disabled}
              aria-label="保存"
            >
              <Download className="size-5 sm:size-6" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>保存</TooltipContent>
        </Tooltip>
      </div>
    </div>
  )
}

function ToolbarButton({
  label,
  onClick,
  disabled,
  children,
}: {
  label: string
  onClick: () => void
  disabled?: boolean
  children: ReactNode
}) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button
          variant="ghost"
          size="icon-lg"
          className="size-10 rounded-full sm:size-11"
          onClick={onClick}
          disabled={disabled}
          aria-label={label}
        >
          {children}
        </Button>
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  )
}
