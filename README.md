# Sukashi Map

写真・動画にフライト・鉄道・移動ルートと地図の透かしを合成するオープンソースアプリです。Android アプリに加え、同じ UX の Web UI (`web/`) を GitHub Pages で公開しています。

- Web UI: https://nico2525nn.github.io/sukashi-map/
- Web UI の開発手順: [web/README.md](web/README.md)
Material Design 3 Expressive (`androidx.compose.material3:material3:1.5.0-rc01`) 準拠のシンプルな単一ワークスペースで構成されています。

## 機能

- **4つの透かしモード**: フライト（オフライン空港DB・大圏航路アーク）、鉄道（国土数値情報 N02 駅・軌道DB）、移動ルート（OSRM 道路経路）、スポット（3行テキスト・地図ピン）
- **撮影地の設定**: 写真の EXIF GPS を自動読み取り。地名検索・度分秒/小数の緯度経度入力・端末の現在地から後付け設定も可能。地名 / 緯度・経度 / 非表示を切り替え
- **インタラクティブ配置**: テキスト・地図の自由ドラッグ移動、角ハンドル拡大縮小 (`0.5×–2.5×`)、2本指地図ズーム・パン (`0.5×–5.0×`)
- **スタイル調整**: 文字色・ルート線色・海岸線色（プリセット・カスタムHSV/HEX・写真スポイト）、7種のフォント、切り抜き・余白枠
- **高画質保存・カメラ撮影**: 写真（JPEG / PNG、Android 14+ Ultra HDR 保持）、動画（Media3 Transformer 元解像度オーバーレイ書き出し）、CameraX ライブ透過プレビュー撮影

## ビルド

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## 位置情報について

Android 10 以降は `ACCESS_MEDIA_LOCATION` がないと写真の EXIF GPS が伏字になります。本アプリは共有された写真でこの権限を要求し、対応端末の Photo Picker では位置情報の共有可否を選択できます。許可しない場合でも、透かし情報シートの「撮影地」から地名検索・座標入力・現在地で設定できます。

## データ出典

- 海岸線・行政区域・鉄道データ: 国土交通省 国土数値情報 (N03 / N02)
- 湖沼データ: 国土地理院 地球地図日本
- 世界地図・空港: Natural Earth / U.S. Census Bureau / OpenFlights
- 道路経路・地名検索: © OpenStreetMap contributors / Project OSRM / Photon
- フォント: Barlow, JetBrains Mono, DM Sans, Outfit, Kaisei Tokumin (SIL Open Font License 1.1)
