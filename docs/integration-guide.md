# Integration Guide

anecdote SDK 통합에 필요한 세부 옵션을 정리한 가이드입니다. 필요한 부분만 골라서 참고해 주시면 됩니다.

---

## 모듈 선택 매트릭스

호스트 앱 상황에 맞춰 필요한 모듈만 골라서 담으시면 됩니다.

| 상황 | 필요 모듈 |
|------|-----------|
| 감지만 필요하고 별도 대응이 없을 때 | `anecdote-core` + `reporter-logcat` (개발용) |
| 프로덕션 분석이 필요할 때 | 위 + `reporter-firebase` |
| AdMob을 쓸 때 | 위 + `adapter-admob` |
| AdMob 미디에이션을 쓸 때 | `adapter-admob` (chain을 자동으로 분해합니다) |
| 다른 광고 SDK를 쓸 때 | `CustomSignalSource` 또는 자체 어댑터 작성 ([adapter-guide.md](adapter-guide.md)) |
| 자체 분석 백엔드를 쓸 때 | `BlockEventReporter`를 직접 구현합니다 |

**모든 모듈은 opt-in 구조입니다.** 실제로 사용하지 않으시는 모듈은 APK에 포함되지 않으니 안심하셔도 됩니다.

---

## 정책 튜닝

### Threshold

```kotlin
AdBlockDetector.Builder(context)
    .policy {
        thresholdSuspected = 40   // 0..100, 기본 40
        thresholdBlocked = 70     // 0..100, 기본 70
        maxEvaluationsPerSession = 3  // 세션당 상태 전이 최대 횟수
    }
    .build()
```

### 점수 계산 개요

내부적으로 아래 가중치를 더해 0~100 범위로 정리합니다.

- `probe delta × 80` — ad 도메인 실패율에서 control 도메인 실패율을 뺀 값입니다 (가장 핵심적인 신호입니다)
- `avg load failure rate × 60` — 광고 SDK의 평균 실패율입니다
- VPN이 감지되면 `+10`점이 더해집니다
- Private DNS가 감지되면 `+15`점이 더해집니다

**튜닝 팁**

- 오탐이 많다고 느껴지신다면 `thresholdBlocked`를 80~85로 올려보시길 권장합니다.
- 반대로 검출 감도가 부족하다면 60~65로 낮춰주시면 됩니다.
- 앱테크처럼 광고 로드 빈도가 높은 앱이라면 데이터가 빠르게 쌓이므로 `maxEvaluationsPerSession`을 5~10 정도로 넉넉하게 잡아주셔도 됩니다.

### Probe 설정

```kotlin
.probe {
    timeoutMs = 3_000        // HTTP 요청 timeout
    gracePeriodMs = 5_000    // 앱 시작 후 이 시간 동안은 Unknown 유지
    intervalMs = 60_000      // probe cycle 주기
    adDomains = listOf(...)  // 기본: pagead2.googlesyndication.com 등
    controlDomains = listOf(...) // 기본: www.google.com, www.gstatic.com
}
```

**앱테크 앱에 권장드리는 값**

- `gracePeriodMs = 3_000` — 보상 지급 경로에서 빠르게 차단을 감지해야 하므로 조금 짧게 잡으시는 편이 좋습니다.
- `intervalMs = 15_000` — 세션이 짧은 편이라 자주 체크하도록 설정하시는 편이 안전합니다.

### 지역 제외

```kotlin
.policy {
    disabledRegionMccs = setOf(460, 255) // CN, UA 등
}
```

기본값은 `{460}` (중국 — 구글 광고 자체가 차단된 지역)으로 설정되어 있습니다.

---

## 커스텀 신호 주입

내장 AdMob 어댑터로 잡히지 않는 광고 네트워크가 있다면 `CustomSignalSource`를 활용해 주시면 됩니다.

```kotlin
val appLovinSource = CustomSignalSource(networkId = "applovin")
detector.registerSignalSource(appLovinSource)

// AppLovin 콜백에서:
override fun onAdLoadFailed(...) {
    appLovinSource.emitLoadFailed(
        errorType = ErrorType.NO_FILL,  // 상황에 맞게 매핑해 주세요
        rawErrorCode = code,
        rawErrorMessage = msg,
    )
}

override fun onAdLoaded(...) {
    appLovinSource.emitLoadSucceeded()
}
```

동일한 네트워크를 여러 앱에서 재사용하실 계획이라면 독립 어댑터 모듈로 빼두시는 편이 유지보수에 좋습니다. 자세한 방법은 [adapter-guide.md](adapter-guide.md)를 참고해 주시면 됩니다.

---

## 커스텀 리포터

자체 분석 백엔드로 데이터를 보내고 싶으시다면 `BlockEventReporter`를 직접 구현하시면 됩니다.

```kotlin
class MyBackendReporter(private val api: MyApi) : BlockEventReporter {
    override fun onStateChanged(state: BlockState) {
        api.postEvent("state_changed", mapOf(
            "state" to state.toString(),
            // ...
        ))
    }

    override fun onSignalReceived(signal: AdNetworkSignal) {
        // ...
    }
}

detector = AdBlockDetector.Builder(context)
    .addReporter(MyBackendReporter(api))
    .build()
```

`onStateChanged`는 상태가 실제로 바뀔 때만 호출됩니다. 반면 `onSignalReceived`는 들어오는 모든 signal마다 호출되기 때문에 (특히 probe 주기에 따라 꽤 많을 수 있습니다) 서버 부담도 함께 고려해 주시면 좋습니다.

---

## 리포터 조합 전략

```kotlin
// 개발 빌드
if (BuildConfig.DEBUG) {
    builder.addReporter(LogcatBlockEventReporter())
}

// 프로덕션 - 상태 전이만 Firebase로 전송합니다
builder.addReporter(FirebaseBlockEventReporter(
    analytics,
    includeSignalEvents = false,  // probe signal은 보내지 않아 이벤트 양을 줄여줍니다
))

// 필요하다면 자체 백엔드로도 같이 전송할 수 있습니다
builder.addReporter(MyBackendReporter(api))
```

여러 리포터를 등록하시면 순차적으로 모두 호출되며, 각 리포터는 서로 영향을 주지 않고 독립적으로 동작합니다.

---

## 대응 UX 가이드

SDK는 "감지"까지만 담당하고, 실제 대응 UX는 호스트 앱에서 자유롭게 구현하시는 구조입니다. 참고하실 만한 패턴을 몇 가지 소개해 드리겠습니다.

### Soft warning (앱테크 앱에 권장합니다)

```kotlin
when (state) {
    is BlockState.Blocked -> {
        if (state.confidence == Confidence.HIGH) {
            showToast("광고 차단이 감지되었습니다. 보상 지급이 정상 동작하지 않을 수 있습니다.")
        }
    }
    else -> Unit
}
```

### Feature gate

```kotlin
fun canClaimReward(state: BlockState): Boolean = when (state) {
    is BlockState.Blocked -> state.confidence != Confidence.HIGH
    else -> true
}
```

### Hard gate (정책 리스크에 유의해 주세요)

광고 차단 해제를 강제하는 방식은 Play Store 정책상 리스크가 있을 수 있습니다. 적용 전에 반드시 법무/정책 검토를 거쳐주시길 권장합니다.

---

## FAQ

### Q. 내장 probe가 실제 광고 도메인에 HTTP 요청을 보내나요?

A. 네. HEAD 요청으로 연결성만 확인하고 응답 body는 받지 않기 때문에 대역폭 영향은 거의 없습니다. 기본 interval은 60초입니다.

### Q. Probe 비용이 걱정됩니다.

A. 기본값 기준 5 도메인 × HEAD × 60초 주기로, 세션당 수 KB 수준입니다. 부담스러우시면 `intervalMs`를 더 길게 잡거나, 특정 상황에서만 `detector.start()`를 호출하도록 조정하면 됩니다.

### Q. False positive가 발생하는 것 같습니다. 어떻게 디버깅하면 좋을까요?

A. 개발 중에는 `LogcatBlockEventReporter()`를 붙여두고 `adb logcat -s anecdote`로 들어오는 모든 signal을 관찰하시는 걸 권장합니다. 어떤 signal이 점수를 올렸는지는 `BlockState.Suspected.signals` / `Blocked.signals`에서 바로 확인할 수 있습니다.

### Q. Confidence.LOW는 무시해도 될까요?

A. 네, 그렇게 처리하시는 편을 권장합니다. HIGH나 MEDIUM 이상부터 대응 UX 트리거로 활용하고, LOW는 아직 데이터가 부족해 확신하지 못하는 단계로 보시면 됩니다.

### Q. 지하철이나 엘리베이터에서 네트워크가 끊기면 Blocked로 판정되나요?

A. 아니요. Probe에서 ad 도메인과 control 도메인이 둘 다 실패하면 delta가 0이 되어 점수가 오르지 않습니다. 또 `gracePeriodMs` 기본 5초가 cold start 직후의 일시적인 상황을 막아주며, 네트워크 환경이 바뀌면 세션도 자동으로 reset 됩니다.

### Q. VPN을 쓰는 유저는 무조건 Blocked로 잡히나요?

A. 그렇지 않습니다. VPN은 `+10`점 보너스 역할만 하기 때문에 다른 신호(probe, load failure 등)와 함께 있어야 threshold를 넘게 됩니다.

### Q. 중국처럼 구글 광고가 원천 차단된 지역에서도 Blocked로 잡히나요?

A. 아니요. `PolicyConfig.disabledRegionMccs` 기본값에 `{460}` (중국)이 포함되어 있어, SIM MCC가 중국이면 판정 자체가 Unknown으로 반환됩니다. 다른 지역도 필요하면 추가해서 사용하시면 됩니다.

### Q. 한 번 Blocked 판정이 난 이후에도 계속 재평가되나요?

A. `Confidence.HIGH`로 확정된 Blocked는 네트워크 환경이 바뀌기 전까지 재평가를 멈춥니다. `Confidence.LOW/MEDIUM`이면 `maxEvaluationsPerSession` 한도까지 다시 평가합니다.

### Q. 유저가 VPN을 켜거나 Private DNS를 바꾸면 어떻게 되나요?

A. 세션이 자동으로 reset 됩니다. 새 grace period가 적용되고 처음부터 다시 판정하게 되므로, 네트워크 전환에 자연스럽게 대응할 수 있습니다.

### Q. Firebase에 이벤트가 너무 많이 올라옵니다.

A. `FirebaseBlockEventReporter(analytics, includeSignalEvents = false)`로 signal-level 이벤트를 끄고 state 전이만 전송하는 방식을 추천합니다. 아니면 `intervalMs`를 120_000 이상으로 올려 probe 빈도를 낮추는 방법도 있습니다.

### Q. 테스트 환경에서 SDK가 의도대로 동작하는지 확인하고 싶습니다.

A. `CustomSignalSource`로 원하는 signal을 직접 주입해 state 전이를 유도해 볼 수 있습니다. 샘플 앱의 "Fail" / "Success" 버튼이 바로 이 패턴을 사용하고 있습니다.

### Q. 앱 백그라운드에서도 감지가 동작하나요?

A. 현재는 Activity lifecycle에 `detector.start/stop`을 묶어서 사용하는 설계입니다. 백그라운드 감지가 꼭 필요하다면 `Application.onCreate`에서 start 해두고 프로세스 종료 전까지 stop을 호출하지 않으면 됩니다. 다만 전력이나 데이터 사용량이 늘어날 수 있다는 점은 미리 고려해 주세요.

---

## 관련 문서

- [quick-start.md](quick-start.md) — 5분 통합 가이드
- [adapter-guide.md](adapter-guide.md) — 새 광고 SDK용 어댑터 작성법
- [phase1-architecture.md](phase1-architecture.md) — 설계 배경과 의사결정 기록
