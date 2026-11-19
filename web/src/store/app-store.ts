import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import {
  defaultCropFrame,
  defaultOverlayStyle,
  defaultTransportInfo,
  type AppSettings,
  type CropFrameSettings,
  type MediaAsset,
  type OverlayStyle,
  type StylePreset,
  type TransportInfo,
  type TransportMode,
} from '@/types'

const defaultPresets: StylePreset[] = [
  {
    id: 'classic-white',
    name: 'Classic White',
    style: { ...defaultOverlayStyle },
  },
  {
    id: 'midnight',
    name: 'Midnight',
    style: {
      ...defaultOverlayStyle,
      font: 'jetbrains-mono',
      textColor: '#f5f5f5',
      routeColor: '#ffb900',
      borderColor: '#cfd8dc',
    },
  },
  {
    id: 'amber',
    name: 'Amber Film',
    style: {
      ...defaultOverlayStyle,
      font: 'kaisei-tokumin',
      textColor: '#ffd08a',
      routeColor: '#ffb900',
      borderColor: '#ffd08a',
    },
  },
]

interface AppState {
  media: MediaAsset | null
  info: TransportInfo
  style: OverlayStyle
  crop: CropFrameSettings
  settings: AppSettings
  history: TransportInfo[]
  presets: StylePreset[]
  /** Bumped whenever committed info or the active media changes. */
  infoRevision: number
  setMedia: (media: MediaAsset | null) => void
  updateInfo: (patch: Partial<TransportInfo>) => void
  /** Writes only if the revision still matches; returns whether it wrote. */
  updateInfoIfRevision: (revision: number, patch: Partial<TransportInfo>) => boolean
  applyInfo: (next: TransportInfo, saveToHistory?: boolean) => number
  updateStyle: (patch: Partial<OverlayStyle>) => void
  resetLayout: () => void
  updateCrop: (patch: Partial<CropFrameSettings>) => void
  resetCrop: () => void
  updateSettings: (patch: Partial<AppSettings>) => void
  savePreset: (name: string) => void
  deletePreset: (id: string) => void
  recordHistory: (info: TransportInfo) => void
  clearHistory: (mode?: TransportMode) => void
}

function compactHistory(history: TransportInfo[], entry: TransportInfo): TransportInfo[] {
  const sameMode = history
    .filter((h) => h.mode === entry.mode && JSON.stringify(h) !== JSON.stringify(entry))
    .slice(0, 4)
  const otherModes = history.filter((h) => h.mode !== entry.mode).slice(0, 15)
  return [entry, ...sameMode, ...otherModes].slice(0, 20)
}

export const useAppStore = create<AppState>()(
  persist(
    (set, get) => ({
      media: null,
      info: defaultTransportInfo,
      style: defaultOverlayStyle,
      crop: defaultCropFrame,
      settings: {
        themeMode: 'SYSTEM',
        dynamicColor: true,
        exportFormat: 'JPEG_95',
        autoLocationEnabled: true,
        preserveHdr: true,
      },
      history: [],
      presets: defaultPresets,
      infoRevision: 0,
      setMedia: (media) =>
        set((s) => {
          if (s.media?.url && s.media.url !== media?.url) URL.revokeObjectURL(s.media.url)
          return { media, infoRevision: s.infoRevision + 1 }
        }),
      updateInfo: (patch) =>
        set((s) => ({
          info: { ...s.info, ...patch },
          infoRevision: s.infoRevision + 1,
        })),
      updateInfoIfRevision: (revision, patch) => {
        const s = get()
        if (s.infoRevision !== revision) return false
        set({ info: { ...s.info, ...patch }, infoRevision: s.infoRevision + 1 })
        return true
      },
      applyInfo: (next, saveToHistory = true) => {
        const revision = get().infoRevision + 1
        set({ info: next, infoRevision: revision })
        if (saveToHistory) get().recordHistory(next)
        return revision
      },
      updateStyle: (patch) => set((s) => ({ style: { ...s.style, ...patch } })),
      resetLayout: () =>
        set((s) => ({
          style: {
            ...s.style,
            textFreePos: null,
            mapFreePos: null,
            textScale: 1,
            mapBoxScale: 1,
            mapInternalZoom: 1,
            mapInternalPanX: 0,
            mapInternalPanY: 0,
          },
        })),
      updateCrop: (patch) => set((s) => ({ crop: { ...s.crop, ...patch } })),
      resetCrop: () => set({ crop: { ...defaultCropFrame } }),
      updateSettings: (patch) => set((s) => ({ settings: { ...s.settings, ...patch } })),
      savePreset: (name) => {
        const { style, presets } = get()
        const preset: StylePreset = {
          id: `custom_${Date.now()}`,
          name: name.trim() || 'My Preset',
          style: { ...style },
        }
        set({ presets: [...presets, preset] })
      },
      deletePreset: (id) => set((s) => ({ presets: s.presets.filter((p) => p.id !== id) })),
      recordHistory: (entry) =>
        set((s) => ({ history: compactHistory(s.history, structuredClone(entry)) })),
      clearHistory: (mode) =>
        set((s) => ({
          history: mode ? s.history.filter((h) => h.mode !== mode) : [],
        })),
    }),
    {
      name: 'sukashi-map-web',
      version: 2,
      migrate: (persisted) => {
        const old = persisted as Partial<AppState>
        return {
          style: old.style ?? defaultOverlayStyle,
          settings: old.settings ?? {
            themeMode: 'SYSTEM',
            dynamicColor: true,
            exportFormat: 'JPEG_95',
            autoLocationEnabled: true,
            preserveHdr: true,
          },
          history: old.history ?? [],
          presets: old.presets ?? defaultPresets,
        }
      },
      partialize: (s) => ({
        style: s.style,
        settings: s.settings,
        history: s.history.map((h) => ({ ...h, routePolylines: [] })),
        presets: s.presets,
      }),
    },
  ),
)
