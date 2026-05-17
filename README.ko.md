# anecdote: Android용 오픈소스 광고 차단 감지 SDK

<p align="center">
  <img src="docs/assets/anecdote-banner-06-split.png" alt="anecdote" width="100%">
</p>

[![License](https://img.shields.io/github/license/Leesin0222/anecdote)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/Leesin0222/anecdote?include_prereleases&sort=semver)](https://github.com/Leesin0222/anecdote/releases)
[![Stars](https://img.shields.io/github/stars/Leesin0222/anecdote?style=flat)](https://github.com/Leesin0222/anecdote/stargazers)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Android minSdk](https://img.shields.io/badge/Android-min%20SDK%2024-3DDC84?logo=android&logoColor=white)](https://developer.android.com)

[English](README.md) · **한국어** · [日本語](README.ja.md)

## 개요

`anecdote`는 특정 광고 네트워크에 종속되지 않으면서 사용자의 광고 차단 여부를 판정하는 Android 오픈소스 SDK입니다. 하나의 신호에만 의지하지 않고 서로 독립적인 세 가지 근거(HTTP 도달성 Probe, 광고 SDK 콜백, 네트워크 환경 텔레메트리)를 가중치 기반으로 합산해 점수를 만들고, 그 결과를 `StateFlow<BlockState>`로 노출합니다.

이 프로젝트를 만든 이유는, `onAdFailedToLoad` 하나로 판단하거나 자체 광고 로직을 끼워 파는 폐쇄형 검출기에 비용을 지불하는 두 대안이 모두 보상 흐름이나 분석 퍼널에 직접 물리기엔 오탐이 너무 많기 때문입니다. `anecdote`는 보정 가능하고 디버깅 가능한 판정을 목표로, 앱 팀이 실제 사용 흐름에 자신 있게 연결할 수 있도록 설계되었습니다.

## 코어 아키텍처

SDK는 세 가지 독립 레이어로 구성됩니다.

**Core (`anecdote-core`)**: 감지 엔진. Active Probe 러너, 네트워크 환경 와처, 정책/스코어링 설정, 그리고 호스트 코드로 노출되는 `StateFlow<BlockState>`를 모두 소유합니다. 어떤 광고 SDK에도 컴파일 타임 의존성이 없습니다.

**Adapter (`anecdote-adapter-admob`, …)**: 특정 광고 SDK의 실패 콜백을 detector에 신호로 묶어주는 모듈입니다. 어댑터는 완전히 옵셔널이며 — Core는 어댑터 없이도 동작합니다 — 어댑터가 연결되면 HTTP probe만 쓸 때보다 훨씬 선명한 판단이 가능합니다. 기본 제공되는 AdMob 어댑터는 미디에이션 체인을 분해해 AppLovin / Unity / ironSource fill을 개별 신호로 기록합니다.

**Reporter (`anecdote-reporter-firebase`, `anecdote-reporter-logcat`, …)**: 상태 전이 이벤트와 신호 단위 이벤트를 분석/디버깅 용도로 수신합니다. 여러 리포터를 동시에 등록할 수 있고, 각 리포터는 서로 영향을 주지 않고 독립적으로 동작합니다.

## 설계 원칙

SDK를 지탱하는 여섯 가지 원칙입니다.

1. **광고 SDK 독립성** — Core는 어떤 광고 네트워크에 대한 컴파일 타임 지식도 갖지 않습니다. 네트워크 종속 코드는 모두 opt-in 어댑터 모듈에만 위치합니다.
2. **다중 신호 결합** — 단일 실패 모드(불안정한 probe 한 번, 운 나쁜 `onAdFailedToLoad` 한 건)만으로는 판정이 뒤집히지 않습니다.
3. **점수 기반 판정 + Confidence** — 공개 인터페이스는 `Unknown / NotBlocked / Suspected / Blocked`이며 `Blocked`는 `Confidence`를 함께 전달합니다. 호스트는 `LOW`엔 침묵하고 `HIGH`부터 대응하는 식으로 설계할 수 있습니다.
4. **기본값으로 오탐 방어** — 콜드 스타트 grace period, 네트워크 전환 시 세션 리셋, 구글 광고 자체가 불가한 지역(중국 등)의 자동 제외가 기본 동작입니다.
5. **Consent-aware** — 호스트가 UMP를 쓰는 경우, consent 거절로 발생한 로드 실패는 점수에 들어가기 전에 필터링됩니다. GDPR/CCPA 흐름이 광고 차단으로 오인되지 않습니다.
6. **감지만 담당, 대응은 호스트의 책임** — SDK는 다이얼로그를 띄우거나 기능을 막거나 UI를 수정하지 않습니다. `Blocked`에 어떻게(또는 그대로 둘지) 대응할지는 전적으로 호스트 앱이 결정하며, 그래서 Play Store 정책 리스크도 호스트 쪽에 남습니다.

## 기능 목록

기본 제공 **신호 소스**:

- **Active Probe** — 설정 가능한 광고 도메인(기본: `pagead2.googlesyndication.com` 등)과 컨트롤 도메인(`www.google.com`, `www.gstatic.com`)에 대한 HEAD 요청. 절대 실패율이 아니라 **두 그룹의 실패율 차이(delta)** 가 점수에 반영되므로, 지하철/엘리베이터 같은 일반적인 네트워크 단절은 Blocked로 이어지지 않습니다.
- **네트워크 환경** — VPN과 Private DNS 감지가 각각 `+10`, `+15`의 가중치 보너스로 반영됩니다.
- **AdMob 어댑터** — `AdListener`, `InterstitialAdLoadCallback`, `RewardedAdLoadCallback`을 감싸 에러를 분류(`NO_FILL` vs 실제 네트워크 실패)하고 미디에이션 체인을 분해합니다.
- **CustomSignalSource** — AdMob 이외 네트워크용 공개 훅. 호스트가 이미 쓰는 AppLovin / Unity / ironSource 콜백이 어댑터를 새로 작성하지 않고도 `emitLoadFailed` / `emitLoadSucceeded`만 호출하면 됩니다.

기본 제공 **리포터**:

- **`LogcatBlockEventReporter`** — 개발용 `Log.d("anecdote", …)`.
- **`FirebaseBlockEventReporter`** — Firebase Analytics로 `adblock_state_changed`와 (선택적으로) `adblock_signal`을 전송합니다. signal 이벤트는 끄기 쉬워서 프로덕션 트래픽이 폭주하지 않습니다.

**스코어 모델**

- `probe delta × 80` — 가장 핵심적인 항입니다.
- `avg load failure rate × 60` — 등록된 광고 SDK 신호들의 평균 실패율.
- VPN 감지 시 `+10`, Private DNS 감지 시 `+15`.
- 최종 점수는 `0..100`로 클램프되며, 기본 임계값은 `40 (Suspected)` / `70 (Blocked)`입니다.

## 판정 모델

`start()`가 활성화된 동안 detector는 평가 루프를 돕니다. 매 tick마다 등록된 모든 `SignalSource`에 최신 데이터를 요청하고, 점수를 재계산하고, **판정이 실제로 바뀌었을 때에만** 새 `BlockState`를 emit 합니다. 그래서 `onStateChanged`는 가볍게 물려두기 좋고, 반면 `onSignalReceived`는 원본 신호 흐름을 그대로 받는 호스트용입니다.

다음 두 가지 가드가 실제 검출 감도는 떨어뜨리지 않으면서 오탐만 줄여줍니다.

- **Grace period (`gracePeriodMs`, 기본 5s)** — 콜드 스타트 직후나 네트워크 전환 직후에는 충분한 근거가 모이기 전까지 `Unknown` 상태를 유지합니다.
- **세션당 재평가 한도 (`maxEvaluationsPerSession`, 기본 3)** — `Confidence.HIGH`로 확정된 Blocked는 네트워크 환경이 바뀔 때까지 재평가를 멈춥니다. 낮은 confidence는 한도까지 재평가를 계속합니다.

네트워크 전환(Wi-Fi ↔ cellular, VPN 토글, Private DNS 변경)이 일어나면 세션이 자동으로 reset 됩니다. 짧은 터널 통과가 사용자를 영구적으로 `Blocked`로 뒤집지 못합니다.

## 빠른 시작

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

AdMob 호스트라면 기존 광고 라이프사이클에 어댑터를 끼워주시면 됩니다.

```kotlin
val gmaSource = GmaSignalSource(consentChecker = UmpConsentChecker(consent))
detector.registerSignalSource(gmaSource)

adView.adListener = gmaSource.adListener(delegate = myExistingListener)
InterstitialAd.load(ctx, unitId, request, gmaSource.interstitialLoadCallback())
RewardedAd.load(ctx, unitId, request, gmaSource.rewardedLoadCallback())
```

> **필수 조건**: 호스트 앱이 이미 `com.google.android.gms:play-services-ads`에
> 의존하고 있어야 합니다. 어댑터는 GMA SDK를 `compileOnly`로 선언하기 때문에,
> `anecdote-adapter-admob`을 추가해도 호스트 앱이 사용 중인 광고 SDK 버전을
> 끌어오거나 덮어쓰지 않습니다 — 호스트가 자체 광고 스택·미디에이션 구성·
> lite/full GMA 변형 중 무엇을 쓰든 어댑터가 안전하게 붙도록 하는 장치입니다.

### 정책 튜닝

```kotlin
AdBlockDetector.Builder(context)
    .policy {
        thresholdSuspected = 40            // 0..100, 기본 40
        thresholdBlocked = 70              // 0..100, 기본 70
        maxEvaluationsPerSession = 3       // 세션당 상태 전이 횟수
        disabledRegionMccs = setOf(460)    // 기본 CN. 필요시 추가
    }
    .probe {
        timeoutMs = 3_000
        gracePeriodMs = 5_000
        // intervalMs = 60_000             // 고정 간격이 필요할 때
        // intervalSequenceMs = listOf(...) // 적응형 백오프. 기본 5s→5분 램프
        // 기본 프로브 대상은 글로벌 모바일 광고망 (AdMob/DoubleClick,
        // Meta, Unity, AppLovin, ironSource, Pangle).
        // adDomains = ProbeConfig.DEFAULT_AD_DOMAINS
        // controlDomains = ProbeConfig.DEFAULT_CONTROL_DOMAINS
    }
    .build()
```

#### 지역별 광고망 팩

`DEFAULT_AD_DOMAINS`는 글로벌 모바일 광고망만 다루며, 지역 특화 광고망은 무관한 프로빙을 피하기 위해 일부러 제외했습니다. 특정 시장을 타깃팅하는 앱은 팩을 추가로 옵트인할 수 있습니다.

```kotlin
.probe {
    adDomains = ProbeConfig.DEFAULT_AD_DOMAINS + ProbeConfig.AdNetworkPacks.KOREA
}
```

현재 제공: `KOREA` (Buzzvil, TNK Factory, Adpopcorn, Kakao AdFit, Naver GFA — 앱테크 리워드 앱에서 흔히 사용). 다른 지역 팩 PR을 환영합니다.

#### 광고 차단 레지스트리 확장

빌트인 광고 차단 DNS 제공자/설치 패키지 목록은 SDK를 포크하지 않고도 호출부에서 확장 가능합니다.

```kotlin
AdBlockDetector.Builder(context)
    .addAdBlockerDnsSuffix(".my-private-dns.example")       // suffix 매칭
    .addAdBlockerDnsSuffix("dns.my-specific-blocker.com")   // 정확 매칭
    .addInstalledAdBlockerPackage("com.example.myblocker")  // AndroidManifest <queries>에도 선언 필요
    .build()
```

#### 가중치 튜닝

각 시그널은 [`SignalWeights`](anecdote-core/src/main/java/com/yongjincompany/anecdote/config/SignalWeights.kt)를 통해 0..100 점수에 기여합니다. 기본값은 보수적으로 잡혀 있고, detector별로 오버라이드 가능합니다.

```kotlin
.policy {
    weights = SignalWeights.DEFAULT.copy(
        knownAdBlockerDnsBonus = 80.0,   // Private DNS 매칭 가중치 ↑
        installedAdBlockerBonus = 30.0,  // 설치 ≠ 활성, 가중치 ↓
    )
}
```

### 커스텀 시그널 소스 (비-AdMob 네트워크)

```kotlin
val appLovin = CustomSignalSource(networkId = "applovin")
detector.registerSignalSource(appLovin)

// 기존 AppLovin 리스너 안에서:
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

### 커스텀 리포터

```kotlin
class MyBackendReporter(private val api: MyApi) : BlockEventReporter {
    override fun onStateChanged(state: BlockState) { /* 상태 전이 시점에만 호출됩니다 */ }
    override fun onSignalReceived(signal: AdNetworkSignal) { /* 모든 raw signal */ }
}

AdBlockDetector.Builder(context)
    .addReporter(MyBackendReporter(api))
    .build()
```

`onStateChanged`는 판정이 실제로 바뀔 때에만 호출됩니다. `onSignalReceived`는 모든 신호에 대해 호출되며 기본 probe 주기에서는 꽤 빈번할 수 있습니다.

## 모듈 선택 매트릭스

모든 모듈은 opt-in입니다. 의존성에 추가하지 않은 모듈은 최종 APK에 포함되지 않습니다.

| 상황                                       | 모듈                                                        |
| ------------------------------------------ | ----------------------------------------------------------- |
| 감지만 필요, 별도 대응 없음                | `anecdote-core` + `anecdote-reporter-logcat`                |
| 프로덕션 분석 필요                         | `anecdote-core` + `anecdote-reporter-firebase`              |
| AdMob 호스트 (미디에이션 포함/미포함 모두) | 위에 `anecdote-adapter-admob` 추가                          |
| AdMob 이외 네트워크                        | `CustomSignalSource` 사용, 또는 전용 어댑터 작성            |
| 자체 분석 백엔드                           | `BlockEventReporter`를 직접 구현                            |

## 빌드

JDK 17 이상이 필요합니다. macOS:

```bash
brew install openjdk@17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
```

자주 쓰는 타깃:

```bash
# 테스트 + 샘플 APK
./gradlew assembleDebug testDebugUnitTest

# 배포용 AAR 수집
./gradlew collectAars
# → build/artifacts/anecdote-*-<version>.aar
```

## 리포지토리 구조

- **`anecdote-core/`** — detector, probe runner, 정책/스코어링, 네트워크 와처, 공개 `StateFlow<BlockState>` 인터페이스
- **`anecdote-adapter-admob/`** — Google Mobile Ads 바인딩, 미디에이션 체인 분해, 선택적 UMP 기반 consent 필터링
- **`anecdote-reporter-firebase/`** — Firebase Analytics 리포터 (`adblock_state_changed`, `adblock_signal`)
- **`anecdote-reporter-logcat/`** — 디버그용 리포터
- **`sample/`** — 실행 가능한 샘플 앱. `CustomSignalSource`를 구동하는 "Fail" / "Success" 버튼이 포함되어 end-to-end 수동 테스트가 가능합니다.
- **`build-logic/`** — Gradle convention 플러그인 (퍼블리싱 convention 포함)

## 스코프 (의도적인 한계)

- **감지만 합니다.** SDK는 다이얼로그를 띄우거나 기능을 막거나 사용자의 차단기를 끄려고 시도하지 않습니다. 모든 대응은 호스트 앱의 판단이며, Play Store 정책 리스크도 호스트가 보유합니다.
- **Android 전용입니다.** iOS는 스코프 밖입니다. 광고 생태계 자체가 달라서 단순 포팅이 아니라 별도 프로젝트가 됩니다.
- **사내 검증을 우선합니다.** 외부 홍보 결정은 실제 보상형 앱 트래픽 검증 이후 별도로 합니다.

## 라이선스

Apache-2.0.

## 기여

이슈와 PR을 환영합니다. 공개 인터페이스(`SignalSource`, `BlockEventReporter`)는 의도적으로 작게 유지하고 있습니다. 새 어댑터/리포터는 Core의 표면을 넓히기보다 이 두 인터페이스를 통과시켜 주시면 좋습니다.
