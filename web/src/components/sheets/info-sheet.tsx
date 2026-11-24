import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import {
  ArrowUpDown,
  Calendar,
  Check,
  ChevronRight,
  Clock,
  History,
  Loader2,
  LocateFixed,
  MapPin,
  Plane,
  Search,
  TrainFront,
  X,
} from 'lucide-react'
import { toast } from 'sonner'
import { SectionCard } from '@/components/section-card'
import { Segmented } from '@/components/segmented'
import { SheetShell } from '@/components/sheets/sheet-shell'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Switch } from '@/components/ui/switch'
import { useIsMobile } from '@/hooks/use-media-query'
import {
  fetchDrivingRoute,
  findAirportByIata,
  searchAirlines,
  searchAirports,
  searchPlaces,
  searchRailLines,
  searchStations,
  reverseGeocode,
} from '@/lib/data'
import { formatLatLon, parseLat, parseLon } from '@/lib/geo'
import { loadTrainRoute } from '@/lib/rail-geometry'
import { useDialogFocusReturn } from '@/hooks/use-dialog-focus'
import { useAppStore } from '@/store/app-store'
import { useUiStore } from '@/store/ui-store'
import {
  CORNER_LABELS,
  MODE_LABELS,
  type AirlineEntry,
  type AirportRecord,
  type DateTimeDisplayMode,
  type LocationDisplayMode,
  type OverlayCorner,
  type PlaceCandidate,
  type RailLineEntry,
  type StationRecord,
  type TransportInfo,
  type TransportMode,
  summaryLabel,
} from '@/types'
import { modeIcon } from '@/components/editor/route-chip'

type SearchKind =
  | 'dep-airport'
  | 'arr-airport'
  | 'airline'
  | 'line'
  | 'dep-station'
  | 'arr-station'
  | 'dep-place'
  | 'dest-place'
  | 'photo-place'

const SEARCH_TITLES: Record<SearchKind, string> = {
  'dep-airport': '出発空港を検索',
  'arr-airport': '到着空港を検索',
  airline: '航空会社を選択',
  line: '路線を検索',
  'dep-station': '出発駅を検索',
  'arr-station': '到着駅を検索',
  'dep-place': '出発地を検索',
  'dest-place': '目的地を検索',
  'photo-place': '撮影地を検索',
}

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function dateValue(info: TransportInfo) {
  return `${info.year}-${pad(info.month)}-${pad(info.day)}`
}

function timeValue(info: TransportInfo) {
  return `${pad(info.hour)}:${pad(info.minute)}`
}

export function InfoSheet() {
  const sheet = useUiStore((s) => s.sheet)
  const closeSheet = useUiStore((s) => s.closeSheet)
  const open = sheet === 'info'
  const info = useAppStore((s) => s.info)
  const mediaUrl = useAppStore((s) => s.media?.url)
  const previousMediaUrl = useRef<string | undefined>(undefined)
  const history = useAppStore((s) => s.history)
  const applyInfo = useAppStore((s) => s.applyInfo)
  const style = useAppStore((s) => s.style)
  const updateStyle = useAppStore((s) => s.updateStyle)
  const [draft, setDraft] = useState<TransportInfo>(info)
  const [styleDraft, setStyleDraft] = useState(style)
  const [searchKind, setSearchKind] = useState<SearchKind | null>(null)
  const [coordOpen, setCoordOpen] = useState(false)
  const [coordLat, setCoordLat] = useState('')
  const [coordLon, setCoordLon] = useState('')
  const [coordError, setCoordError] = useState<string | null>(null)
  const coordFocus = useDialogFocusReturn()
  const searchFocus = useDialogFocusReturn()
  const [locating, setLocating] = useState(false)
  const [applying, setApplying] = useState(false)
  const locToken = useRef(0)
  const applyingRef = useRef(false)
  const applyToken = useRef(0)

  function cancelPendingApply() {
    applyToken.current++
    applyingRef.current = false
    setApplying(false)
  }

  useEffect(() => {
    // Switching media invalidates any route request started for the old asset.
    applyToken.current++
  }, [mediaUrl])

  const wasOpen = useRef(false)
  useEffect(() => {
    if (open && !wasOpen.current) {
      locToken.current++
      setDraft(info)
      setStyleDraft(style)
    }
    wasOpen.current = open
  }, [open, info, style])

  useEffect(() => {
    if (!open) locToken.current++
  }, [open])

  useEffect(() => {
    if (open && mediaUrl && mediaUrl !== previousMediaUrl.current) setDraft(info)
    previousMediaUrl.current = mediaUrl
  }, [mediaUrl, open, info])

  const modeHistory = useMemo(
    () => history.filter((h) => h.mode === draft.mode).slice(0, 5),
    [history, draft.mode],
  )

  function update(patch: Partial<TransportInfo>) {
    setDraft((d) => ({ ...d, ...patch }))
  }

  async function apply() {
    if (applyingRef.current) return
    applyingRef.current = true
    setApplying(true)
    const token = ++applyToken.current
    const startRevision = useAppStore.getState().infoRevision
    const startMediaUrl = useAppStore.getState().media?.url
    let next: TransportInfo = { ...draft, routePolylines: [] }
    let routeMissing = false
    let trainRouteMissing = false
    try {
      if (draft.mode === 'TRAIN' && draft.isDomesticJapanRail) {
        const depLat = draft.departureStationLat
        const depLon = draft.departureStationLon
        const arrLat = draft.arrivalStationLat
        const arrLon = draft.arrivalStationLon
        if (depLat != null && depLon != null && arrLat != null && arrLon != null) {
          try {
            const route = await loadTrainRoute(
              draft.railwayCompany,
              draft.railwayLineName,
              { lat: depLat, lon: depLon },
              { lat: arrLat, lon: arrLon },
            )
            if (route.length) next = { ...next, routePolylines: route }
            else trainRouteMissing = true
          } catch (err) {
            console.error(err)
            trainRouteMissing = true
          }
        }
      } else if (draft.mode === 'OTHER') {
        const depLat = draft.departurePlaceLat
        const depLon = draft.departurePlaceLon
        const destLat = draft.destinationPlaceLat
        const destLon = draft.destinationPlaceLon
        if (depLat != null && depLon != null && destLat != null && destLon != null) {
          try {
            const route = await fetchDrivingRoute(
              { lat: depLat, lon: depLon },
              { lat: destLat, lon: destLon },
              draft.photoLat != null && draft.photoLon != null
                ? { lat: draft.photoLat, lon: draft.photoLon }
                : null,
            )
            if (route.length) next = { ...next, routePolylines: route }
            else routeMissing = true
          } catch (err) {
            console.error(err)
            routeMissing = true
          }
        }
      }

      if (applyToken.current !== token) return
      const current = useAppStore.getState()
      if (current.infoRevision !== startRevision || current.media?.url !== startMediaUrl) return

      locToken.current++
      updateStyle({
        textCorner: styleDraft.textCorner,
        mapCorner: styleDraft.mapCorner,
        showMap: styleDraft.showMap,
      })
      applyInfo(next)
      closeSheet()
      toast.success('透かし情報を適用しました')
      if (routeMissing) {
        toast.info('道路経路を取得できませんでした。直線で表示します')
      }
      if (trainRouteMissing) {
        toast.info('鉄道形状を取得できませんでした。直線で表示します')
      }
    } finally {
      if (applyToken.current === token) {
        applyingRef.current = false
        setApplying(false)
      }
    }
  }

  function setLocation(lat: number, lon: number, label?: string) {
    const token = ++locToken.current
    update({
      photoLat: lat,
      photoLon: lon,
      photoCountryCode: '',
      photoLocationName: label ?? formatLatLon(lat, lon, 5),
    })
    void reverseGeocode(lat, lon).then((resolved) => {
      if (token !== locToken.current) return
      setDraft((d) => ({
        ...d,
        photoLocationName: resolved || formatLatLon(lat, lon, 5),
      }))
    })
  }

  function useCurrentLocation() {
    if (!navigator.geolocation) {
      toast.error('このブラウザは現在地取得に対応していません')
      return
    }
    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocating(false)
        setLocation(pos.coords.latitude, pos.coords.longitude)
        toast.success('現在地を撮影地に設定しました')
      },
      () => {
        setLocating(false)
        toast.error('現在地を取得できませんでした。座標を手入力してください')
      },
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 60000 },
    )
  }

  function openSearch(kind: SearchKind) {
    searchFocus.capture()
    setSearchKind(kind)
  }

  function openCoordinateDialog() {
    setCoordLat(draft.photoLat != null ? draft.photoLat.toFixed(6) : '')
    setCoordLon(draft.photoLon != null ? draft.photoLon.toFixed(6) : '')
    setCoordError(null)
    setCoordOpen(true)
  }

  function confirmCoordinates() {
    const lat = parseLat(coordLat)
    const lon = parseLon(coordLon)
    if (lat == null || lon == null) {
      setCoordError('緯度は -90〜90、経度は -180〜180 の範囲で入力してください')
      return
    }
    setLocation(lat, lon)
    setCoordOpen(false)
  }

  function onSearchSelect(kind: SearchKind, value: unknown) {
    if (kind === 'dep-airport' || kind === 'arr-airport') {
      const ap = value as AirportRecord
      if (kind === 'dep-airport') {
        update({
          departureAirportCode: ap.iata,
          departureAirportName: ap.name,
          departureAirportCity: ap.city,
          departureAirportCountry: ap.country,
          departureLat: ap.lat,
          departureLon: ap.lon,
        })
      } else {
        update({
          arrivalAirportCode: ap.iata,
          arrivalAirportName: ap.name,
          arrivalAirportCity: ap.city,
          arrivalAirportCountry: ap.country,
          arrivalLat: ap.lat,
          arrivalLon: ap.lon,
        })
      }
    } else if (kind === 'airline') {
      const airline = value as AirlineEntry
      update({ airlineCode: airline.code, airlineDisplayName: airline.fullName })
    } else if (kind === 'line') {
      const line = value as RailLineEntry
      const clearStations =
        draft.railwayLineName !== line.line || draft.railwayCompany !== line.company
      update({
        railwayLineName: line.line,
        railwayCompany: line.company,
        ...(clearStations
          ? {
              departureStationName: '',
              departureStationGroupCode: '',
              departureStationLat: null,
              departureStationLon: null,
              departureStationCountry: '',
              arrivalStationName: '',
              arrivalStationGroupCode: '',
              arrivalStationLat: null,
              arrivalStationLon: null,
              arrivalStationCountry: '',
            }
          : {}),
      })
    } else if (kind === 'dep-station' || kind === 'arr-station') {
      const st = value as StationRecord
      const prefix = kind === 'dep-station' ? 'departureStation' : 'arrivalStation'
      update({
        [`${prefix}Name`]: st.name,
        [`${prefix}GroupCode`]: st.code,
        [`${prefix}Lat`]: st.lat,
        [`${prefix}Lon`]: st.lon,
        [`${prefix}Country`]: 'JP',
        railwayLineName: draft.railwayLineName || st.line,
        railwayCompany: st.company,
      } as Partial<TransportInfo>)
    } else {
      const place = value as PlaceCandidate
      if (kind === 'photo-place') {
        locToken.current++
        update({
          photoLat: place.latitude,
          photoLon: place.longitude,
          photoLocationName: place.subtitle ? `${place.name}, ${place.subtitle}` : place.name,
          photoCountryCode: place.countryCode,
        })
      } else if (kind === 'dep-place') {
        if (draft.mode === 'TRAIN') {
          update({
            departureStationName: place.name,
            departureStationLat: place.latitude,
            departureStationLon: place.longitude,
            departureStationCountry: place.countryCode,
          })
        } else {
          update({
            departurePlaceName: place.name,
            departurePlaceLat: place.latitude,
            departurePlaceLon: place.longitude,
            departurePlaceCountry: place.countryCode,
          })
        }
      } else {
        if (draft.mode === 'TRAIN') {
          update({
            arrivalStationName: place.name,
            arrivalStationLat: place.latitude,
            arrivalStationLon: place.longitude,
            arrivalStationCountry: place.countryCode,
          })
        } else {
          update({
            destinationPlaceName: place.name,
            destinationPlaceLat: place.latitude,
            destinationPlaceLon: place.longitude,
            destinationPlaceCountry: place.countryCode,
          })
        }
      }
    }
    setSearchKind(null)
  }

  async function handleFreeText(kind: SearchKind, raw: string) {
    const text = raw.trim()
    if (!text) return
    if (kind === 'dep-airport' || kind === 'arr-airport') {
      const found = await findAirportByIata(text)
      if (found) {
        onSearchSelect(kind, found)
        return
      }
      if (kind === 'dep-airport') {
        update({
          departureAirportCode: text.toUpperCase(),
          departureAirportName: '',
          departureAirportCity: '',
          departureAirportCountry: '',
          departureLat: null,
          departureLon: null,
        })
      } else {
        update({
          arrivalAirportCode: text.toUpperCase(),
          arrivalAirportName: '',
          arrivalAirportCity: '',
          arrivalAirportCountry: '',
          arrivalLat: null,
          arrivalLon: null,
        })
      }
    } else if (kind === 'airline') {
      update({ airlineCode: text.toUpperCase(), airlineDisplayName: text })
    } else if (kind === 'line') {
      update({ railwayLineName: text, railwayCompany: '' })
    } else if (kind === 'dep-station' || kind === 'arr-station') {
      const prefix = kind === 'dep-station' ? 'departureStation' : 'arrivalStation'
      update({
        [`${prefix}Name`]: text,
        [`${prefix}GroupCode`]: '',
        [`${prefix}Lat`]: null,
        [`${prefix}Lon`]: null,
      } as Partial<TransportInfo>)
    } else if (kind === 'dep-place' || kind === 'dest-place') {
      if (draft.mode === 'TRAIN') {
        const prefix = kind === 'dep-place' ? 'departureStation' : 'arrivalStation'
        update({
          [`${prefix}Name`]: text,
          [`${prefix}GroupCode`]: '',
          [`${prefix}Lat`]: null,
          [`${prefix}Lon`]: null,
        } as Partial<TransportInfo>)
      } else {
        const prefix = kind === 'dep-place' ? 'departurePlace' : 'destinationPlace'
        update({
          [`${prefix}Name`]: text,
          [`${prefix}Lat`]: null,
          [`${prefix}Lon`]: null,
        } as Partial<TransportInfo>)
      }
    } else {
      return
    }
    setSearchKind(null)
  }

  const modeOptions = (Object.keys(MODE_LABELS) as TransportMode[]).map((m) => ({
    value: m,
    label: MODE_LABELS[m],
    icon: modeIcon(m, 'size-3.5 shrink-0'),
  }))

  return (
    <>
      <SheetShell
        open={open}
        onOpenChange={(next) => {
          if (!next) cancelPendingApply()
          closeSheet()
        }}
        title="透かし情報"
        action={
          <Button
            size="sm"
            className="rounded-full px-4 font-semibold"
            onClick={() => void apply()}
            disabled={applying}
          >
            {applying && <Loader2 className="size-3.5 animate-spin" />}
            適用
          </Button>
        }
      >
        <div className="space-y-4 pb-4">
          <Segmented ariaLabel="透かしモード" options={modeOptions} value={draft.mode} onValueChange={(mode) => update({ mode })} />

          {modeHistory.length > 0 && (
            <div className="flex gap-2 overflow-x-auto pb-1 no-scrollbar">
              {modeHistory.map((h, i) => (
                <Button
                  key={`${h.mode}-${i}`}
                  variant="outline"
                  size="sm"
                  className="shrink-0 rounded-full text-xs"
                  onClick={() => {
                    setDraft({
                      ...h,
                      year: draft.year,
                      month: draft.month,
                      day: draft.day,
                      hour: draft.hour,
                      minute: draft.minute,
                      photoLat: draft.photoLat,
                      photoLon: draft.photoLon,
                      photoLocationName: draft.photoLocationName,
                      photoCountryCode: draft.photoCountryCode,
                      locationDisplayMode: draft.locationDisplayMode,
                      dateTimeDisplayMode: draft.dateTimeDisplayMode,
                    })
                  }}
                >
                  <History className="size-3.5" />
                  {summaryLabel(h)}
                </Button>
              ))}
            </div>
          )}

          {draft.mode === 'AIRPLANE' && (
            <SectionCard
              title="フライト情報"
              trailing={
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="rounded-full"
                  aria-label="出発・到着を入れ替え"
                  onClick={() =>
                    setDraft((d) => ({
                      ...d,
                      departureAirportCode: d.arrivalAirportCode,
                      departureAirportName: d.arrivalAirportName,
                      departureAirportCity: d.arrivalAirportCity,
                      departureAirportCountry: d.arrivalAirportCountry,
                      departureLat: d.arrivalLat,
                      departureLon: d.arrivalLon,
                      arrivalAirportCode: d.departureAirportCode,
                      arrivalAirportName: d.departureAirportName,
                      arrivalAirportCity: d.departureAirportCity,
                      arrivalAirportCountry: d.departureAirportCountry,
                      arrivalLat: d.departureLat,
                      arrivalLon: d.departureLon,
                    }))
                  }
                >
                  <ArrowUpDown className="size-4" />
                </Button>
              }
            >
              <PickerField
                label="出発空港"
                value={
                  draft.departureAirportCode
                    ? `${draft.departureAirportCode} · ${draft.departureAirportName}`
                    : ''
                }
                placeholder="空港を検索 (例: HND, KIX)"
                icon={<Plane className="size-4" />}
                onClick={() => openSearch('dep-airport')}
              />
              <PickerField
                label="到着空港"
                value={
                  draft.arrivalAirportCode
                    ? `${draft.arrivalAirportCode} · ${draft.arrivalAirportName}`
                    : ''
                }
                placeholder="空港を検索 (例: OKA, CTS)"
                icon={<Plane className="size-4 rotate-180" />}
                onClick={() => openSearch('arr-airport')}
              />
              <PickerField
                label="航空会社"
                value={draft.airlineCode ? `${draft.airlineCode} · ${draft.airlineDisplayName}` : ''}
                placeholder="航空会社を選択 (任意)"
                icon={<Search className="size-4" />}
                onClick={() => openSearch('airline')}
                onClear={() => update({ airlineCode: '', airlineDisplayName: '' })}
              />
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <Label htmlFor="flight-number">便名</Label>
                  <Input
                    id="flight-number"
                    value={draft.flightNumber}
                    onChange={(e) => update({ flightNumber: e.target.value })}
                    placeholder="1731"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="registration">機体記号 (任意)</Label>
                  <Input
                    id="registration"
                    value={draft.registrationNumber}
                    onChange={(e) => update({ registrationNumber: e.target.value.toUpperCase() })}
                    placeholder="JA812A"
                  />
                </div>
              </div>
            </SectionCard>
          )}

          {draft.mode === 'TRAIN' && (
            <SectionCard
              title="列車・路線情報"
              trailing={
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="rounded-full"
                  aria-label="発着駅を入れ替え"
                  onClick={() =>
                    setDraft((d) => ({
                      ...d,
                      departureStationName: d.arrivalStationName,
                      departureStationGroupCode: d.arrivalStationGroupCode,
                      departureStationLat: d.arrivalStationLat,
                      departureStationLon: d.arrivalStationLon,
                      departureStationCountry: d.arrivalStationCountry,
                      arrivalStationName: d.departureStationName,
                      arrivalStationGroupCode: d.departureStationGroupCode,
                      arrivalStationLat: d.departureStationLat,
                      arrivalStationLon: d.departureStationLon,
                      arrivalStationCountry: d.departureStationCountry,
                    }))
                  }
                >
                  <ArrowUpDown className="size-4" />
                </Button>
              }
            >
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-medium">日本の鉄道路線・駅DBを使用</p>
                <Switch
                  aria-label="日本の鉄道路線・駅DBを使用"
                  checked={draft.isDomesticJapanRail}
                  onCheckedChange={(checked) => update({ isDomesticJapanRail: checked })}
                />
              </div>
              {draft.isDomesticJapanRail ? (
                <PickerField
                  label="路線名"
                  value={draft.railwayLineName}
                  placeholder="路線を検索（駅選択で自動判定も可）"
                  icon={<TrainFront className="size-4" />}
                  onClick={() => openSearch('line')}
                  onClear={() => update({ railwayLineName: '', railwayCompany: '' })}
                />
              ) : (
                <div className="space-y-1.5">
                  <Label htmlFor="railway-line">路線名</Label>
                  <Input
                    id="railway-line"
                    value={draft.railwayLineName}
                    onChange={(e) => update({ railwayLineName: e.target.value })}
                  />
                </div>
              )}
              <PickerField
                label="出発駅"
                value={draft.departureStationName}
                placeholder="駅を検索"
                icon={<MapPin className="size-4" />}
                onClick={() => setSearchKind(draft.isDomesticJapanRail ? 'dep-station' : 'dep-place')}
              />
              <PickerField
                label="到着駅"
                value={draft.arrivalStationName}
                placeholder="駅を検索"
                icon={<MapPin className="size-4" />}
                onClick={() => setSearchKind(draft.isDomesticJapanRail ? 'arr-station' : 'dest-place')}
              />
              <p className="text-xs leading-5 text-muted-foreground">
                Web版の線路形状・経由路線は簡易表示です。
              </p>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <Label htmlFor="train-name">列車名 (任意)</Label>
                  <Input
                    id="train-name"
                    value={draft.trainName}
                    onChange={(e) => update({ trainName: e.target.value })}
                    placeholder="はくたか 570"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="vehicle">車両型式 (任意)</Label>
                  <Input
                    id="vehicle"
                    value={draft.vehicleModel}
                    onChange={(e) => update({ vehicleModel: e.target.value })}
                    placeholder="E7系"
                  />
                </div>
              </div>
            </SectionCard>
          )}

          {draft.mode === 'OTHER' && (
            <SectionCard
              title="移動ルート情報"
              trailing={
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="rounded-full"
                  aria-label="出発・目的地を入れ替え"
                  onClick={() =>
                    setDraft((d) => ({
                      ...d,
                      departurePlaceName: d.destinationPlaceName,
                      departurePlaceLat: d.destinationPlaceLat,
                      departurePlaceLon: d.destinationPlaceLon,
                      departurePlaceCountry: d.destinationPlaceCountry,
                      destinationPlaceName: d.departurePlaceName,
                      destinationPlaceLat: d.departurePlaceLat,
                      destinationPlaceLon: d.departurePlaceLon,
                      destinationPlaceCountry: d.departurePlaceCountry,
                    }))
                  }
                >
                  <ArrowUpDown className="size-4" />
                </Button>
              }
            >
              <div className="space-y-1.5">
                <Label htmlFor="transport-name">移動手段・タイトル (任意)</Label>
                <Input
                  id="transport-name"
                  value={draft.transportName}
                  onChange={(e) => update({ transportName: e.target.value })}
                  placeholder="Road Trip / Ferry"
                />
              </div>
              <PickerField
                label="出発地"
                value={draft.departurePlaceName}
                placeholder="場所を検索"
                icon={<MapPin className="size-4" />}
                onClick={() => openSearch('dep-place')}
              />
              <PickerField
                label="目的地"
                value={draft.destinationPlaceName}
                placeholder="場所を検索"
                icon={<MapPin className="size-4" />}
                onClick={() => openSearch('dest-place')}
              />
            </SectionCard>
          )}

          {draft.mode === 'SPOT' && (
            <SectionCard title="スポットテキスト">
              <div className="space-y-1.5">
                <Label htmlFor="spot1">1行目（メイン見出し）</Label>
                <Input
                  id="spot1"
                  value={draft.spotLine1}
                  onChange={(e) => update({ spotLine1: e.target.value })}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="spot2">2行目（サブ見出し・任意）</Label>
                <Input
                  id="spot2"
                  value={draft.spotLine2}
                  onChange={(e) => update({ spotLine2: e.target.value })}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="spot3">3行目（補足・任意）</Label>
                <Input
                  id="spot3"
                  value={draft.spotLine3}
                  onChange={(e) => update({ spotLine3: e.target.value })}
                />
              </div>
            </SectionCard>
          )}

          <SectionCard title="日時・撮影地">
            <Segmented
              ariaLabel="日時表示"
              options={[
                { value: 'DATE_ONLY', label: '日付のみ' },
                { value: 'DATE_AND_TIME', label: '日時' },
                { value: 'NONE', label: '非表示' },
              ]}
              value={draft.dateTimeDisplayMode}
              onValueChange={(value) => update({ dateTimeDisplayMode: value as DateTimeDisplayMode })}
            />
            {draft.dateTimeDisplayMode !== 'NONE' && (
              <div className="grid grid-cols-2 gap-3">
                <div className="relative">
                  <Calendar className="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    type="date"
                    aria-label="日付"
                    className="pl-8"
                    value={dateValue(draft)}
                    onChange={(e) => {
                      const [y, m, d] = e.target.value.split('-').map(Number)
                      if (y && m && d) update({ year: y, month: m, day: d })
                    }}
                  />
                </div>
                {draft.dateTimeDisplayMode === 'DATE_AND_TIME' && (
                  <div className="relative">
                    <Clock className="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
                    <Input
                      type="time"
                      aria-label="時刻"
                      className="pl-8"
                      value={timeValue(draft)}
                      onChange={(e) => {
                        const [h, min] = e.target.value.split(':').map(Number)
                        if (Number.isFinite(h) && Number.isFinite(min)) update({ hour: h, minute: min })
                      }}
                    />
                  </div>
                )}
              </div>
            )}

            <div className="space-y-2">
              <p className="text-sm font-medium">撮影地</p>
              <div className="rounded-2xl border border-border/70 bg-background/60 p-3">
                <div className="flex items-start gap-2.5">
                  <MapPin className="mt-0.5 size-5 shrink-0 text-primary" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold">
                      {draft.photoLocationName || (draft.photoLat != null ? '座標から設定' : '未設定')}
                    </p>
                    <p className="truncate text-xs text-muted-foreground">
                      {draft.photoLat != null && draft.photoLon != null
                        ? formatLatLon(draft.photoLat, draft.photoLon, 5)
                        : '座標が未設定 — 検索・座標入力・現在地から設定できます'}
                    </p>
                  </div>
                </div>
                <div className="mt-3 flex gap-2 overflow-x-auto no-scrollbar">
                  <Button
                    variant="outline"
                    size="sm"
                    className="shrink-0 rounded-full"
                    onClick={() => openSearch('photo-place')}
                  >
                    <Search className="size-3.5" />
                    場所を検索
                  </Button>
                  <Button variant="outline" size="sm" className="shrink-0 rounded-full" onClick={(event) => { coordFocus.capture(event.currentTarget); openCoordinateDialog() }}>
                    <MapPin className="size-3.5" />
                    座標入力
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    className="shrink-0 rounded-full"
                    onClick={useCurrentLocation}
                    disabled={locating}
                  >
                    {locating ? <Loader2 className="size-3.5 animate-spin" /> : <LocateFixed className="size-3.5" />}
                    現在地
                  </Button>
                  {(draft.photoLat != null || draft.photoLocationName) && (
                    <Button
                      variant="ghost"
                      size="sm"
                      className="shrink-0 rounded-full text-muted-foreground"
                      onClick={() => {
                        locToken.current++
                        update({ photoLat: null, photoLon: null, photoLocationName: '', photoCountryCode: '' })
                      }}
                    >
                      <X className="size-3.5" />
                      クリア
                    </Button>
                  )}
                </div>
              </div>
            </div>

            <div className="space-y-2">
              <p className="text-sm font-medium">撮影地ラベルの表示</p>
              <Segmented
                ariaLabel="撮影地ラベルの表示"
                options={[
                  { value: 'NAME', label: '地名' },
                  { value: 'COORDINATES', label: '緯度・経度' },
                  { value: 'NONE', label: '非表示' },
                ]}
                value={draft.locationDisplayMode}
                onValueChange={(value) => update({ locationDisplayMode: value as LocationDisplayMode })}
              />
            </div>
            {draft.locationDisplayMode === 'NAME' && (
              <div className="space-y-1.5">
                <Label htmlFor="location-name">表示する地名</Label>
                <Input
                  id="location-name"
                  value={draft.photoLocationName}
                  onChange={(e) => {
                    locToken.current++
                    update({ photoLocationName: e.target.value })
                  }}
                  placeholder="Osaka, Japan"
                />
              </div>
            )}
          </SectionCard>

          <SectionCard title="配置と地図">
            <p className="text-sm font-medium">テキストの基準位置</p>
            <Segmented
              ariaLabel="テキストの基準位置"
              options={(Object.keys(CORNER_LABELS) as OverlayCorner[]).map((corner) => ({
                value: corner,
                label: CORNER_LABELS[corner],
              }))}
              value={styleDraft.textCorner}
              onValueChange={(corner) =>
                setStyleDraft((s) => ({
                  ...s,
                  textCorner: corner as OverlayCorner,
                  textFreePos: null,
                }))
              }
            />
            <p className="text-sm font-medium">地図の基準位置</p>
            <Segmented
              ariaLabel="地図の基準位置"
              options={(Object.keys(CORNER_LABELS) as OverlayCorner[]).map((corner) => ({
                value: corner,
                label: CORNER_LABELS[corner],
              }))}
              value={styleDraft.mapCorner}
              onValueChange={(corner) =>
                setStyleDraft((s) => ({
                  ...s,
                  mapCorner: corner as OverlayCorner,
                  mapFreePos: null,
                }))
              }
            />
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm font-medium">地図透かしを表示</p>
              <Switch
                aria-label="地図透かしを表示"
                checked={styleDraft.showMap}
                onCheckedChange={(checked) => setStyleDraft((s) => ({ ...s, showMap: checked }))}
              />
            </div>
          </SectionCard>
        </div>
      </SheetShell>

      <EntitySearchDialog
        kind={searchKind}
        lineFilter={draft.railwayLineName}
        companyFilter={draft.railwayCompany}
        biasLat={draft.photoLat}
        biasLon={draft.photoLon}
        onClose={() => setSearchKind(null)}
        onCloseAutoFocus={searchFocus.onCloseAutoFocus}
        onSelect={onSearchSelect}
        onFreeText={(kind, text) => void handleFreeText(kind, text)}
      />

      <Dialog open={coordOpen} onOpenChange={setCoordOpen}>
        <DialogContent
          className="max-h-[min(90dvh,32rem)] overflow-y-auto rounded-[28px] pb-[max(1.25rem,env(safe-area-inset-bottom))]"
          onCloseAutoFocus={coordFocus.onCloseAutoFocus}
        >
          <DialogHeader>
            <DialogTitle>緯度・経度を入力</DialogTitle>
            <DialogDescription>
              小数の度（例: 35.65858 / 139.74543）または度分秒で入力できます。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1.5">
              <Label htmlFor="coord-lat">緯度（-90〜90）</Label>
              <Input
                id="coord-lat"
                aria-invalid={coordError != null}
                value={coordLat}
                onChange={(e) => {
                  setCoordLat(e.target.value)
                  setCoordError(null)
                }}
                placeholder="35.65858"
                inputMode="text"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="coord-lon">経度（-180〜180）</Label>
              <Input
                id="coord-lon"
                aria-invalid={coordError != null}
                value={coordLon}
                onChange={(e) => {
                  setCoordLon(e.target.value)
                  setCoordError(null)
                }}
                placeholder="139.74543"
                inputMode="text"
              />
            </div>
            {coordError && <p role="alert" className="text-xs text-destructive">{coordError}</p>}
            <div className="flex justify-end gap-2 pt-1">
              <Button variant="ghost" onClick={() => setCoordOpen(false)}>
                キャンセル
              </Button>
              <Button onClick={confirmCoordinates}>
                <Check className="size-4" />
                決定
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </>
  )
}

function PickerField({
  label,
  value,
  placeholder,
  icon,
  onClick,
  onClear,
}: {
  label: string
  value: string
  placeholder: string
  icon: ReactNode
  onClick: () => void
  onClear?: () => void
}) {
  return (
    <div className="flex items-center gap-3 rounded-2xl border border-border/70 bg-background/60 px-3 py-3">
      <span className="text-primary">{icon}</span>
      <button
        type="button"
        className="min-w-0 flex-1 text-left"
        onClick={onClick}
        aria-label={`${label}を選択`}
      >
        <span className="block text-xs leading-4 font-medium text-muted-foreground">{label}</span>
        <span className={value ? 'block truncate text-sm font-semibold' : 'block truncate text-sm text-muted-foreground'}>
          {value || placeholder}
        </span>
      </button>
      {onClear && value && (
        <Button
          variant="ghost"
          size="icon-xs"
          className="rounded-full"
          onClick={onClear}
          aria-label={`${label}をクリア`}
        >
          <X className="size-3.5" />
        </Button>
      )}
      <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
    </div>
  )
}

interface EntitySearchDialogProps {
  kind: SearchKind | null
  lineFilter: string
  companyFilter: string
  biasLat: number | null
  biasLon: number | null
  onClose: () => void
  onCloseAutoFocus?: (event: Event) => void
  onSelect: (kind: SearchKind, value: unknown) => void
  onFreeText: (kind: SearchKind, text: string) => void
}

function EntitySearchDialog({ kind, lineFilter, companyFilter, biasLat, biasLon, onClose, onCloseAutoFocus, onSelect, onFreeText }: EntitySearchDialogProps) {
  const isMobile = useIsMobile()
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [airports, setAirports] = useState<AirportRecord[]>([])
  const [stations, setStations] = useState<StationRecord[]>([])
  const [lines, setLines] = useState<RailLineEntry[]>([])
  const [places, setPlaces] = useState<PlaceCandidate[]>([])
  const [airlines, setAirlines] = useState<AirlineEntry[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!kind) return
    setQuery('')
    setError(null)
  }, [kind])

  useEffect(() => {
    if (!kind) return
    let cancelled = false
    const timer = setTimeout(() => {
      const run = async () => {
        setLoading(true)
        setError(null)
        try {
          if (kind === 'dep-airport' || kind === 'arr-airport') {
            const res = await searchAirports(query)
            if (!cancelled) setAirports(res)
          } else if (kind === 'line') {
            const res = await searchRailLines(query)
            if (!cancelled) setLines(res)
          } else if (kind === 'dep-station' || kind === 'arr-station') {
            const res = await searchStations(query, lineFilter, companyFilter)
            if (!cancelled) setStations(res)
          } else if (kind === 'photo-place' || kind === 'dep-place' || kind === 'dest-place') {
            const res = await searchPlaces(query, biasLat, biasLon)
            if (!cancelled) setPlaces(res)
          } else {
            const res = await searchAirlines(query)
            if (!cancelled) setAirlines(res)
          }
        } catch (err) {
          if (!cancelled) {
            console.error(err)
            setError('検索に失敗しました。通信状態を確認して再度お試しください。')
          }
        } finally {
          if (!cancelled) setLoading(false)
        }
      }
      void run()
    }, query ? 350 : 0)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [kind, query, lineFilter, companyFilter, biasLat, biasLon])

  const title = kind ? SEARCH_TITLES[kind] : ''

  return (
    <Dialog open={!!kind} onOpenChange={(open) => (!open ? onClose() : undefined)}>
      <DialogContent
        className={
          isMobile
            ? 'top-auto bottom-0 max-h-[86dvh] w-full max-w-full translate-y-0 rounded-t-[2rem] rounded-b-none p-0'
            : 'max-w-md rounded-3xl p-0'
        }
        onCloseAutoFocus={onCloseAutoFocus}
      >
        <DialogHeader className="min-w-0 border-b border-border/60 px-5 py-4">
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription className="sr-only">候補を検索して選択します</DialogDescription>
          <div className="relative mt-2">
            <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              aria-label="検索語"
              autoFocus
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && query.trim() && kind && kind !== 'photo-place') {
                  e.preventDefault()
                  onFreeText(kind, query.trim())
                }
              }}
              placeholder="名前・コードで絞り込み"
              className="pr-9 pl-9"
            />
            {query && (
              <button
                type="button"
                className="absolute top-1/2 right-2 flex size-6 -translate-y-1/2 items-center justify-center rounded-full text-muted-foreground hover:bg-muted"
                onClick={() => setQuery('')}
                aria-label="検索語をクリア"
              >
                <X className="size-3.5" />
              </button>
            )}
          </div>
          {query.trim() && kind && kind !== 'photo-place' && (
            <Button
              variant="ghost"
              size="sm"
              className="mt-1 h-8 max-w-full self-end truncate text-xs"
              title={`「${query.trim()}」を直接入力`}
              onClick={() => onFreeText(kind, query.trim())}
            >
              直接入力
            </Button>
          )}
        </DialogHeader>
        <ScrollArea className="h-[min(60dvh,26rem)]">
          <div className="p-2">
            {loading && (
              <div role="status" aria-live="polite" className="flex items-center justify-center gap-2 py-3 text-sm text-muted-foreground">
                <Loader2 className="size-4 animate-spin" /> 検索中…
              </div>
            )}
            {error && (
              <p role="alert" className="px-3 py-4 text-center text-sm text-destructive">{error}</p>
            )}
            {!loading && kind === 'airline' &&
              airlines.map((a) => (
                <button
                  key={a.code}
                  type="button"
                  className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left hover:bg-muted"
                  onClick={() => onSelect(kind, a)}
                >
                  <Plane className="size-4 text-primary" />
                  <span className="text-sm font-semibold">{a.code}</span>
                  <span className="truncate text-sm text-muted-foreground">{a.shortName} · {a.fullName}</span>
                </button>
              ))}
            {!loading && (kind === 'dep-airport' || kind === 'arr-airport') &&
              airports.map((a) => (
                <button
                  key={`${a.iata}-${a.icao}`}
                  type="button"
                  className="flex w-full flex-col rounded-xl px-3 py-2 text-left hover:bg-muted"
                  onClick={() => onSelect(kind, a)}
                >
                  <span className="text-sm font-semibold">
                    {a.iata} — {a.name}
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {a.city}, {a.country}
                  </span>
                </button>
              ))}
            {!loading && kind === 'line' &&
              lines.map((l) => (
                <button
                  key={`${l.line}-${l.company}`}
                  type="button"
                  className="flex w-full flex-col rounded-xl px-3 py-2 text-left hover:bg-muted"
                  onClick={() => onSelect(kind, l)}
                >
                  <span className="text-sm font-semibold">{l.line}</span>
                  <span className="text-xs text-muted-foreground">{l.company}</span>
                </button>
              ))}
            {!loading && (kind === 'dep-station' || kind === 'arr-station') &&
              stations.map((s, i) => (
                <button
                  key={`${s.name}-${s.line}-${i}`}
                  type="button"
                  className="flex w-full flex-col rounded-xl px-3 py-2 text-left hover:bg-muted"
                  onClick={() => onSelect(kind, s)}
                >
                  <span className="text-sm font-semibold">{s.name}</span>
                  <span className="text-xs text-muted-foreground">
                    {s.company} · {s.line}
                  </span>
                </button>
              ))}
            {!loading && (kind === 'photo-place' || kind === 'dep-place' || kind === 'dest-place') &&
              places.map((p, i) => (
                <button
                  key={`${p.name}-${p.latitude}-${i}`}
                  type="button"
                  className="flex w-full flex-col rounded-xl px-3 py-2 text-left hover:bg-muted"
                  onClick={() => onSelect(kind, p)}
                >
                  <span className="text-sm font-semibold">{p.name}</span>
                  <span className="text-xs text-muted-foreground">
                    {p.subtitle ? `${p.subtitle} · ` : ''}
                    {formatLatLon(p.latitude, p.longitude, 4)}
                  </span>
                </button>
              ))}
            {!loading && !error && !hasResults(kind, airports, stations, lines, places, airlines) && (
              <p className="px-3 py-8 text-center text-sm text-muted-foreground">
                候補が見つかりません。別のキーワードで検索してください。
              </p>
            )}
          </div>
        </ScrollArea>
      </DialogContent>
    </Dialog>
  )
}

function hasResults(
  kind: SearchKind | null,
  airports: AirportRecord[],
  stations: StationRecord[],
  lines: RailLineEntry[],
  places: PlaceCandidate[],
  airlines: AirlineEntry[],
): boolean {
  if (!kind) return true
  if (kind === 'dep-airport' || kind === 'arr-airport') return airports.length > 0
  if (kind === 'line') return lines.length > 0
  if (kind === 'dep-station' || kind === 'arr-station') return stations.length > 0
  if (kind === 'photo-place' || kind === 'dep-place' || kind === 'dest-place') return places.length > 0
  return airlines.length > 0
}
