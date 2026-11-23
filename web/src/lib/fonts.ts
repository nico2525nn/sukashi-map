import { FONT_OPTIONS, type TransportInfo } from '@/types'

const dynamicCss: Partial<Record<string, () => Promise<unknown>>> = {
  'kaisei-tokumin': () => import('../generated/font-faces-kaisei-tokumin.css'),
  'noto-sans-jp': () => import('../generated/font-faces-noto-sans-jp.css'),
  'noto-serif-jp': () => import('../generated/font-faces-noto-serif-jp.css'),
}

/** All text that may be drawn into the watermark; font subsets are loaded for these glyphs. */
export function overlayText(info: TransportInfo): string {
  return [
    info.airlineCode,
    info.flightNumber,
    info.registrationNumber,
    info.departureAirportCode,
    info.departureAirportName,
    info.arrivalAirportCode,
    info.arrivalAirportName,
    info.railwayLineName,
    info.trainName,
    info.vehicleModel,
    info.departureStationName,
    info.arrivalStationName,
    info.transportName,
    info.departurePlaceName,
    info.destinationPlaceName,
    info.spotLine1,
    info.spotLine2,
    info.spotLine3,
    info.photoLocationName,
    String(info.year),
    String(info.month),
    String(info.day),
    String(info.hour),
    String(info.minute),
  ]
    .filter(Boolean)
    .join(' ')
}

export async function loadOverlayFont(font: string, text: string): Promise<void> {
  const family = FONT_OPTIONS.find((option) => option.value === font)?.family ?? 'system-ui'
  await dynamicCss[font]?.()
  if (typeof document !== 'undefined' && document.fonts) {
    await document.fonts.load(`700 40px ${family}`, text)
  }
}
