import { useEffect, useState } from 'react'

export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() =>
    typeof window !== 'undefined' ? window.matchMedia(query).matches : false,
  )
  useEffect(() => {
    const mql = window.matchMedia(query)
    const onChange = () => setMatches(mql.matches)
    onChange()
    mql.addEventListener('change', onChange)
    return () => mql.removeEventListener('change', onChange)
  }, [query])
  return matches
}

export function useIsMobile(): boolean {
  return useMediaQuery('(max-width: 767px)')
}

/** Phones in landscape have very little vertical room for a bottom sheet. */
export function useIsShortLandscape(): boolean {
  return useMediaQuery('(max-height: 520px) and (orientation: landscape)')
}

export function useIsCoarsePointer(): boolean {
  return useMediaQuery('(pointer: coarse)')
}
