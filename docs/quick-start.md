# Quick Start

anecdote SDK를 5분 안에 통합하실 수 있도록 필요한 단계만 모아두었습니다.

---

## 1. 의존성 추가

사내 AAR로 배포받으셨다면 `libs/`에 파일을 넣어두시거나, 사내 Maven 경로를 그대로 참조하시면 됩니다.

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":anecdote-core"))           // 필수
    implementation(project(":anecdote-adapter-admob"))  // AdMob을 사용하시는 경우
    implementation(project(":anecdote-reporter-logcat")) // 개발용
    implementation(project(":anecdote-reporter-firebase")) // 프로덕션 분석용
}
```

---

## 2. 최소 통합

아래 코드만으로도 기본 감지 기능이 정상적으로 동작합니다.

```kotlin
class MainActivity : ComponentActivity() {

    private lateinit var detector: AdBlockDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        detector = AdBlockDetector.Builder(applicationContext)
            .addReporter(LogcatBlockEventReporter())
            .build()

        detector.start()

        lifecycleScope.launch {
            detector.state.collect { state ->
                when (state) {
                    is BlockState.Blocked -> showBlockedMessage(state.confidence)
                    is BlockState.Suspected -> Unit // 우선 관찰만 합니다
                    BlockState.NotBlocked, BlockState.Unknown -> Unit
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.stop()
    }
}
```

이 코드만 넣어두셔도 내장 Active Probe와 네트워크 환경 감지가 함께 돌아갑니다.

---

## 3. AdMob 어댑터 연결 (선택)

AdMob을 사용하는 앱이라면 광고 SDK 콜백에서 신호를 직접 받을 수 있어, Probe보다 훨씬 정확하게 판단할 수 있습니다.

```kotlin
val gmaSource = GmaSignalSource()
detector.registerSignalSource(gmaSource)

// Banner
adView.adListener = gmaSource.adListener()

// Interstitial
InterstitialAd.load(ctx, unitId, request, gmaSource.interstitialLoadCallback())

// Rewarded
RewardedAd.load(ctx, unitId, request, gmaSource.rewardedLoadCallback())
```

이미 쓰고 계신 `AdListener`가 있다면 delegate로 넘겨주시면 기존 동작도 그대로 유지됩니다.

```kotlin
adView.adListener = gmaSource.adListener(delegate = myExistingListener)
```

---

## 4. Firebase Analytics 리포터 (프로덕션 권장)

```kotlin
val analytics = Firebase.analytics

detector = AdBlockDetector.Builder(applicationContext)
    .addReporter(FirebaseBlockEventReporter(analytics))
    .build()
```

다음 두 이벤트가 자동으로 전송됩니다.

- `adblock_state_changed` — 판정 결과가 바뀐 시점
- `adblock_signal` — 개별 신호 (probe 결과, 로드 실패 등)

Probe signal이 많다고 느껴지신다면 아래처럼 꺼두셔도 괜찮습니다.

```kotlin
FirebaseBlockEventReporter(analytics, includeSignalEvents = false)
```

---

## 5. UMP (Consent) 연동 (AdMob + GDPR 대응 앱)

유저가 consent를 거절해서 발생한 `LoadFailed`는 실제 광고 차단이 아닙니다. 아래처럼 필터링해두시길 권장합니다.

```kotlin
val consent = UserMessagingPlatform.getConsentInformation(context)

val gmaSource = GmaSignalSource(
    consentChecker = UmpConsentChecker(consent),
)
```

---

## 체크리스트

- [ ] `onCreate` 등 적절한 시점에 `detector.start()` 호출
- [ ] `onDestroy` 등 lifecycle 종료 시점에 `detector.stop()` 호출
- [ ] `state.collect`로 `BlockState` 수신 코드 연결
- [ ] 대응 UX는 호스트 앱에서 직접 구현 (SDK는 감지까지만 담당합니다)
- [ ] AdMob을 쓴다면 `GmaSignalSource` 등록
- [ ] Firebase를 쓴다면 `FirebaseBlockEventReporter` 추가
- [ ] UMP를 쓴다면 `UmpConsentChecker` 주입

---

더 자세한 옵션은 [integration-guide.md](integration-guide.md)를 참고해 주시면 됩니다.
