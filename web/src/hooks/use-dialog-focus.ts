import { useRef } from 'react'

/**
 * Radix restores focus to a DialogTrigger automatically. These dialogs are
 * controlled from menu buttons, so remember the opener explicitly.
 */
export function useDialogFocusReturn() {
  const openerRef = useRef<HTMLElement | null>(null)

  return {
    capture: (element?: HTMLElement | null) => {
      const active = element ?? (document.activeElement instanceof HTMLElement ? document.activeElement : null)
      if (active && active !== document.body) openerRef.current = active
    },
    onCloseAutoFocus: (event: Event) => {
      const opener = openerRef.current
      if (!opener?.isConnected) return
      event.preventDefault()
      opener.focus()
    },
  }
}
