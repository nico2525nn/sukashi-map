# Sukashi Map

![Sukashi Map](docs/images/readme-hero-m3.png)

写真・動画にフライト・鉄道・移動ルートと地図の透かしを合成するオープンソースアプリです。Android アプリに加え、共通の操作体系を持つ Web UI (`web/`) を GitHub Pages で公開しています。

- Web UI: https://nico2525nn.github.io/sukashi-map/
- Web UI の開発手順: [web/README.md](web/README.md)
Material Design 3 Expressive (`androidx.compose.material3:material3:1.5.0-rc01`) 準拠のシンプルな単一ワークスペースで構成されています。

## 機能

- **4つの透かしモード**: フライト（オフライン空港DB・大圏航路アーク）、鉄道（国土数値情報 N02 駅・軌道DB）、移動ルート（OSRM 道路経路）、スポット（3行テキスト・地図ピン）
- **経路の共通ルール**: 撮影地を道路経由点にする判定は Android / Web で同一（直線距離の145%以内）。鉄道は会社と路線名の組で形状を選択。仕様は [docs/route-behavior.md](docs/route-behavior.md)
- **撮影地の設定**: 写真の EXIF GPS を自動読み取り。地名検索・度分秒/小数の緯度経度入力・端末の現在地から後付け設定も可能。地名 / 緯度・経度 / 非表示を切り替え
- **要素別サイズ**: 全体・タイトル・サブタイトル・座標/地名・日付・地図をそれぞれ50〜250%で調整
- **配置**: テキストと地図をそれぞれ左上・右上・左下・右下から独立して選択。ドラッグ移動、角ハンドル拡大縮小 (`0.5×–2.5×`)、2本指地図ズーム・パン (`0.5×–5.0×`)
- **スタイル調整**: 文字色・ルート線色・海岸線色（プリセット・カスタムHSV/HEX・写真スポイト）、9種のフォント（Noto Sans JP / Noto Serif JP を含む）、切り抜き・余白枠
- **高画質保存・カメラ撮影**: 写真（JPEG / PNG、Android 14+ Ultra HDR 保持）、動画（Media3 Transformer 元解像度オーバーレイ書き出し）、CameraX ライブ透過プレビュー撮影

## バージョニング

Android と Web は同じバージョンを使用します。

- 新機能: `0.1` を加算（例: `0.1.3` → `0.2.0`）
- 不具合修正: `0.0.1` を加算（例: `0.2.0` → `0.2.1`）
- GitHub Release は `vX.Y.Z` タグで作成し、Android APK と Web ビルドを同一リリースに含める
- Android の `versionName` と Web の `package.json` の `version` は常に一致させる
- Android の `versionCode` はリリースごとに +1 する

## ビルド

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

リリース APK は署名設定が必須です。鍵の生成・`keystore.properties`・CI シークレット・署名検証手順は [docs/releasing.md](docs/releasing.md) を参照してください。デバッグ鍵でリリース APK を署名することはありません。

## 位置情報について

Android 10 以降は `ACCESS_MEDIA_LOCATION` がないと写真の EXIF GPS が伏字になります。本アプリは共有された写真でこの権限を要求し、対応端末の Photo Picker では位置情報の共有可否を選択できます。許可しない場合でも、透かし情報シートの「撮影地」から地名検索・座標入力・現在地で設定できます。

JPEG に書き込む GPS は、実際に設定された撮影座標のみです。撮影地ラベルを「非表示」にした場合や撮影地を未設定にした場合は GPS を書き込まず、出発空港・出発駅などの座標を撮影位置として代用することもありません。

## データ出典

- 海岸線・行政区域・鉄道データ: 国土交通省 国土数値情報 (N03 / N02)
- 湖沼データ: 国土地理院 地球地図日本
- 世界地図・空港: Natural Earth / U.S. Census Bureau / OpenFlights
- 道路経路・地名検索: © OpenStreetMap contributors / Project OSRM / Photon
- フォント: Barlow, JetBrains Mono, DM Sans, Outfit, Kaisei Tokumin (SIL Open Font License 1.1)
