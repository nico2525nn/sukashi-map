# Sukashi Map (透かしマップ)

旅行の移動情報を写真・動画に合成する Material 3 カメラアプリ。
地図つきオーバーレイ撮影、ギャラリー、Trip Recap ポスター作成、Passage Plus 相当の
プレミアム解除を備える。クリーンルーム再実装 (コード・文言・素材は全て自作)。

機能仕様は [SPEC.md](SPEC.md) を参照。

## 必要環境
- JDK 17+
- Android SDK: platform android-36, build-tools 36.0.0
- `local.properties` に `sdk.dir` (または ANDROID_HOME)

## APIキー (任意・空でも動作、機能縮退あり)
`local.properties` または環境変数:
```properties
MAPS_API_KEY=...
ADMOB_APP_ID=...
ADMOB_BANNER=...
ADMOB_INTERSTITIAL=...
ADMOB_REWARDED=...
BILLING_PRODUCT_IDS=passage_plus_2days,passage_plus_1week,passage_plus_1year
```

## ビルド
```bash
./gradlew :app:assembleDebug
```

## データ生成
```bash
python3 tools/mapdata/build_mapdata.py  # assets用地図JSON生成
```

## 地図・データの出典
- 世界地図: Natural Earth (public domain)
- 日本都道府県境: 地球地図日本 (国土地理院) / dataofjapan 加工
- 米国州境: U.S. Census Bureau
- 空港・航空会社: OpenFlights (ODbL)
- 鉄道: 国土交通省 国土数値情報 N02 (mlit-json 加工)
- Trip Recap タイル: © OpenFreeMap © OpenMapTiles © OpenStreetMap contributors / 地点検索: Photon
