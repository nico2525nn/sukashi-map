# Sukashi Map — 機能仕様 (SPEC)

Passage 系「透かしマップカメラ」のクリーンルーム再実装。コード・文言・素材は全て自作。
原APK (com.flightphoto.passage v1.6.1/vc52) の動作解析に基づく。

## 0. 用語
- オーバーレイ: 写真/動画に焼き込む交通情報テキスト+地図+ロゴの合成レイヤー
- Plus: 有効期限付きプレミアム(2日/1週間/1年パス)。広告除去・自由配置・ロゴ非表示・高画質等の解除キー

## 1. 画面一覧 (11 Activity + BottomSheets)
| # | 画面 | 起点 | 役割 |
|---|------|------|------|
| 1 | Launch | ランチャー | 背景写真+タイトル。撮影開始 / アルバムから作成 / ギャラリー / 設定 |
| 2 | Main(撮影) | Launch・共有受付 | CameraXプレビュー+交通オーバーレイ表示。写真/動画撮影→プレビュー→保存/共有 |
| 3 | TransportInfo入力 | Main ⓘ | BottomSheet(実際はDialogFragment)。移動手段別フォーム+履歴+詳細設定 |
| 4 | TransportInfoActivity | (予備・単体編集) | 同フォームのActivity版 ※Manifest登録あり |
| 5 | Gallery | Launch・Main | MediaStoreグリッド/地図クラスタ切替、ソート、日付/追加順、TripRecap入口 |
| 6 | GalleryDetail | Gallery | ViewPager2全画面表示、共有・削除・詳細(EXIF)、動画再生 |
| 7 | MapClusterGrid | Gallery地図 | クラスタタップ→該当エリア写真グリッド |
| 8 | TripRecap | Galleryメニュー | 旅行ポスター作成: 地図+写真12枚+訪問地20件、MapLibre+OpenFreeMap |
| 9 | Settings | Launch・Main | Plusバナー、言語、自動ジオタグ、HDR保持、バージョン、About、Privacy、UMP同意 |
| 10 | Paywall(Plus) | 各種ゲート | 3プラン表示・購入・復元、残り時間表示 |
| 11 | About | Settings | 地図データ出典表示(日英) |
| 12 | PrivacyPolicy | Settings | assets内HTMLをWebView表示 |

### BottomSheets / Dialogs
- TransportInfoBottomSheet (al4, 1619行相当): 移動手段タブ(✈飛行機/🚃電車/🚗他/📍スポット)+履歴チップ+日時+表示形式+配置+地図ON/OFF+詳細(フォント/文字色/線色/枠色)+写真選択
- SaveSuccessBottomSheet: 保存完了+姉妹アプリ(RatioFit)紹介カード
- ImageInfoBottomSheet: ファイル名/パス/サイズ/解像度/撮影日時/追加日時/位置名/座標
- Discard確認 / レビュー促進 / 日時ピッカー / 言語選択 / 終了日時確認 / 調整ゲート(広告orPlus) / 透かしゲート / Cropゲート / TripRecap Plusゲート

## 2. Main撮影フロー (状態機械)
```
[起動] → 権限確認(カメラ必須) → プレビュー(3:4) + flightInfoOverlay(交通テキスト) + 地図プレビュー?
  ├ btnTransportInfo → BottomSheet入力 → TransportInfo確定 → オーバーレイ再描画
  ├ btnCapture(写真) → ImageCapture → overlayPreview表示 + postCaptureControls(破棄/共有×3/保存)
  ├ btnCaptureMode → 写真⇔動画切替
  ├ btnCapture(動画) → 録画開始(赤●タイマー) → 停止 → videoPreview(PlayerView)+seek/mute → postCapture
  ├ btnCropFrame(Plus/広告) → CropFrameEditor(アスペクト/枠/回転)
  ├ btnGallery → Gallery    ├ btnSettings → Settings
  ├ btnFlash → フラッシュ切替  ├ btnFlipCamera → 内/外切替
  ├ btnLocation → 位置更新   ├ btnToggleLocationMode → 地域名⇔座標表示切替
  └ DraggableOverlayView(Plus/広告で自由配置・拡縮) → 保存時に合成
[保存] 写真: Bitmap合成→MediaStore追加→EXIF GPS書込→SaveSuccess / 動画: Media3 Transformerで焼込→保存
[共有] FileProvider URIを chooser / X(com.twitter.android) / Instagram(com.instagram.android) へSEND (+#PassageApp→自タグ)
[外部共有受付] ACTION_SEND 画像/動画 → Mainで開いてオーバーレイ作成
```

## 3. TransportInfo モデル
sealed: Airplane(航空会社/便名/登録記号/出発・到着空港+コード/緯度経度/日時/表示形式/地図位置/透かし/地図ON/OFF)
Train(国内判定/路線名/列車名/車両形式/出発・到着駅+正式名/緯度経度/形状線/geometries/経由線viaLines/最短選択/日時…)
Other(交通名/出発地/目的地/国コード/緯度経度/routePolyline…)
Spot(3行テキスト/日時…)
共通: 現在地(緯度経度/国コード/地名)/日時/日時表示形式(DATE_ONLY/DATE_TIME/NONE)/地図ON/OFF/透かしON/OFF/地図位置(TOP_LEFT/TOP_RIGHT/BOTTOM_LEFT/BOTTOM_RIGHT)
- formattedDate: ロケールMEDIUM/SHORT形式 / formattedCoordinates: `35.68N, 139.69E`
- routeDisplay: `出発 → 到着` (片方のみ可)

## 4. 地図レンダラ (WorldMapRenderer相当)
- 自前Canvas描画。assets内 `[[[lon,lat],…]]` リングJSONを読む
- 世界: coarse(通常)/fine(拡大)。日本: 海岸線highres+都道府県境+河川湖沼。米国: 州境
- モード別: Airplane=大圏2点+現在地 / Train=鉄道形状+駅 / Other=道路polyline(JP内なら詳細地図) / 単点=ズーム円
- 出発マーカー #4FC3F7 / 到着 #FF7043。地図枠: 画面幅45%・高33%(縦)、余白3%、最小アスペクト0.85
- 凡例: 位置情報あれば地図描画 (電車JPは検索リスト選択時のみ)

## 5. データ
- airports.db(Room, prepack): OpenFlights airports.dat→IATA/ICAO/名前/都市/国/緯度経度
- airlines: OpenFlights airlines.dat→コード/名前 (表示 `%2$s: %1$s (%3$s)`)
- railways.db(Room, prepack): 国土数値情報N02由来 (mlit-json: lines/stations/sections)→路線/駅/区間形状
- 新幹線グループ定義 + 経由線チップ選択 + 最短経路判定
- HistoryManager: SharedPreferencesに入力履歴 (チップ表示・クリア可)
- MapLibre style: 自作light/dark (OpenFreeMap glyphs/tiles URLは定数、帰属表示必須)
- 地点検索: Photon API (https://photon.komoot.io) + オフライン時は地図タップ選択
- Timeline取込: Google Maps Timeline JSON → 実測線(太)+推定線(細)

## 6. Gallery
- MediaStore Images+Video (DATE_ADDED/DATE_TAKEN/緯度経度/DURATION)。自作写真フィルタ+全写真切替
- 表示: グリッド(列数1/3/5 ピンチ?/設定) ⇔ Google Mapsクラスタ (SupportMapFragment+android-maps-utils)
- ソート: 追加日順 / 撮影日順(Timeline)。地図⇔衛星切替。ContentObserverで自動更新
- 空/権限/エラー/部分アクセスの各状態表示 + 設定誘導 + リトライ
- Detail: ViewPager2、写真PhotoView(ズーム)/動画ExoPlayer、共有/削除(DeleteRequest+権限)/詳細ボトムシート

## 7. TripRecap (ポスター作成)
- タブ: 地図/写真/比率/スタイル。写真最大12枚(PhotoPicker+MediaStore+絞込: 日付/場所/全て)
- 訪問地最大20件: Photon検索/地図タップ/写真位置/既存訪問流用。順序入替・挿入・削除・改名・日時
- 旅行期間: 開始/終了日 (Timeline取込の条件)
- 地図: MapLibre GL (GLES2必須判定あり→非対応文言)、light/dark、国名/地名表示切替、経路色、全体表示
- 比率: 正方/4:5/2:3/9:16/丸?/自由、ズーム、角丸、背景色、フォント選択、文字色
- 保存: 2048px標準 / 4096px高画質(Plusor広告)。メモリ不足・写真読込失敗・高解像度地図失敗の個別エラー
- 破棄確認ダイアログあり

## 8. Plus/広告/課金
- 商品: passage_plus_2days / passage_plus_1week / passage_plus_1year (INAPP、非自動更新、UTC起算+2日/+7日/+1年)
- 権利: plus_entitlement_v2.json (AtomicFile, noBackup) + 旧prefs移行。購入→承認→サーバ時刻検証→保存
- オフライン猶予: 24hリース+ブート紐付け、期限確認にネット必須の旨表示
- ゲート箇所: 自由配置・拡縮 / ロゴ非表示 / Crop&Frame / TripRecap高画質・全比率 → Plus or リワード広告
- 広告: AdMob バナー(Main下部100dp/Gallery?/Recap?) + インタースティシャル + リワード。Plus有効中は全除去
- 一時解除: リワード視聴で「ロゴ非表示+広告スキップ 5回/2h」
- UMP同意フロー (Settings→Privacy options)、広告ID権限
- ※鍵・商品IDは開発者自身のものに置換必須 (BuildConfig注入、リポに秘密鍵なし)

## 9. Settings
Plusバナー(状態連動) / 言語(10言語+システム: en/ja/ko/zh-Hans/zh-Hant/fr/de/it/es-ES/pt-PT, AppCompatDelegate per-app locale) / 自動ジオタグ(起動時・有効化時に現在地付与) / HDR保持(動画書出時) / バージョン表示 / About / PrivacyPolicy / Privacy options(UMP再表示)

## 10. 権限
CAMERA(必須) / RECORD_AUDIO(任意: 拒否→無音録画継続) / FINE+COARSE(必須: GPS表示) / READ_MEDIA_IMAGES+VIDEO(+VISUAL_USER_SELECTED部分対応) / ACCESS_MEDIA_LOCATION(写真位置読取) / INTERNET/ACCESS_NETWORK_STATE/WAKE_LOCK/FOREGROUND_SERVICE / BILLING / AD_ID。 rationaleダイアログ(全件許可の要請)あり

## 11. 共有・外部連携
queries: X/Instagram/RatioFit(com.path.ratiofit)/Maps/Billing/CustomTabs/カレンダー/SMS/電話/Play。共有後のSaveSuccessでRatioFit編集CTA(未導入時はストア誘導)

## 12. 非機能
- minSdk 32 / target 37 / portrait固定 / dark黒基調UI / Outfit系見出し+本文フォント切替(7種同梱→自作時は OFL フォントに置換)
- WorkManager(広告?定期?)、Room、DataStore?、Emoji2、ProfileInstaller、Firebase Analytics? ※解析継続
- エッジ: 位置未取得/オフライン座標のみ/写真位置なし/広告未読込/購入保留/時刻未検証/ストレージ不足/権限拒否→各Toast・ダイアログ・再試行あり

## 13. 再実装時の置換表 (原物→自作)
| 原物 | 自作 |
|---|---|
| 地図JSON(6種) | Natural Earth + dataofjapan(地球地図日本, 出典明記) から tools/mapdata で生成 |
| airports.db/airlines | OpenFlights dat (ODbL, 出典明記) |
| railways.db | mlit-json (国土数値情報利用約款, 出典明記) |
| 方向API私設サーバ | OSRM demo + 直線フォールバック (将来差替可能) |
| AdMob ID / Maps APIキー | local.properties注入 (空なら広告・地図デグレード表示) |
| 課金商品ID | 自分のPlay Console商品に置換 (商用利用時のみ) |
| フォント/画像素材 | OFL/自作に置換 |
| 文言(strings) | 意味同等・表現自作 (10言語は機械翻訳+レビュー前提で段階整備、まずen/ja) |
