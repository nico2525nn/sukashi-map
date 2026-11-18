# Sukashi Map

写真・動画にフライト・鉄道・移動ルートと地図の透かしを合成するオープンソース Android アプリです。
Material Design 3 Expressive (`androidx.compose.material3:material3:1.5.0-rc01`) 準拠のシンプルな単一ワークスペースで構成されています。

## 機能

- **4つの透かしモード**: フライト（オフライン空港DB・大圏航路アーク）、鉄道（国土数値情報 N02 駅・軌道DB）、移動ルート（OSRM 道路経路）、スポット（3行テキスト・地図ピン）
- **インタラクティブ配置**: テキスト・地図の自由ドラッグ移動、角ハンドル拡大縮小 (`0.5×–2.5×`)、2本指地図ズーム・パン (`0.5×–5.0×`)
- **スタイル調整**: 文字色・ルート線色・海岸線色（プリセット・カスタムHSV/HEX・写真スポイト）、7種のフォント、切り抜き・余白枠
- **高画質保存・カメラ撮影**: 写真（JPEG / PNG、Android 14+ Ultra HDR 保持）、動画（Media3 Transformer 元解像度オーバーレイ書き出し）、CameraX ライブ透過プレビュー撮影

## ビルド

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## データ出典

- 海岸線・行政区域・鉄道データ: 国土交通省 国土数値情報 (N03 / N02)
- 湖沼データ: 国土地理院 地球地図日本
- 世界地図・空港: Natural Earth / U.S. Census Bureau / OpenFlights
- 道路経路・地名検索: © OpenStreetMap contributors / Project OSRM / Photon
- フォント: Barlow, JetBrains Mono, DM Sans, Outfit, Kaisei Tokumin (SIL Open Font License 1.1)
