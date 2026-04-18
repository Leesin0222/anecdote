# Quick Start

anecdote SDK를 5분 안에 통합하기.

---

## 1. 의존성 추가

사내 AAR 배포 시에는 `libs/` 에 파일을 떨구거나 사내 Maven 경로를 참조.

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":anecdote-core"))           // 필수
    implementation(project(":anecdote-adapter-admob"))  // AdMob 쓰는 경우
    implementation(project(":anecdote-reporter-logcat")) // 개발용
    implementation(project(":anecdote-reporter-firebase")) // 프로덕션 분석
}
```

---

## 2. 최소 통합

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
                    is BlockState.Suspected -> Unit // 관찰만
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

이것만으로 내장 Active Probe + 네트워크 환경 감지가 돌아감.

---

## 3. AdMob 어댑터 연결 (선택)

앱이 AdMob을 쓰면 광고 SDK 콜백에서 직접 신호를 수집할 수 있음 → probe보다 훨씬 정확.

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

기존 `AdListener`가 있다면 delegate로 넘기면 됨:
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

이벤트 2개가 자동 전송:
- `adblock_state_changed` — 판정 결과 전이
- `adblock_signal` — 개별 신호 (probe 결과, 로드 실패 등)

Probe signal 볼륨이 부담되면 끌 수 있음:
```kotlin
FirebaseBlockEventReporter(analytics, includeSignalEvents = false)
```

---

## 5. UMP (Consent) 연동 (AdMob + GDPR 대응 앱)

유저가 consent를 거절해서 발생한 `LoadFailed`는 광고 차단이 아님. 필터링 권장:

```kotlin
val consent = UserMessagingPlatform.getConsentInformation(context)

val gmaSource = GmaSignalSource(
    consentChecker = UmpConsentChecker(consent),
)
```

---

## 체크리스트

- [ ] `detector.start()` 호출 — `onCreate` 적절한 시점에
- [ ] `detector.stop()` 호출 — `onDestroy` 등 lifecycle 끝에
- [ ] `state.collect`로 `BlockState` 수신
- [ ] 대응 UX는 호스트 앱 책임 (SDK는 감지만)
- [ ] AdMob 쓰면 `GmaSignalSource` 등록
- [ ] Firebase 쓰면 `FirebaseBlockEventReporter` 추가
- [ ] UMP 쓰면 `UmpConsentChecker` 주입

---

더 상세한 옵션은 [integration-guide.md](integration-guide.md) 참조.
