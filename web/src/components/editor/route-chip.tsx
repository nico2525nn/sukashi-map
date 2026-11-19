import { Car, MapPin, Plane, TrainFront } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { summaryLabel, type TransportInfo } from '@/types'

export function modeIcon(mode: TransportInfo['mode'], className = 'size-4') {
  switch (mode) {
    case 'AIRPLANE':
      return <Plane className={className} />
    case 'TRAIN':
      return <TrainFront className={className} />
    case 'OTHER':
      return <Car className={className} />
    case 'SPOT':
      return <MapPin className={className} />
  }
}

interface RouteChipProps {
  info: TransportInfo
  onClick: () => void
  computing?: boolean
}

export function RouteChip({ info, onClick }: RouteChipProps) {
  return (
    <div className="flex min-h-10 items-center justify-center px-2">
      <Button
        variant="outline"
        onClick={onClick}
        className="max-w-[92vw] gap-2 rounded-full border-border/70 bg-card/70 px-4 text-xs font-semibold shadow-sm sm:text-sm"
      >
        {modeIcon(info.mode, 'size-4 text-primary')}
        <span className="truncate">{summaryLabel(info)}</span>
      </Button>
    </div>
  )
}
