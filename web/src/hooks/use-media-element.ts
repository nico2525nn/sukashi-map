import { useEffect, useState } from 'react'
import type { MediaAsset } from '@/types'

export function useMediaElement(media: MediaAsset | null) {
  const [image, setImage] = useState<HTMLImageElement | null>(null)
  const [video, setVideo] = useState<HTMLVideoElement | null>(null)

  useEffect(() => {
    setImage(null)
    setVideo(null)
    if (!media) return
    if (media.type === 'image') {
      const img = new Image()
      img.decoding = 'async'
      img.onload = () => setImage(img)
      img.src = media.url
      return () => {
        img.onload = null
      }
    }
    const el = document.createElement('video')
    el.src = media.url
    el.loop = true
    el.muted = true
    el.playsInline = true
    el.preload = 'auto'
    const onReady = () => {
      setVideo(el)
      void el.play().catch(() => undefined)
    }
    el.addEventListener('loadeddata', onReady, { once: true })
    return () => {
      el.removeEventListener('loadeddata', onReady)
      el.pause()
      el.removeAttribute('src')
      el.load()
    }
  }, [media])

  return { image, video }
}
