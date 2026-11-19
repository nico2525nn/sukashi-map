import type { ReactNode } from 'react'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetTitle,
} from '@/components/ui/sheet'
import { useIsMobile } from '@/hooks/use-media-query'
import { cn } from '@/lib/utils'

interface SheetShellProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  action?: ReactNode
  children: ReactNode
  className?: string
}

export function SheetShell({
  open,
  onOpenChange,
  title,
  description,
  action,
  children,
  className,
}: SheetShellProps) {
  const isMobile = useIsMobile()
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side={isMobile ? 'bottom' : 'right'}
        showCloseButton={false}
        className={cn(
          'flex flex-col gap-0 bg-card p-0',
          isMobile
            ? 'max-h-[92dvh] rounded-t-[2rem] border-t-0'
            : 'w-full border-l-0 p-0 sm:max-w-[30rem]',
          className,
        )}
      >
        <div className="shrink-0 border-b border-border/60 bg-card/95 px-4 pt-2.5 pb-2 backdrop-blur-xl sm:px-5">
          {isMobile && <div className="mx-auto mb-2 h-1 w-10 rounded-full bg-muted-foreground/25" />}
          <div className="flex items-center justify-between gap-3">
            <div className="min-w-0">
              <SheetTitle className="truncate text-lg font-bold tracking-tight">{title}</SheetTitle>
              {description && (
                <SheetDescription className="truncate text-xs">{description}</SheetDescription>
              )}
            </div>
            <div className="flex shrink-0 items-center gap-1.5">
              {action}
              <Button
                variant="ghost"
                size="icon-sm"
                className="rounded-full"
                onClick={() => onOpenChange(false)}
                aria-label="閉じる"
              >
                <X className="size-4" />
              </Button>
            </div>
          </div>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-4 py-4 sm:px-5">
          {children}
        </div>
      </SheetContent>
    </Sheet>
  )
}
