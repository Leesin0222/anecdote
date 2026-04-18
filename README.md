# anecdote

광고 SDK에 독립적으로 동작하는 Android 광고 차단 감지 SDK입니다.

Active Probe(HTTP 기반 광고 도메인 도달성 테스트), 네트워크 환경 감지(VPN / Private DNS), 광고 SDK 콜백 신호를 통합해 사용자의 광고 차단 여부를 `StateFlow<BlockState>`로 노출합니다.

---

## 특징

- **플러그인 아키텍처** — Core는 광고 SDK에 독립적이며, 어댑터와 리포터는 모두 opt-in 구조입니다.
- **멀티 신호 조합** — Probe, 광고 SDK 콜백, 네트워크 환경 신호를 함께 활용해 단일 신호 오탐을 방지합니다.
- **점수 기반 판정** — 신호별 가중치를 합산해 `Unknown` / `NotBlocked` / `Suspected` / `Blocked` (+ `Confidence`)로 판정합니다.
- **오탐 방어** — Cold start grace period, 네트워크 전환 감지, 중국 등 구글 광고 불가 지역 자동 제외를 지원합니다.
- **UMP (Consent) 연동** — GDPR/CCPA로 발생하는 load failure를 광고 차단과 구분합니다.
- **AdMob 미디에이션 분해** — 미디에이션된 AppLovin / Unity / ironSource 등을 개별 신호로 기록합니다.

---

## 모듈 구조

```
anecdote-core                 # 광고 SDK 독립 감지 로직 (필수)
anecdote-adapter-admob        # Google Mobile Ads 어댑터 (+ 미디에이션 분해)
anecdote-reporter-firebase    # Firebase Analytics 리포터
anecdote-reporter-logcat      # 디버그용 Log.d 리포터
sample                        # 샘플 앱
```

필요한 모듈만 골라서 의존성에 추가하면 됩니다. 다른 광고 네트워크나 분석 도구도 공개 인터페이스를 통해 쉽게 확장할 수 있습니다.

---

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

자세한 내용은 [docs/quick-start.md](docs/quick-start.md)를 참고해 주세요.

---

## 문서

| 문서 | 내용 |
|------|------|
| [quick-start.md](docs/quick-start.md) | 5분 통합 가이드 |
| [integration-guide.md](docs/integration-guide.md) | 상세 옵션 / 정책 튜닝 / FAQ |
| [adapter-guide.md](docs/adapter-guide.md) | 새 광고 SDK 어댑터 작성법 |
| [phase1-architecture.md](docs/phase1-architecture.md) | 설계 배경 / API 설계 |
| [release-guide.md](docs/release-guide.md) | SemVer / 릴리즈 워크플로 |

---

## 빌드

```bash
# 테스트 + APK 빌드
./gradlew assembleDebug testDebugUnitTest

# 배포용 AAR 수집
./gradlew collectAars
# → build/artifacts/anecdote-*-<version>.aar
```

**JDK 17 이상**이 필요합니다. macOS에서는 Homebrew로 설치할 수 있습니다.

```bash
brew install openjdk@17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
```

---

## 스코프 한계 (의도적)

- **감지만 담당합니다.** 광고 차단 해제를 강요하는 UX는 호스트 앱의 책임입니다. Play Store 정책 리스크를 SDK가 떠안지 않도록 설계했습니다.
- **Android 전용입니다.** iOS 대응 계획은 없습니다 (광고 생태계 자체가 다릅니다).
- **사내 배포를 우선합니다.** 외부 공개 여부는 현장 검증 이후 별도로 결정합니다.
