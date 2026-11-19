import { useEffect, useState } from 'react'
import type { MediaAsset } from '@/types'

export function useMediaElement(media: MediaAsset | null) {
  const [image, setImage] = useState<HTMLImageElement | null>(null)
  const [video, setVideo] = useState<HTMLVideoElement | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setImage(null)
    setVideo(null)
    setError(null)
    if (!media) return
    if (media.type === 'image') {
      const img = new Image()
      img.decoding = 'async'
      img.onload = () => setImage(img)
      img.onerror = () => setError('画像を読み込めませんでした')
      img.src = media.url
      const timer = window.setTimeout(() => {
        if (!img.complete) setError('画像の読み込みがタイムアウトしました')
      }, 20_000)
      return () => {
        window.clearTimeout(timer)
        img.onload = null
        img.onerror = null
      }
    }
    const el = document.createElement('video')
    el.src = media.url
    el.loop = true
    el.muted = true
    el.playsInline = true
    el.preload = 'auto'
    // iOS only reliably plays/draws video elements attached to the document.
    el.style.position = 'fixed'
    el.style.left = '-10000px'
    el.style.width = '1px'
    el.style.height = '1px'
    el.style.opacity = '0'
    el.setAttribute('aria-hidden', 'true')
    document.body.appendChild(el)
    el.onerror = () => setError('動画を読み込めませんでした')
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
      el.remove()
    }
  }, [media])

  return { image, video, error }
}
