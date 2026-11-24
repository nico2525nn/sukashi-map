import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defaultCropFrame, defaultOverlayStyle, defaultTransportInfo } from '@/types'
import { EMPTY_MAP_DATASETS } from '@/lib/map-data'
import { exportVideo } from '@/lib/export'

vi.mock('@/lib/renderer', () => ({
  renderOverlay: vi.fn(),
}))

const fixState = vi.hoisted(() => ({ hang: false }))
vi.mock('fix-webm-duration', () => ({
  default: (blob: Blob) =>
    fixState.hang ? new Promise<Blob>(() => undefined) : Promise.resolve(blob),
}))

class FakeMediaRecorder {
  static isTypeSupported = () => true
  static failOnStart = false
  static emitData = true
  static hangOnStop = false
  static instances: FakeMediaRecorder[] = []
  state: 'inactive' | 'recording' = 'inactive'
  mimeType = 'video/webm;codecs=vp8'
  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null
  onerror: ((event: { error: Error }) => void) | null = null

  constructor() {
    FakeMediaRecorder.instances.push(this)
  }

  start() {
    this.state = 'recording'
    if (FakeMediaRecorder.failOnStart) {
      setTimeout(() => this.onerror?.({ error: new Error('encoder exploded') }), 0)
    }
  }

  stop() {
    this.state = 'inactive'
    if (FakeMediaRecorder.hangOnStop) return
    if (FakeMediaRecorder.emitData) {
      this.ondataavailable?.({ data: new Blob(['frame'], { type: this.mimeType }) })
    }
    this.onstop?.()
  }
}

function fakeVideo(
  options: { hangPlay?: boolean; withAudioApi?: boolean; lateAudio?: boolean } = {},
): HTMLVideoElement {
  const listeners = new Map<string, () => void>()
  let audioStarted = false
  const lateTracks = [{ id: 'late-audio', kind: 'audio', stop: () => undefined }]
  const video = {
    videoWidth: 320,
    videoHeight: 240,
    duration: 1,
    currentTime: 0,
    paused: false,
    ended: false,
    play: () =>
      options.hangPlay
        ? new Promise<void>(() => undefined)
        : new Promise<void>((resolve) => {
            audioStarted = true
            setTimeout(() => {
              video.ended = true
              listeners.get('ended')?.()
              resolve()
            }, 10)
          }),
    pause: () => undefined,
    addEventListener: (name: string, listener: () => void) => listeners.set(name, listener),
    removeEventListener: (name: string) => listeners.delete(name),
  }
  if (options.withAudioApi !== false) {
    ;(video as unknown as { captureStream: () => MediaStream }).captureStream = () =>
      ({
        getAudioTracks: () => (options.lateAudio && audioStarted ? lateTracks : []),
      }) as unknown as MediaStream
  }
  return video as unknown as HTMLVideoElement
}

function fakeStream() {
  const tracks: Array<{ id: string; kind: string; stop: () => void }> = []
  return {
    addTrack: (track: { id: string; kind: string; stop: () => void }) => {
      if (!tracks.some((existing) => existing.id === track.id)) tracks.push(track)
    },
    getAudioTracks: () => tracks,
    getTracks: () => tracks,
  }
}

function fakeCanvas() {
  const stream = fakeStream()
  return {
    width: 0,
    height: 0,
    getContext: () => ({
      clearRect: vi.fn(),
      fillRect: vi.fn(),
      drawImage: vi.fn(),
      fillStyle: '',
    }),
    captureStream: () => stream,
  }
}

class FakeHtmlCanvasElement {
  captureStream() {
    return fakeStream()
  }
}

beforeEach(() => {
  FakeMediaRecorder.instances = []
  FakeMediaRecorder.failOnStart = false
  FakeMediaRecorder.emitData = true
  FakeMediaRecorder.hangOnStop = false
  fixState.hang = false
  vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
  vi.stubGlobal('HTMLCanvasElement', FakeHtmlCanvasElement)
  vi.stubGlobal('requestAnimationFrame', () => 0)
  vi.stubGlobal('cancelAnimationFrame', () => undefined)
  vi.stubGlobal('document', {
    createElement: (tag: string) =>
      tag === 'canvas' ? (fakeCanvas() as unknown as HTMLCanvasElement) : ({} as HTMLElement),
  })
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

const options = (video = fakeVideo()) => ({
  video,
  info: defaultTransportInfo,
  style: defaultOverlayStyle,
  crop: defaultCropFrame,
  datasets: EMPTY_MAP_DATASETS,
})

describe('exportVideo recorder failures', () => {
  it('rejects promptly when MediaRecorder reports an error', async () => {
    FakeMediaRecorder.failOnStart = true
    await expect(exportVideo(options())).rejects.toThrow('動画の書き出しに失敗しました')
  })

  it('rejects a successful stop that produced no data', async () => {
    FakeMediaRecorder.emitData = false
    await expect(exportVideo(options())).rejects.toThrow('動画データが空です')
  })

  it('does not wait forever for a stalled play()', async () => {
    const controller = new AbortController()
    setTimeout(() => controller.abort(), 20)
    await expect(
      exportVideo({ ...options(fakeVideo({ hangPlay: true })), signal: controller.signal }),
    ).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('does not wait forever for a stopped recorder that never fires onstop', async () => {
    const controller = new AbortController()
    FakeMediaRecorder.hangOnStop = true
    setTimeout(() => controller.abort(), 30)
    await expect(
      exportVideo({ ...options(), signal: controller.signal }),
    ).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('reports audio tracks that appear only after playback starts', async () => {
    const result = await exportVideo(options(fakeVideo({ lateAudio: true })))
    expect(result.hasAudio).toBe(true)
    expect(result.audioCaptureFailed).toBe(false)
  })

  it('stops waiting if the WebM duration fix is cancelled', async () => {
    fixState.hang = true
    const controller = new AbortController()
    setTimeout(() => controller.abort(), 50)
    await expect(
      exportVideo({ ...options(), signal: controller.signal }),
    ).rejects.toMatchObject({ name: 'AbortError' })
    fixState.hang = false
  })

  it('reports whether audio was captured and whether the API was available', async () => {
    const result = await exportVideo(options())
    expect(result.extension).toBe('webm')
    expect(result.hasAudio).toBe(false)
    expect(result.audioCaptureFailed).toBe(false)

    const noApi = await exportVideo(options(fakeVideo({ withAudioApi: false })))
    expect(noApi.audioCaptureFailed).toBe(true)
  })
})
