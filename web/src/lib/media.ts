import type { MediaAsset } from '@/types'

export function fileKind(file: File): 'image' | 'video' {
  if (file.type.startsWith('video/')) return 'video'
  return 'image'
}

export function mediaFromFile(file: File): Promise<MediaAsset> {
  const kind = fileKind(file)
  const url = URL.createObjectURL(file)
  return new Promise((resolve, reject) => {
    if (kind === 'image') {
      const img = new Image()
      img.onload = () => {
        resolve({
          url,
          name: file.name || 'photo.jpg',
          type: 'image',
          width: img.naturalWidth,
          height: img.naturalHeight,
          file,
          capturedAt: new Date(),
        })
      }
      img.onerror = () => {
        URL.revokeObjectURL(url)
        reject(new Error('画像を読み込めませんでした'))
      }
      img.src = url
    } else {
      const video = document.createElement('video')
      video.preload = 'metadata'
      video.onloadedmetadata = () => {
        resolve({
          url,
          name: file.name || 'movie.mp4',
          type: 'video',
          width: video.videoWidth,
          height: video.videoHeight,
          file,
          capturedAt: new Date(),
        })
      }
      video.onerror = () => {
        URL.revokeObjectURL(url)
        reject(new Error('動画を読み込めませんでした'))
      }
      video.src = url
    }
  })
}

export function blobToFile(blob: Blob, name: string): File {
  return new File([blob], name, { type: blob.type || 'image/jpeg' })
}

export function formatDuration(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds))
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

export async function captureFrame(video: HTMLVideoElement): Promise<Blob> {
  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth
  canvas.height = video.videoHeight
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Canvas 2D context unavailable')
  ctx.drawImage(video, 0, 0)
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('撮影に失敗しました'))), 'image/jpeg', 0.95)
  })
}
