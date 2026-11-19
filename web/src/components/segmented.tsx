import type { ReactNode } from 'react'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { cn } from '@/lib/utils'

export interface SegmentOption<T extends string> {
  value: T
  label: string
  icon?: ReactNode
}

interface SegmentedProps<T extends string> {
  value: T
  options: readonly SegmentOption<T>[]
  onValueChange: (value: T) => void
  className?: string
  itemClassName?: string
}

export function Segmented<T extends string>({
  value,
  options,
  onValueChange,
  className,
  itemClassName,
}: SegmentedProps<T>) {
  return (
    <ToggleGroup
      type="single"
      value={value}
      onValueChange={(next) => {
        if (next) onValueChange(next as T)
      }}
      variant="outline"
      spacing={1}
      style={{ width: '100%' }}
      className={cn(
        'flex w-full items-stretch rounded-full border-0 bg-secondary/70 p-1',
        className,
      )}
    >
      {options.map((option) => (
        <ToggleGroupItem
          key={option.value}
          value={option.value}
          className={cn(
            'h-9 min-w-0 flex-1 basis-0 justify-center gap-1.5 rounded-full border-0 bg-transparent px-1.5 text-[11px] font-medium text-muted-foreground shadow-none data-[state=on]:bg-primary data-[state=on]:text-primary-foreground sm:px-2 sm:text-sm',
            itemClassName,
          )}
        >
          {option.icon}
          <span className="truncate">{option.label}</span>
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  )
}
