import type { ReactNode } from 'react'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'

interface SectionCardProps {
  title: string
  trailing?: ReactNode
  children: ReactNode
  className?: string
  contentClassName?: string
}

export function SectionCard({
  title,
  trailing,
  children,
  className,
  contentClassName,
}: SectionCardProps) {
  return (
    <Card
      className={cn(
        'gap-0 rounded-3xl border-0 bg-card/80 p-0 shadow-none ring-1 ring-border/60',
        className,
      )}
    >
      <div className="flex items-center justify-between gap-2 px-4 pt-4 pb-3">
        <h3 className="text-sm font-semibold tracking-tight">{title}</h3>
        {trailing}
      </div>
      <div className={cn('flex flex-col gap-3 px-4 pb-4', contentClassName)}>{children}</div>
    </Card>
  )
}
