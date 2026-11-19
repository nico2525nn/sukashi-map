import type { ReactNode } from 'react'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetTitle,
} from '@/components/ui/sheet'
import { useIsCoarsePointer, useIsMobile, useIsShortLandscape } from '@/hooks/use-media-query'
import { useUiStore } from '@/store/ui-store'
import { cn } from '@/lib/utils'

interface SheetShellProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  action?: ReactNode
  children: ReactNode
  className?: string
  compact?: boolean
}

export function SheetShell({
  open,
  onOpenChange,
  title,
  description,
  action,
  children,
  className,
  compact,
}: SheetShellProps) {
  const isMobile = useIsMobile()
  const shortLandscape = useIsShortLandscape()
  const coarse = useIsCoarsePointer()
  const compactSide = shortLandscape && coarse
  const sideSheet = !isMobile || shortLandscape
  const sheetOpener = useUiStore((state) => state.sheetOpener)
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side={sideSheet ? 'right' : 'bottom'}
        showCloseButton={false}
        style={compactSide ? { width: 'min(20rem, 70vw)' } : undefined}
        onCloseAutoFocus={(event) => {
          if (!sheetOpener) return
          const label = sheetOpener.label
          if (sheetOpener.element.isConnected) {
            event.preventDefault()
            sheetOpener.element.focus()
            return
          }
          if (!label) return
          event.preventDefault()
          const focusFallback = (attempt = 0) => {
            const next = [...document.querySelectorAll<HTMLElement>('[aria-label]')].find(
              (element) =>
                element.getAttribute('aria-label') === label &&
                element.getBoundingClientRect().width > 0,
            )
            if (next) {
              next.focus()
              return
            }
            if (attempt < 12) window.setTimeout(() => focusFallback(attempt + 1), 50)
          }
          window.setTimeout(() => focusFallback(), 50)
        }}
        className={cn(
          'flex flex-col gap-0 bg-card p-0',
          sideSheet
            ? compactSide
              ? 'border-l-0 p-0'
              : 'w-full border-l-0 p-0 sm:max-w-[25rem]!'
            : compact
              ? 'max-h-[50dvh] rounded-t-[2rem] border-t-0'
              : 'max-h-[92dvh] rounded-t-[2rem] border-t-0',
          className,
        )}
      >
        <div data-testid="sheet-header-bar" className="safe-x shrink-0 border-b border-border/60 bg-card/95 pt-4 pb-3 backdrop-blur-xl">
          {!sideSheet && <div className="mx-auto mb-2 h-1 w-10 rounded-full bg-muted-foreground/25" />}
          <div data-testid="sheet-title-row" className="flex items-center justify-between gap-3">
            <div className="min-w-0">
              <SheetTitle className="line-clamp-2 break-words text-[22px] leading-7 font-bold tracking-tight">{title}</SheetTitle>
              {description && (
                <SheetDescription className="truncate text-xs leading-4">{description}</SheetDescription>
              )}
            </div>
            <div className="flex shrink-0 items-center gap-2">
              {action}
              <Button
                variant="ghost"
                size="icon"
                className="size-11 rounded-full"
                onClick={() => onOpenChange(false)}
                aria-label="閉じる"
              >
                <X className="size-4" />
              </Button>
            </div>
          </div>
        </div>
        <div data-testid="sheet-body" className="safe-x min-h-0 flex-1 overflow-y-auto overscroll-contain pt-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
          {children}
        </div>
      </SheetContent>
    </Sheet>
  )
}
