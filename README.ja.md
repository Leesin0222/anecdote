# anecdote: Android向けオープンソース広告ブロック検出SDK

<p align="center">
  <img src="docs/assets/anecdote-banner-06-split.png" alt="anecdote" width="100%">
</p>

[![License](https://img.shields.io/github/license/Leesin0222/anecdote)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/Leesin0222/anecdote?include_prereleases&sort=semver)](https://github.com/Leesin0222/anecdote/releases)
[![Stars](https://img.shields.io/github/stars/Leesin0222/anecdote?style=flat)](https://github.com/Leesin0222/anecdote/stargazers)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android minSdk](https://img.shields.io/badge/Android-min%20SDK%2024-3DDC84?logo=android&logoColor=white)](https://developer.android.com)

[English](README.md) · [한국어](README.ko.md) · **日本語**

## 概要

`anecdote` は、ユーザーが広告をブロックしているかを検出する Android 向けオープンソース SDK です。特定の広告ネットワークに結合せず、3 つの独立した根拠 — HTTP 到達性プローブ、広告 SDK のコールバック、ネットワーク環境テレメトリ — を重み付きスコアに統合し、判定結果を `StateFlow<BlockState>` として公開します。

このプロジェクトが存在する理由は、既存の選択肢が満足できないからです。`onAdFailedToLoad` 単独からの推測も、独自広告ロジックを抱えるクローズドソース検出器も、誤検出が多すぎて UX に組み込むには使い物になりません。`anecdote` は、リワードゲートや分析ファネルに実際に組み込んでも違和感のない、デバッグ可能でキャリブレーション済みの判定を目指しています。

## コアアーキテクチャ

SDK は 3 つの独立したレイヤで構成されています:

**Core (`anecdote-core`)**: 検出エンジン本体。Active Probe ランナー、ネットワーク環境ウォッチャー、ポリシー/スコアリング設定、そしてホスト側に公開される `StateFlow<BlockState>` を保持します。広告 SDK への依存は一切ありません。

**Adapters (`anecdote-adapter-admob` ほか)**: 特定の広告 SDK の失敗コールバックを追加のシグナルとして検出器に流し込みます。アダプターは完全にオプトイン — Core はアダプターなしでも動作します — ただし接続されたアダプターは、HTTP プローブだけよりはるかに鮮明な判定を可能にします。同梱の AdMob アダプターはさらにメディエーションチェーンを分解し、AppLovin / Unity / ironSource の fill を個別のシグナルとして記録します。

**Reporters (`anecdote-reporter-firebase`, `anecdote-reporter-logcat` ほか)**: 状態遷移とシグナル単位のイベントを分析またはデバッグ用に受け取ります。複数のレポーターを並列に登録でき、それぞれ独立に動作します。

## 設計思想

SDK の根幹を支える 6 つの原則:

1. **広告 SDK 非依存** — Core はコンパイル時点でいかなる広告ネットワークも知らず、ネットワーク固有のものはすべてオプトインのアダプターモジュールにあります。
2. **マルチシグナル統合** — 単一の失敗 (不安定なプローブ、運悪く起きた `onAdFailedToLoad` 1 件) だけでは判定を覆せません。
3. **信頼度付きスコアベース判定** — 公開する判定は `Unknown / NotBlocked / Suspected / Blocked` の 4 値で、各 `Blocked` は `Confidence` を持つため、ホスト UX は `LOW` のときは静観し、`HIGH` のときだけ動作するといった使い分けが可能です。
4. **デフォルトで誤検出に強い** — コールドスタート時のグレースピリオド、ネットワーク遷移によるセッションリセット、Google 広告が利用できない地域 (中国など) の自動除外が組み込まれています。
5. **同意状態を考慮** — ホストが UMP を使用している場合、同意拒否によって発生したロード失敗はスコアに入る前にフィルタされるため、GDPR/CCPA フローが広告ブロックと誤認されることがありません。
6. **検出のみ、対応はホスト側** — SDK は警告や UI 変更、ゲートを一切行いません。`Blocked` にどう反応するか、そもそも反応するか自体、ホストアプリに完全に委ねます — これにより Play Store ポリシーリスクはホスト側に留まります。

## 機能一覧

**標準搭載のシグナルソース**:

- **Active Probe** — 設定可能な広告ドメイン (デフォルト: `pagead2.googlesyndication.com` など) と対照ドメイン (`www.google.com`, `www.gstatic.com`) に対する HEAD リクエスト。スコアに入るのは失敗率の絶対値ではなく **差分** — 地下鉄やエレベーターでの一時的な通信断によって `Blocked` 判定にならない仕組みです。
- **ネットワーク環境** — VPN と Private DNS の検出が重み付きボーナス (`+10` と `+15`) として寄与します。
- **AdMob アダプター** — `AdListener`、`InterstitialAdLoadCallback`、`RewardedAdLoadCallback` をラップし、エラーを `NO_FILL` と実際のネットワーク障害に分類、さらにメディエーションチェーンを分解します。
- **CustomSignalSource** — AdMob 以外のネットワーク用の公開フック。アダプターを新規作成しなくても、ホストアプリ既存の AppLovin / Unity / ironSource コールバックから `emitLoadFailed` / `emitLoadSucceeded` を呼ぶだけで連携できます。

**標準搭載のレポーター**:

- **`LogcatBlockEventReporter`** — 開発用の `Log.d("anecdote", …)` 出力。
- **`FirebaseBlockEventReporter`** — Firebase Analytics に `adblock_state_changed` と (オプションで) `adblock_signal` を送信します。シグナルイベントはデフォルトで控えめに動作するため、本番トラフィックが膨張しません。

**スコアモデル**

- `probe delta × 80` — 支配的な項。
- `avg load failure rate × 60` — 登録された広告 SDK シグナルにわたる平均失敗率。
- `+10` (VPN)、`+15` (Private DNS)。
- 最終スコアは `0..100` にクランプ。デフォルト閾値は `40 (Suspected)` / `70 (Blocked)`。

## 検出モデル

検出器は `start()` がスコープ内にある間、評価ループを走らせます。毎ティックごとに、登録された各 `SignalSource` に最新データを問い合わせ、スコアを再計算し、判定が**実際に変わったときだけ** 新しい `BlockState` を発行します。そのため `onStateChanged` の購読コストは小さく、生のシグナルストリームが必要なホストは `onSignalReceived` を使えます。

誤検出を抑えながら本物の検出は鈍らせないために、2 つのガードが入っています:

- **グレースピリオド (`gracePeriodMs`, デフォルト 5s)** — コールドスタート後、またはネットワーク変化後、十分な根拠が集まるまで検出器は `Unknown` のままです。
- **セッションあたりの再評価上限 (`maxEvaluationsPerSession`, デフォルト 3)** — `Confidence.HIGH` で確定した `Blocked` は、ネットワーク環境が変わるまで再評価を停止します。低信頼度の判定は上限まで再評価を続けます。

ネットワーク遷移 (Wi-Fi ⇄ セルラー、VPN の切替、Private DNS の変更) は自動的にセッションをリセットするため、トンネル内での短時間の通信断がユーザーを `Blocked` に固定し続けることはありません。

## クイックスタート

```kotlin
val detector = AdBlockDetector.Builder(applicationContext)
    .addReporter(LogcatBlockEventReporter())
    .build()

detector.start()

lifecycleScope.launch {
    detector.state.collect { state ->
        when (state) {
            is BlockState.Blocked -> handleBlocked(state.confidence)
            else -> Unit
        }
    }
}
```

AdMob ホストの場合、アダプターを既存の広告ライフサイクルに組み込みます:

```kotlin
val gmaSource = GmaSignalSource(consentChecker = UmpConsentChecker(consent))
detector.registerSignalSource(gmaSource)

adView.adListener = gmaSource.adListener(delegate = myExistingListener)
InterstitialAd.load(ctx, unitId, request, gmaSource.interstitialLoadCallback())
RewardedAd.load(ctx, unitId, request, gmaSource.rewardedLoadCallback())
```

> **前提条件**: アプリ側ですでに `com.google.android.gms:play-services-ads` に
> 依存している必要があります。アダプターは GMA SDK を `compileOnly` として宣言
> しているため、`anecdote-adapter-admob` を追加してもアプリ側で使用中の
> 広告 SDK バージョンを引き込んだり上書きしたりすることはありません — これは、
> 独自の広告スタック・メディエーション構成・lite/full の GMA バリアントを
> 使用するホストとアダプターを安全に共存させるための仕組みです。

### ポリシーチューニング

```kotlin
AdBlockDetector.Builder(context)
    .policy {
        thresholdSuspected = 40            // 0..100, デフォルト 40
        thresholdBlocked = 70              // 0..100, デフォルト 70
        maxEvaluationsPerSession = 3       // セッションごとの状態遷移回数
        disabledRegionMccs = setOf(460)    // デフォルトは CN。必要に応じて追加
    }
    .probe {
        timeoutMs = 3_000
        gracePeriodMs = 5_000
        intervalMs = 60_000
        // デフォルト: pagead2.googlesyndication.com (ad), www.google.com (control)
        // adDomains = listOf("pagead2.googlesyndication.com", ...)
        // controlDomains = listOf("www.google.com", "www.gstatic.com")
    }
    .build()
```

### カスタムシグナルソース (AdMob 以外のネットワーク)

```kotlin
val appLovin = CustomSignalSource(networkId = "applovin")
detector.registerSignalSource(appLovin)

// 既存の AppLovin リスナーの中で:
override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
    appLovin.emitLoadFailed(
        errorType = ErrorType.NO_FILL,
        rawErrorCode = error.code,
        rawErrorMessage = error.message,
    )
}
override fun onAdLoaded(ad: MaxAd) {
    appLovin.emitLoadSucceeded()
}
```

### カスタムレポーター

```kotlin
class MyBackendReporter(private val api: MyApi) : BlockEventReporter {
    override fun onStateChanged(state: BlockState) { /* 状態遷移のみ */ }
    override fun onSignalReceived(signal: AdNetworkSignal) { /* 生シグナルすべて */ }
}

AdBlockDetector.Builder(context)
    .addReporter(MyBackendReporter(api))
    .build()
```

`onStateChanged` は判定が実際に変わったときだけ発火します。`onSignalReceived` はすべての受信シグナルで発火するため、デフォルトのプローブ頻度では出力量がそれなりに多くなります。

## モジュール選択マトリクス

すべてのモジュールはオプトインで、依存していないものは最終 APK に入りません。

| 想定状況                                          | モジュール                                                |
| ------------------------------------------------- | --------------------------------------------------------- |
| 検出のみ、ホストは反応しない                      | `anecdote-core` + `anecdote-reporter-logcat`              |
| 本番分析                                          | `anecdote-core` + `anecdote-reporter-firebase`            |
| AdMob ホスト (メディエーション有無問わず)         | 上記に `anecdote-adapter-admob` を追加                    |
| AdMob 以外のネットワーク                          | `CustomSignalSource` を使用、または専用アダプターを実装   |
| カスタム分析バックエンド                          | `BlockEventReporter` を直接実装                           |

## ビルド

JDK 17+ が必要です。macOS の場合:

```bash
brew install openjdk@17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
```

主なターゲット:

```bash
# テスト + サンプル APK
./gradlew assembleDebug testDebugUnitTest

# 配布用 AAR セット
./gradlew collectAars
# → build/artifacts/anecdote-*-<version>.aar
```

## リポジトリ構成

- **`anecdote-core/`** — 検出器、プローブランナー、ポリシー/スコアリング、ネットワークウォッチャー、公開 `StateFlow<BlockState>` API
- **`anecdote-adapter-admob/`** — Google Mobile Ads バインディング、メディエーションチェーン分解、オプションの UMP ベース consent フィルタリング
- **`anecdote-reporter-firebase/`** — Firebase Analytics レポーター (`adblock_state_changed`, `adblock_signal`)
- **`anecdote-reporter-logcat/`** — デバッグ用レポーター
- **`sample/`** — 動作するサンプルアプリ。エンドツーエンドの手動検証用に `CustomSignalSource` を駆動する「Fail」「Success」ボタンを含みます
- **`build-logic/`** — Gradle convention plugin (publishing convention を含む)

## スコープ (意図的な制限)

- **検出のみ。** SDK はダイアログを表示したり機能をゲートしたり、ユーザーのブロッカーを無効化しようとしたりはしません。そうした反応はすべてホストアプリの判断であり、Play Store ポリシーリスクもホスト側に残ります。
- **Android のみ。** iOS はスコープ外です。あちらの広告エコシステムは別物すぎて、移植ではなく別プロジェクトになります。
- **まず内部検証から。** SDK は外部告知を考える前に、まず実際のリワードアプリのトラフィックで検証されています。

## ライセンス

Apache-2.0。

## コントリビュート

Issue と Pull Request を歓迎します。公開インターフェース (`SignalSource`, `BlockEventReporter`) は意図的に小さく保たれています — 新しいアダプターやレポーターは、Core の表面積を広げるのではなく、これらを通る形で実装してください。
