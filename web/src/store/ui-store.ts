import { create } from 'zustand'

export type SheetKind = 'info' | 'style' | 'crop' | 'settings' | null

interface UiState {
  sheet: SheetKind
  cameraOpen: boolean
  openSheet: (sheet: Exclude<SheetKind, null>) => void
  closeSheet: () => void
  setCameraOpen: (open: boolean) => void
}

export const useUiStore = create<UiState>((set) => ({
  sheet: null,
  cameraOpen: false,
  openSheet: (sheet) => set({ sheet }),
  closeSheet: () => set({ sheet: null }),
  setCameraOpen: (cameraOpen) => set({ cameraOpen }),
}))
