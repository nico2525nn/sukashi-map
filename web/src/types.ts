export type TransportMode = 'AIRPLANE' | 'TRAIN' | 'OTHER' | 'SPOT'
export type DateTimeDisplayMode = 'DATE_ONLY' | 'DATE_AND_TIME' | 'NONE'
export type LocationDisplayMode = 'NAME' | 'COORDINATES' | 'NONE'
export type OverlayCorner = 'TOP_LEFT' | 'TOP_RIGHT' | 'BOTTOM_LEFT' | 'BOTTOM_RIGHT'
export type ExportFormat = 'JPEG_95' | 'PNG_LOSSLESS'
export type ThemeMode = 'SYSTEM' | 'LIGHT' | 'DARK'
export type CropAspectPreset = 'ORIGINAL' | 'FREE' | 'RATIO_4_5' | 'RATIO_9_16' | 'RATIO_2_3' | 'RATIO_1_1'

export interface Coord {
  lon: number
  lat: number
}

export interface PlaceCandidate {
  name: string
  subtitle: string
  latitude: number
  longitude: number
  countryCode: string
}

export interface AirportRecord {
  iata: string
  icao: string
  name: string
  city: string
  country: string
  lat: number
  lon: number
}

export interface StationRecord {
  name: string
  line: string
  company: string
  code: string
  lat: number
  lon: number
}

export interface RailLineEntry {
  line: string
  company: string
  geometryKey?: string
  geometryPoints?: number
  geometryChains?: number
}

export interface AirlineEntry {
  code: string
  shortName: string
  fullName: string
  country?: string
}

export interface TransportInfo {
  mode: TransportMode

  airlineCode: string
  airlineDisplayName: string
  flightNumber: string
  registrationNumber: string
  departureAirportCode: string
  departureAirportName: string
  departureAirportCity: string
  departureAirportCountry: string
  departureLat: number | null
  departureLon: number | null
  arrivalAirportCode: string
  arrivalAirportName: string
  arrivalAirportCity: string
  arrivalAirportCountry: string
  arrivalLat: number | null
  arrivalLon: number | null

  isDomesticJapanRail: boolean
  railwayCompany: string
  railwayLineName: string
  trainName: string
  vehicleModel: string
  departureStationName: string
  departureStationGroupCode: string
  departureStationLat: number | null
  departureStationLon: number | null
  departureStationCountry: string
  arrivalStationName: string
  arrivalStationGroupCode: string
  arrivalStationLat: number | null
  arrivalStationLon: number | null
  arrivalStationCountry: string
  selectedViaLines: string[]
  isAutoShortestVia: boolean

  transportName: string
  departurePlaceName: string
  departurePlaceLat: number | null
  departurePlaceLon: number | null
  departurePlaceCountry: string
  destinationPlaceName: string
  destinationPlaceLat: number | null
  destinationPlaceLon: number | null
  destinationPlaceCountry: string

  spotLine1: string
  spotLine2: string
  spotLine3: string

  routePolylines: Coord[][]

  photoLat: number | null
  photoLon: number | null
  photoCountryCode: string
  photoLocationName: string
  locationDisplayMode: LocationDisplayMode

  year: number
  month: number
  day: number
  hour: number
  minute: number
  dateTimeDisplayMode: DateTimeDisplayMode
}

export interface OverlayStyle {
  font: string
  textColor: string
  routeColor: string
  borderColor: string
  showMap: boolean
  showOriginPlaneBadge: boolean
  textCorner: OverlayCorner
  mapCorner: OverlayCorner
  textFreePos: { x: number; y: number } | null
  mapFreePos: { x: number; y: number } | null
  textScale: number
  titleScale: number
  subtitleScale: number
  locationScale: number
  dateScale: number
  mapBoxScale: number
  mapInternalZoom: number
  mapInternalPanX: number
  mapInternalPanY: number
}

export interface CropFrameSettings {
  aspectPreset: CropAspectPreset
  isLandscapeOrientation: boolean
  freeAspectRatio: number
  cropCenterX: number
  cropCenterY: number
  cropZoom: number
  frameMarginRatio: number
  frameColor: string
}

export interface StylePreset {
  id: string
  name: string
  style: OverlayStyle
}

export interface AppSettings {
  themeMode: ThemeMode
  dynamicColor: boolean
  exportFormat: ExportFormat
  autoLocationEnabled: boolean
}

export interface MediaAsset {
  url: string
  name: string
  type: 'image' | 'video'
  width: number
  height: number
  file: File | null
  capturedAt: Date
}

export const MODE_LABELS: Record<TransportMode, string> = {
  AIRPLANE: 'フライト',
  TRAIN: '鉄道',
  OTHER: '移動',
  SPOT: 'スポット',
}

export const MODE_ICONS: Record<TransportMode, string> = {
  AIRPLANE: 'plane',
  TRAIN: 'train',
  OTHER: 'car',
  SPOT: 'pin',
}

export const CORNER_LABELS: Record<OverlayCorner, string> = {
  TOP_LEFT: '左上',
  TOP_RIGHT: '右上',
  BOTTOM_LEFT: '左下',
  BOTTOM_RIGHT: '右下',
}

export const ASPECT_LABELS: Record<CropAspectPreset, string> = {
  ORIGINAL: '元画像',
  FREE: 'フリー',
  RATIO_4_5: '4:5',
  RATIO_9_16: '9:16',
  RATIO_2_3: '2:3',
  RATIO_1_1: '1:1',
}

export const FONT_OPTIONS = [
  { value: 'outfit', label: 'Outfit', family: 'Outfit' },
  { value: 'barlow', label: 'Barlow', family: 'Barlow' },
  { value: 'jetbrains-mono', label: 'JetBrains Mono', family: '"JetBrains Mono"' },
  { value: 'dm-sans', label: 'DM Sans', family: '"DM Sans"' },
  { value: 'kaisei-tokumin', label: 'Kaisei Tokumin', family: '"Kaisei Tokumin"' },
  { value: 'noto-sans-jp', label: 'Noto Sans JP', family: '"Noto Sans JP"' },
  { value: 'noto-serif-jp', label: 'Noto Serif JP', family: '"Noto Serif JP"' },
  { value: 'artemis', label: 'ARTEMIS INTER', family: '"Artemis Inter"' },
  { value: 'default', label: 'Default', family: 'system-ui' },
] as const

export const COLOR_SWATCHES = [
  '#ffffff',
  '#1a1a1a',
  '#767676',
  '#0078d7',
  '#002050',
  '#ffb900',
  '#d83b01',
  '#008272',
  '#107c10',
]

const now = new Date()

export const defaultTransportInfo: TransportInfo = {
  mode: 'AIRPLANE',
  airlineCode: 'NH',
  airlineDisplayName: 'ANA (All Nippon Airways)',
  flightNumber: '1731',
  registrationNumber: '',
  departureAirportCode: 'KIX',
  departureAirportName: 'Kansai International Airport',
  departureAirportCity: 'Osaka',
  departureAirportCountry: 'JP',
  departureLat: 34.427299,
  departureLon: 135.244003,
  arrivalAirportCode: 'OKA',
  arrivalAirportName: 'Naha Airport',
  arrivalAirportCity: 'Naha',
  arrivalAirportCountry: 'JP',
  arrivalLat: 26.195801,
  arrivalLon: 127.646004,
  isDomesticJapanRail: true,
  railwayCompany: 'JR東日本',
  railwayLineName: '北陸新幹線',
  trainName: 'はくたか 570',
  vehicleModel: 'E7系',
  departureStationName: '飯山',
  departureStationGroupCode: '001769',
  departureStationLat: 36.845143,
  departureStationLon: 138.359011,
  departureStationCountry: 'JP',
  arrivalStationName: '東京',
  arrivalStationGroupCode: '003766',
  arrivalStationLat: 35.681103,
  arrivalStationLon: 139.76788,
  arrivalStationCountry: 'JP',
  selectedViaLines: [],
  isAutoShortestVia: true,
  transportName: '湘南ドライブ',
  departurePlaceName: '鎌倉',
  departurePlaceLat: 35.3192,
  departurePlaceLon: 139.5467,
  departurePlaceCountry: 'JP',
  destinationPlaceName: '箱根湯本',
  destinationPlaceLat: 35.2324,
  destinationPlaceLon: 139.1069,
  destinationPlaceCountry: 'JP',
  spotLine1: 'SHIBUYA SKY',
  spotLine2: 'Tokyo Twilight',
  spotLine3: '229m Above Ground',
  routePolylines: [],
  photoLat: 34.4342,
  photoLon: 135.244,
  photoCountryCode: 'JP',
  photoLocationName: 'Osaka, Japan',
  locationDisplayMode: 'NAME',
  year: now.getFullYear(),
  month: now.getMonth() + 1,
  day: now.getDate(),
  hour: now.getHours(),
  minute: now.getMinutes(),
  dateTimeDisplayMode: 'DATE_ONLY',
}

export const defaultOverlayStyle: OverlayStyle = {
  font: 'outfit',
  textColor: '#ffffff',
  routeColor: '#ffffff',
  borderColor: '#ffffff',
  showMap: true,
  showOriginPlaneBadge: true,
  textCorner: 'BOTTOM_RIGHT',
  mapCorner: 'BOTTOM_LEFT',
  textFreePos: null,
  mapFreePos: null,
  textScale: 1,
  titleScale: 1,
  subtitleScale: 1,
  locationScale: 1,
  dateScale: 1,
  mapBoxScale: 1,
  mapInternalZoom: 1,
  mapInternalPanX: 0,
  mapInternalPanY: 0,
}

export function normalizeOverlayStyle(style?: Partial<OverlayStyle> | null): OverlayStyle {
  return { ...defaultOverlayStyle, ...style }
}

export const defaultCropFrame: CropFrameSettings = {
  aspectPreset: 'ORIGINAL',
  isLandscapeOrientation: false,
  freeAspectRatio: 1,
  cropCenterX: 0.5,
  cropCenterY: 0.5,
  cropZoom: 1,
  frameMarginRatio: 0,
  frameColor: '#ffffff',
}

export function buildFlightCode(info: TransportInfo): string {
  const code = info.airlineCode.trim().toUpperCase()
  const num = info.flightNumber.trim().toUpperCase()
  if (!code && !num) return ''
  if (!code) return num
  if (!num) return code
  return num.startsWith(code) ? `${code} ${num.slice(code.length).trim()}`.trim() : `${code} ${num}`
}

export function summaryLabel(info: TransportInfo): string {
  switch (info.mode) {
    case 'AIRPLANE': {
      const route = [info.departureAirportCode, info.arrivalAirportCode].filter(Boolean).join(' ✈ ')
      return [buildFlightCode(info), route].filter(Boolean).join(' · ') || 'Flight'
    }
    case 'TRAIN': {
      const route = [info.departureStationName, info.arrivalStationName].filter(Boolean).join(' → ')
      return [info.railwayLineName, route].filter(Boolean).join(' · ') || 'Train'
    }
    case 'OTHER': {
      const route = [info.departurePlaceName, info.destinationPlaceName].filter(Boolean).join(' → ')
      return [info.transportName, route].filter(Boolean).join(' · ') || 'Drive'
    }
    case 'SPOT':
      return [info.spotLine1, info.spotLine2, info.spotLine3].find(Boolean) || 'Spot'
  }
}

export function currentGpsCoord(info: TransportInfo): Coord | null {
  if (info.photoLat == null || info.photoLon == null) return null
  return { lon: info.photoLon, lat: info.photoLat }
}

/** Coordinates that decide which map datasets and viewport are needed. */
export function mapAnchorPoints(info: TransportInfo): Coord[] {
  return [startCoord(info), endCoord(info), currentGpsCoord(info)].filter(
    (point): point is Coord => point != null,
  )
}

export function startCoord(info: TransportInfo): Coord | null {
  switch (info.mode) {
    case 'AIRPLANE':
      return info.departureLat != null && info.departureLon != null
        ? { lon: info.departureLon, lat: info.departureLat }
        : null
    case 'TRAIN':
      return info.departureStationLat != null && info.departureStationLon != null
        ? { lon: info.departureStationLon, lat: info.departureStationLat }
        : null
    case 'OTHER':
      return info.departurePlaceLat != null && info.departurePlaceLon != null
        ? { lon: info.departurePlaceLon, lat: info.departurePlaceLat }
        : null
    case 'SPOT':
      return null
  }
}

export function endCoord(info: TransportInfo): Coord | null {
  switch (info.mode) {
    case 'AIRPLANE':
      return info.arrivalLat != null && info.arrivalLon != null
        ? { lon: info.arrivalLon, lat: info.arrivalLat }
        : null
    case 'TRAIN':
      return info.arrivalStationLat != null && info.arrivalStationLon != null
        ? { lon: info.arrivalStationLon, lat: info.arrivalStationLat }
        : null
    case 'OTHER':
      return info.destinationPlaceLat != null && info.destinationPlaceLon != null
        ? { lon: info.destinationPlaceLon, lat: info.destinationPlaceLat }
        : null
    case 'SPOT':
      return null
  }
}
