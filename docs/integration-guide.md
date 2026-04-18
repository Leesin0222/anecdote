# Integration Guide

anecdote SDK 통합 상세 가이드.

---

## 모듈 선택 매트릭스

호스트 앱의 조건에 따라 필요한 모듈만 선택:

| 상황 | 필요 모듈 |
|------|-----------|
| 감지만 되면 OK, 대응 없음 | `anecdote-core` + `reporter-logcat` (개발용) |
| 프로덕션 분석 필요 | 위 + `reporter-firebase` |
| AdMob 사용 | 위 + `adapter-admob` |
| AdMob 미디에이션 사용 | `adapter-admob` (자동으로 chain 분해) |
| 다른 광고 SDK | `CustomSignalSource` 또는 자체 어댑터 작성 ([adapter-guide.md](adapter-guide.md)) |
| 자체 분석 백엔드 | `BlockEventReporter` 커스텀 구현 |

**모든 모듈은 opt-in.** 쓰지 않으면 APK에 포함되지 않음.

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

내부적으로 아래 가중치로 점수 합산 (0..100으로 clip):
- `probe delta × 80` — ad 도메인 실패율 - control 도메인 실패율 (핵심 신호)
- `avg load failure rate × 60` — 광고 SDK 실패율 평균
- VPN 감지 시 `+10`
- Private DNS 감지 시 `+15`

**tuning 가이드:**
- 오탐이 많으면 `thresholdBlocked` 를 80~85로 올림
- 검출 민감도가 낮으면 60~65로 내림
- 앱테크처럼 광고 로드 빈도가 높은 앱 → 데이터가 빨리 모여서 `maxEvaluationsPerSession` 를 5~10으로 늘려도 됨

### Probe 설정

```kotlin
.probe {
    timeoutMs = 3_000        // HTTP 요청 timeout
    gracePeriodMs = 5_000    // 앱 시작 후 이 시간 동안은 Unknown 유지
    intervalMs = 60_000      // probe cycle 주기
    adDomains = listOf(...)  // 기본: pagead2.googlesyndication.com 외
    controlDomains = listOf(...) // 기본: www.google.com, www.gstatic.com
}
```

**앱테크 앱 권장값**:
- `gracePeriodMs = 3_000` (짧게) — 보상 지급 경로가 빠르게 차단 감지해야 함
- `intervalMs = 15_000` (빈번하게) — 세션이 짧아서 자주 체크 필요

### 지역 제외

```kotlin
.policy {
    disabledRegionMccs = setOf(460, 255) // CN, UA 등
}
```

기본값: `{460}` (중국 — 구글 광고 자체 차단 지역).

---

## 커스텀 신호 주입

내장 AdMob 어댑터로 잡히지 않는 광고 네트워크라면 `CustomSignalSource`:

```kotlin
val appLovinSource = CustomSignalSource(networkId = "applovin")
detector.registerSignalSource(appLovinSource)

// AppLovin 콜백에서:
override fun onAdLoadFailed(...) {
    appLovinSource.emitLoadFailed(
        errorType = ErrorType.NO_FILL,  // 적절히 매핑
        rawErrorCode = code,
        rawErrorMessage = msg,
    )
}

override fun onAdLoaded(...) {
    appLovinSource.emitLoadSucceeded()
}
```

동일 네트워크를 여러 앱에서 쓴다면 독립 어댑터 모듈로 추출 — [adapter-guide.md](adapter-guide.md) 참조.

---

## 커스텀 리포터

자체 분석 백엔드로 데이터 보내려면 `BlockEventReporter` 구현:

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

`onStateChanged`는 상태 전이 시에만 호출. `onSignalReceived`는 모든 signal마다 호출 (probe 주기에 따라 많을 수 있음 — 서버 부담 고려).

---

## 리포터 조합 전략

```kotlin
// 개발 빌드
if (BuildConfig.DEBUG) {
    builder.addReporter(LogcatBlockEventReporter())
}

// 프로덕션 - 상태 전이만 Firebase
builder.addReporter(FirebaseBlockEventReporter(
    analytics,
    includeSignalEvents = false,  // probe signal 안 보냄 → 이벤트 양 절감
))

// 필요 시 자체 백엔드로도 동시 전송
builder.addReporter(MyBackendReporter(api))
```

여러 리포터 등록 시 모두 순차 호출됨. 각 리포터는 독립적.

---

## 대응 UX 가이드

SDK는 감지만 함. 대응 UX는 호스트 앱 책임. 권장 패턴:

### Soft warning (앱테크 앱 권장)

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

### Hard gate (주의: AdMob 정책 검토 필요)

광고 차단 해제를 강요하는 것은 Play Store 정책상 리스크. 사용 전 법무 / 정책 리뷰 필수.

---

## FAQ

### Q. 내장 probe가 실제 광고 도메인에 HTTP 요청을 날리는 건가?

A. 네. HEAD 요청으로 연결성만 확인. 응답 body를 받지 않아 대역폭 미미. 기본 interval은 60초.

### Q. Probe 비용이 걱정된다

A. 기본 5 도메인 × HEAD × 60초 주기 = 세션당 수 KB 수준. `intervalMs`를 올리거나, 특정 상황에서만 `detector.start()` 하도록 조정.

### Q. False positive가 나온다. 어떻게 디버깅?

A. 개발 중엔 `LogcatBlockEventReporter()` 추가해서 `adb logcat -s anecdote` 로 모든 signal 관찰. 어떤 signal이 점수를 올렸는지 `BlockState.Suspected.signals` / `Blocked.signals` 에서 확인.

### Q. Confidence.LOW는 무시해도 되나?

A. 권장. HIGH 또는 MEDIUM 이상으로 대응 UX 트리거. LOW는 데이터가 부족해서 확신 못하는 단계.

### Q. 지하철, 엘리베이터 들어가서 네트워크 끊겼다 → Blocked로 판정?

A. 아니. Probe에서 ad 도메인과 control 도메인이 둘 다 실패하면 delta가 0 → 점수 증가 없음. 또한 `gracePeriodMs` 기본 5초는 cold start 직후 transient 방어. 네트워크 환경이 바뀌면 자동으로 세션 reset.

### Q. VPN 쓰는 유저는 무조건 Blocked로 잡히나?

A. 아니. VPN은 `+10`점 보너스만. 다른 신호(probe, load failure)가 함께 있어야 threshold 초과.

### Q. 중국 등 구글 광고 원천 차단 지역에서도 Blocked로 잡히나?

A. 아니. `PolicyConfig.disabledRegionMccs` 기본값 `{460}` (중국)이 있어 SIM MCC가 중국이면 판정 자체가 Unknown 반환. 다른 지역도 필요시 추가.

### Q. 한 번 Blocked 판정 났는데 계속 재평가되나?

A. `Confidence.HIGH` Blocked로 확정되면 네트워크 환경 변경 전까지 재평가 중단. `Confidence.LOW/MEDIUM` 이면 maxEvaluationsPerSession 한도까지 재평가.

### Q. 유저가 VPN을 켜거나 Private DNS 바꾸면?

A. 세션이 자동 reset. 새 grace period 적용, 처음부터 다시 판정. 네트워크 전환에 자연스러운 대응.

### Q. Firebase에 이벤트가 너무 많이 올라옴

A. `FirebaseBlockEventReporter(analytics, includeSignalEvents = false)` 로 signal-level 이벤트 끄고 state 전이만 전송. 혹은 `intervalMs`를 120_000 이상으로 올려서 probe 빈도 낮춤.

### Q. 테스트 환경에서 SDK가 의도대로 동작하는지 확인하고 싶다

A. `CustomSignalSource` 로 원하는 signal을 직접 주입해서 state 전이를 유도. 샘플 앱의 "Fail" / "Success" 버튼이 이 패턴.

### Q. 앱 백그라운드에서도 감지 돌아가나?

A. 현재는 Activity lifecycle에 detector.start/stop을 묶는 설계. 백그라운드 감지가 필요하면 `Application.onCreate` 에서 start, 프로세스 종료 전엔 stop 안 함. 단 전력/데이터 사용 증가 주의.

---

## 관련 문서

- [quick-start.md](quick-start.md) — 5분 통합
- [adapter-guide.md](adapter-guide.md) — 새 광고 SDK 어댑터 작성
- [phase1-architecture.md](phase1-architecture.md) — 설계 배경
