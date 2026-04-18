# Phase 1: anecdote SDK 아키텍처 설계

**상태**: 초안 확정 (2026-04-17)
**다음 단계**: Phase 2 — Core 모듈 스캐폴딩

---

## 확정된 결정사항

| # | 항목 | 결정 |
|---|------|------|
| 1 | 기존 `app/` 모듈 처리 | 삭제 후 `sample/` 신규 생성 |
| 2 | API 스타일 | Builder 패턴 |
| 3 | 루트 패키지 | `com.yongjincompany.anecdote` |
| 4 | 디버그 리포터 | `reporter-logcat` 1개만 (memory 리포터 미포함) |
| 5 | Gradle convention plugin 위치 | `build-logic/` |
| 6 | minSdk | 24 (Android 7.0) |

### 상위 기획 결정 (이전 확정)
- SDK 제품명: **anecdote**
- 배포 범위: 사내 앱 전용 (AAR 직접 전달 허용, Maven은 후순위)
- 검증 대상: 사내 앱테크(보상형) 앱들
- 스코프: **감지 전용** (차단 회피/게이트 UX 미포함)
- HTTP 클라이언트: OkHttp
- iOS/KMP: 미지원 (Android only)
- 게이트 UX: 호스트 앱 책임

---

## 1. 멀티모듈 디렉토리 트리

```
anecdote/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
├── build-logic/                             # Gradle convention plugins
│   ├── settings.gradle.kts
│   └── convention/
│       ├── build.gradle.kts
│       └── src/main/kotlin/
│           ├── AnecdoteLibraryConventionPlugin.kt
│           └── AnecdoteApplicationConventionPlugin.kt
├── anecdote-core/                           # 광고 SDK 독립 감지 로직
├── anecdote-adapter-admob/                  # GMA Next-Gen 어댑터
├── anecdote-reporter-firebase/              # Firebase Analytics 리포터
├── anecdote-reporter-logcat/                # 디버그용 로그 리포터
├── sample/                                  # 통합 샘플 앱 (Compose)
└── docs/
    └── phase1-architecture.md
```

**마이그레이션 작업**:
- 기존 `app/` 디렉토리 완전 삭제
- `sample/` 모듈로 Compose 기반 신규 생성
- `settings.gradle.kts`에서 `:app` 제거, 신규 모듈 5개 등록

---

## 2. `AdBlockDetector` Public API

### 2.1 엔트리포인트

```kotlin
package com.yongjincompany.anecdote

public class AdBlockDetector private constructor(
    private val config: DetectorConfig,
) {
    public val state: StateFlow<BlockState>

    public fun start()
    public fun stop()
    public fun reevaluate()

    public fun registerSignalSource(source: AdNetworkSignalSource)
    public fun unregisterSignalSource(source: AdNetworkSignalSource)

    public class Builder(private val context: Context) {
        public fun probe(block: ProbeConfig.Builder.() -> Unit): Builder
        public fun policy(block: PolicyConfig.Builder.() -> Unit): Builder
        public fun addReporter(reporter: BlockEventReporter): Builder
        public fun addSignalSource(source: AdNetworkSignalSource): Builder
        public fun build(): AdBlockDetector
    }
}
```

### 2.2 상태 모델

```kotlin
public sealed interface BlockState {
    public data object Unknown : BlockState
    public data object NotBlocked : BlockState
    public data class Suspected(
        val confidence: Confidence,
        val signals: List<AdNetworkSignal>,
    ) : BlockState
    public data class Blocked(
        val confidence: Confidence,
        val signals: List<AdNetworkSignal>,
    ) : BlockState
}

public enum class Confidence { LOW, MEDIUM, HIGH }
```

### 2.3 설정 객체

```kotlin
public class ProbeConfig private constructor(
    public val timeoutMs: Long,
    public val gracePeriodMs: Long,
    public val adDomains: List<String>,
    public val controlDomains: List<String>,
    public val intervalMs: Long,
) {
    public class Builder {
        public var timeoutMs: Long = 3000
        public var gracePeriodMs: Long = 5000
        public var adDomains: List<String> = DEFAULT_AD_DOMAINS
        public var controlDomains: List<String> = DEFAULT_CONTROL_DOMAINS
        public var intervalMs: Long = 60_000
        internal fun build(): ProbeConfig = ProbeConfig(...)
    }
}

public class PolicyConfig private constructor(
    public val thresholdSuspected: Int,     // 0..100
    public val thresholdBlocked: Int,
    public val maxEvaluationsPerSession: Int,
    public val disabledRegionMccs: Set<Int>, // e.g. 460 (CN)
) {
    public class Builder {
        public var thresholdSuspected: Int = 40
        public var thresholdBlocked: Int = 70
        public var maxEvaluationsPerSession: Int = 3
        public var disabledRegionMccs: Set<Int> = setOf(460)
        internal fun build(): PolicyConfig = PolicyConfig(...)
    }
}
```

### 2.4 사용 예시

```kotlin
val detector = AdBlockDetector.Builder(context)
    .probe {
        timeoutMs = 3000
        gracePeriodMs = 5000
    }
    .policy {
        thresholdSuspected = 40
        thresholdBlocked = 70
    }
    .addReporter(FirebaseBlockEventReporter(firebaseAnalytics))
    .addReporter(LogcatBlockEventReporter())
    .build()

detector.start()

lifecycleScope.launch {
    detector.state.collect { state ->
        when (state) {
            is BlockState.Blocked -> showBlockedUx(state.confidence)
            is BlockState.Suspected -> logSoftWarning()
            BlockState.NotBlocked, BlockState.Unknown -> Unit
        }
    }
}
```

**AdMob 어댑터 연결** (선택적):
```kotlin
val admobSource = GmaSignalSource()
detector.registerSignalSource(admobSource)
// AdMob 호출부에서 admobSource가 자동으로 콜백 훅
```

---

## 3. `AdNetworkSignal` sealed class 계층

```kotlin
package com.yongjincompany.anecdote.signal

public sealed class AdNetworkSignal {
    public abstract val networkId: String       // "admob", "applovin", "probe", "env"
    public abstract val timestamp: Long

    public data class LoadFailed(
        override val networkId: String,
        override val timestamp: Long,
        val errorType: ErrorType,
        val rawErrorCode: Int?,
        val rawErrorMessage: String?,
    ) : AdNetworkSignal()

    public data class LoadSucceeded(
        override val networkId: String,
        override val timestamp: Long,
    ) : AdNetworkSignal()

    public data class ProbeResult(
        override val networkId: String,         // 고정: "probe"
        override val timestamp: Long,
        val domain: String,
        val isControl: Boolean,
        val reachable: Boolean,
        val latencyMs: Long?,
    ) : AdNetworkSignal()

    public data class NetworkEnvironment(
        override val networkId: String,         // 고정: "env"
        override val timestamp: Long,
        val vpnActive: Boolean,
        val privateDnsActive: Boolean,
        val privateDnsServer: String?,
        val mcc: Int?,
    ) : AdNetworkSignal()
}

public enum class ErrorType {
    NETWORK_ERROR,
    NO_FILL,
    INVALID_REQUEST,
    INTERNAL_ERROR,
    TIMEOUT,
    DNS_RESOLUTION_FAILED,
    UNKNOWN,
}

public interface AdNetworkSignalSource {
    public val networkId: String
    public val signals: SharedFlow<AdNetworkSignal>
    public fun start()
    public fun stop()
}
```

### 리포터 인터페이스

```kotlin
package com.yongjincompany.anecdote.report

public interface BlockEventReporter {
    public fun onStateChanged(state: BlockState)
    public fun onSignalReceived(signal: AdNetworkSignal)
}
```

---

## 4. 모듈 의존성 그래프

```
                   ┌──────────────────────────┐
                   │   anecdote-core          │
                   │                          │
                   │ - AdBlockDetector        │
                   │ - AdDomainProber         │
                   │ - SignalAggregator       │
                   │ - BlockStateEvaluator    │
                   │ - NetworkEnvironmentSrc  │
                   │ - CustomSignalSource     │
                   │                          │
                   │ deps: OkHttp, Coroutines,│
                   │       androidx.core      │
                   └────────────▲─────────────┘
                                │ (api)
    ┌───────────────────────────┼───────────────────────┐
    │                           │                       │
┌───┴──────────────┐   ┌────────┴─────────────┐   ┌─────┴──────────────┐
│ adapter-admob    │   │ reporter-firebase    │   │ reporter-logcat    │
│                  │   │                      │   │                    │
│ + play-services- │   │ + firebase-          │   │ (no extra dep)     │
│   ads            │   │   analytics-ktx      │   │                    │
└───┬──────────────┘   └────────┬─────────────┘   └─────┬──────────────┘
    │                           │                       │
    └───────────────────────────┴───────────────────────┘
                                │
                         ┌──────┴──────┐
                         │   sample    │
                         │  (Compose)  │
                         └─────────────┘
```

**의존성 규칙**:
- `anecdote-core`는 광고 SDK / analytics SDK에 의존하지 않음
- 어댑터 ↔ 어댑터 의존 금지
- 리포터 ↔ 리포터 의존 금지
- 어댑터 ↔ 리포터 의존 금지
- `sample`만 전체 조합 가능

---

## 5. Gradle 설정 초안

### 5.1 `settings.gradle.kts`

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "anecdote"

include(":anecdote-core")
include(":anecdote-adapter-admob")
include(":anecdote-reporter-firebase")
include(":anecdote-reporter-logcat")
include(":sample")
```

### 5.2 `gradle/libs.versions.toml`

```toml
[versions]
agp = "8.5.0"
kotlin = "2.0.0"
coroutines = "1.8.1"
okhttp = "4.12.0"
androidx-core = "1.13.1"
androidx-lifecycle = "2.8.0"
compose-bom = "2024.06.00"
compose-activity = "1.9.0"
play-services-ads = "23.2.0"
firebase-bom = "33.1.0"
junit = "4.13.2"
mockk = "1.13.11"
turbine = "1.1.0"
coroutines-test = "1.8.1"

[libraries]
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines-test" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "androidx-lifecycle" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-activity = { module = "androidx.activity:activity-compose", version.ref = "compose-activity" }
play-services-ads = { module = "com.google.android.gms:play-services-ads", version.ref = "play-services-ads" }
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebase-bom" }
firebase-analytics-ktx = { module = "com.google.firebase:firebase-analytics-ktx" }
junit = { module = "junit:junit", version.ref = "junit" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
android-library = { id = "com.android.library", version.ref = "agp" }
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

### 5.3 Convention Plugin 샘플

```kotlin
// build-logic/convention/src/main/kotlin/AnecdoteLibraryConventionPlugin.kt
class AnecdoteLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            compileSdk = 34
            defaultConfig {
                minSdk = 24
                consumerProguardFiles("consumer-rules.pro")
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
        // Kotlin jvmTarget = 17
    }
}
```

---

## 6. Phase 2 진입 체크리스트

Phase 2(Core 모듈) 시작 전 완료해야 할 것들:

- [ ] 기존 `app/` 디렉토리 삭제
- [ ] `build-logic/` 디렉토리 및 convention plugin 생성
- [ ] `gradle/libs.versions.toml` 작성
- [ ] `settings.gradle.kts` 업데이트 (모듈 5개 등록)
- [ ] `anecdote-core/build.gradle.kts` 작성 (convention plugin 적용)
- [ ] `anecdote-core` 빈 모듈 빌드 성공 확인
- [ ] `AdBlockDetector`, `BlockState`, `AdNetworkSignal` 등 Public API 인터페이스/시그니처만 먼저 커밋 (구현은 이후)

이 체크리스트가 끝나면 Phase 2.1(Active Probe 엔진)부터 실제 로직 구현 진입.
