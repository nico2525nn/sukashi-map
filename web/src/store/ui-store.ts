import { create } from 'zustand'

export type SheetKind = 'info' | 'style' | 'crop' | 'settings' | null

interface SheetOpener {
  element: HTMLElement
  label: string | null
}

function captureOpener(): SheetOpener | null {
  const active = document.activeElement
  if (!(active instanceof HTMLElement) || active === document.body) return null
  return {
    element: active,
    label: active.getAttribute('aria-label') ?? active.textContent?.trim().slice(0, 32) ?? null,
  }
}

interface UiState {
  sheet: SheetKind
  sheetOpener: SheetOpener | null
  cameraOpen: boolean
  openSheet: (sheet: Exclude<SheetKind, null>) => void
  closeSheet: () => void
  setCameraOpen: (open: boolean) => void
}

export const useUiStore = create<UiState>((set) => ({
  sheet: null,
  sheetOpener: null,
  cameraOpen: false,
  openSheet: (sheet) => set({ sheet, sheetOpener: captureOpener() }),
  closeSheet: () => set({ sheet: null }),
  setCameraOpen: (cameraOpen) => set({ cameraOpen }),
}))
