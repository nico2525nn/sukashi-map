import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
  message: string
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, message: '' }

  static getDerivedStateFromError(error: unknown): State {
    return { hasError: true, message: error instanceof Error ? error.message : String(error) }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Sukashi Map Web crashed', error, info.componentStack)
  }

  render() {
    if (!this.state.hasError) return this.props.children
    return (
      <div className="flex min-h-dvh items-center justify-center bg-background p-6">
        <div className="w-full max-w-md rounded-3xl border border-border/70 bg-card p-6 text-center shadow-xl">
          <h1 className="text-lg font-bold">問題が発生しました</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            ページを再読み込みしてください。画像はブラウザ内に留まっており、アップロードされていません。
          </p>
          <pre className="mt-3 max-h-32 overflow-auto rounded-xl bg-muted p-3 text-left text-[11px] text-muted-foreground">
            {this.state.message}
          </pre>
          <button
            type="button"
            className="mt-4 w-full rounded-full bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground"
            onClick={() => window.location.reload()}
          >
            再読み込み
          </button>
        </div>
      </div>
    )
  }
}
