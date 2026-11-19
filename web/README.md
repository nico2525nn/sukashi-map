# Sukashi Map Web

写真・動画にフライト、鉄道、移動ルート、スポットの地図透かしを合成する Web アプリです。Android 版 `Sukashi Map` の UI 内容と操作フローに準拠し、UI コンポーネントは shadcn/ui で構成しています。画像処理はすべてブラウザ内で完結し、アップロードは行いません。

## スタック

- React 19 + TypeScript + Vite
- Tailwind CSS v4 + shadcn/ui (Radix)
- Zustand（スタイル・設定・履歴の永続化）
- Rust → WebAssembly (`wasm/geo-core`): 座標パース/整形、大圏航路、距離、RDP 簡略化
- Canvas 2D: 写真・動画のプレビュー、地図・テキスト透かし、JPEG/PNG/WebM 書き出し

## 開発

```bash
npm ci
npm run dev
```

Wasm を再ビルドする場合（Rust + wasm-pack が必要）:

```bash
npm run build:wasm
```

生成物は `src/wasm/geo/` にコミットしています。CI では Rust ツールチェーン不要です。

## テスト・ビルド

```bash
npm run test        # Vitest
npm run build       # tsc -b && vite build
npm run lint        # oxlint
```

## GitHub Pages

公開 URL: **https://nico2525nn.github.io/sukashi-map/**

`.github/workflows/web-pages.yml` が `web/**` の変更時に `web/dist` を GitHub Pages へデプロイします。プロジェクトページのサブパス `/sukashi-map/` を考慮し、`GITHUB_ACTIONS=true` のとき Vite の `base` を切り替えています。ローカルで同じ条件を確認する場合:

```bash
GITHUB_ACTIONS=true npm run build
```

GitHub リポジトリの Settings → Pages → Source は **GitHub Actions** を選択してください。地図データは `public/data` から `import.meta.env.BASE_URL` 経由で取得します。

## 機能

- 4モード: フライト / 鉄道 / 移動ルート / スポット
- 撮影地: EXIF GPS 自動読み取り、場所検索、緯度・経度（小数・度分秒）、現在地、クリア
- 表示: 地名 / 緯度・経度 / 非表示、日付 / 日時、文字・地図の四隅配置
- スタイル: 7フォント、文字/ルート/海岸線色、地図・飛行機バッジ表示、プリセット
- 切り抜き: 元画像 / フリー / 4:5 / 9:16 / 2:3 / 1:1、ズーム・位置、余白枠
- 編集: テキスト・地図のドラッグ、角ハンドル拡大縮小、地図のホイール/ピンチズーム
- 書き出し: JPEG / PNG、動画 MP4 / WebM（ブラウザ対応に応じて自動選択、音声はベストエフォート）、Web Share API
- カメラ: `getUserMedia` によるライブプレビュー + 透かし合成 + 撮影
- レスポンシブ: モバイルはボトムシート、デスクトップは右サイドシート

## データ出典

- 海岸線・行政区域・鉄道駅: 国土交通省 国土数値情報 (N02 / N03)
- 湖沼: 国土地理院 地球地図日本
- 世界地図・空港: Natural Earth / U.S. Census Bureau / OpenFlights
- 地名検索: Photon / © OpenStreetMap contributors
- 道路経路: Project OSRM / © OpenStreetMap contributors
- 逆ジオコーディング: Nominatim / © OpenStreetMap contributors
- フォント: Outfit, Barlow, JetBrains Mono, DM Sans, Kaisei Tokumin (SIL Open Font License 1.1)

## ブラウザ依存の制約

- 動画はブラウザが対応する MP4 / WebM を自動選択します（Safari は MP4、Chrome は WebM が中心）。
- HDR ゲインマップの保持は未対応です。
- 写真の EXIF GPS はブラウザが取得できる場合のみ読み取ります。取得できない場合は手動設定できます。
