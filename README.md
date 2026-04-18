# anecdote

광고 SDK에 독립적으로 동작하는 Android 광고 차단 감지 SDK.

Active Probe (HTTP 기반 광고 도메인 도달성 테스트), 네트워크 환경 감지 (VPN / Private DNS), 광고 SDK 콜백 신호를 통합해서 사용자의 광고 차단 여부를 `StateFlow<BlockState>` 로 노출.

---

## 특징

- **플러그인 아키텍처**: Core는 광고 SDK 독립. 어댑터/리포터는 모두 opt-in.
- **멀티 신호 조합**: Probe + 광고 SDK 콜백 + 네트워크 환경. 단일 신호 오탐 방지.
- **score-based 판정**: 신호별 가중치 합산 → `Unknown` / `NotBlocked` / `Suspected` / `Blocked` (+ `Confidence`).
- **오탐 방어**: Cold start grace period, 네트워크 전환 감지, 중국 등 구글 광고 불가 지역 자동 제외.
- **UMP (Consent) 연동**: GDPR/CCPA로 인한 load failure 를 광고 차단과 구별.
- **AdMob 미디에이션 분해**: 미디에이션된 AppLovin / Unity / ironSource 등을 개별 신호로 기록.

---

## 모듈 구조

```
anecdote-core                 # 광고 SDK 독립 감지 로직 (필수)
anecdote-adapter-admob        # Google Mobile Ads 어댑터 (+ 미디에이션 분해)
anecdote-reporter-firebase    # Firebase Analytics 리포터
anecdote-reporter-logcat      # 디버그용 Log.d 리포터
sample                        # 샘플 앱
```

필요한 것만 선택해서 의존성 추가. 다른 광고 네트워크 / 분석 도구도 공개 인터페이스로 쉽게 확장.

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

상세: [docs/quick-start.md](docs/quick-start.md)

---

## 문서

| 문서 | 내용 |
|------|------|
| [quick-start.md](docs/quick-start.md) | 5분 통합 가이드 |
| [integration-guide.md](docs/integration-guide.md) | 상세 옵션 / 정책 튜닝 / FAQ |
| [adapter-guide.md](docs/adapter-guide.md) | 새 광고 SDK 어댑터 작성 |
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

**JDK 17** 이상 필요. macOS에선 Homebrew:
```bash
brew install openjdk@17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
```

---

## 스코프 한계 (의도적)

- **감지만** 한다. 광고 차단 해제를 강요하는 UX 는 호스트 앱 책임. Play Store 정책 리스크를 SDK가 떠안지 않음.
- **Android only.** iOS 대응 계획 없음 (광고 생태계 자체가 다름).
- **사내 배포 우선.** 외부 공개는 현장 검증 후 별도 결정.
