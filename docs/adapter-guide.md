# 어댑터 작성 가이드

anecdote SDK에 새로운 광고 네트워크 어댑터를 추가하는 방법.

---

## TL;DR

1. 새 Gradle 모듈 `anecdote-adapter-<네트워크>` 생성
2. `AdNetworkSignalSource` 구현 → `LoadSucceeded` / `LoadFailed` signal emit
3. 광고 SDK 에러 코드 → `ErrorType` 매핑 함수 작성
4. 단위 테스트 추가
5. `settings.gradle.kts`에 등록

레퍼런스 구현: `anecdote-adapter-admob/` (특히 `GmaSignalSource.kt`, `GmaErrorMapping.kt`).

---

## 어댑터 vs CustomSignalSource

먼저 어댑터를 **만들지 말지** 결정.

**CustomSignalSource 로 충분한 경우** (가장 흔함):
- 해당 광고 네트워크가 호스트 앱 1~2개에서만 쓰임
- 단순 콜백 2~3개 래핑이면 됨
- 미디에이션 chain 분해 같은 심화 기능 불필요

→ 호스트 앱에서 `CustomSignalSource("my-network")` 만들고 `emitLoadFailed()` / `emitLoadSucceeded()` 호출.

**어댑터를 만들어야 하는 경우**:
- 사내 앱 2개 이상에서 같은 SDK 재사용
- SDK 에러 코드 → `ErrorType` 매핑에 도메인 지식 필요
- 미디에이션 같은 SDK 내부 구조 분해
- 여러 광고 포맷 (Banner/Interstitial/Rewarded) 공통 처리

→ 별도 모듈로 만들어서 모든 앱이 공유.

---

## 1. 모듈 생성

```bash
mkdir -p anecdote-adapter-mynetwork/src/main/java/com/yongjincompany/anecdote/adapter/mynetwork
mkdir -p anecdote-adapter-mynetwork/src/test/java/com/yongjincompany/anecdote/adapter/mynetwork
```

### `anecdote-adapter-mynetwork/build.gradle.kts`

```kotlin
plugins {
    id("anecdote.android.library")
}

android {
    namespace = "com.yongjincompany.anecdote.adapter.mynetwork"
}

dependencies {
    api(project(":anecdote-core"))
    implementation("com.mynetwork:sdk:1.2.3")       // 해당 SDK 의존성
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

### 빈 AndroidManifest

```
anecdote-adapter-mynetwork/src/main/AndroidManifest.xml
```
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

### `consumer-rules.pro`

```
# MyNetwork adapter consumer rules
```

### `settings.gradle.kts` 에 등록

```kotlin
include(":anecdote-adapter-mynetwork")
```

---

## 2. `networkId` 결정

공개 상수로 정의. 소문자, 하이픈 없이, 짧고 명확하게.

```kotlin
public companion object {
    public const val NETWORK_ID: String = "mynetwork"
}
```

**예약어 금지**: `"probe"`, `"env"` (core에서 내장 sources가 사용). `"admob"`은 `GmaSignalSource` 전용.

---

## 3. `AdNetworkSignalSource` 구현 템플릿

```kotlin
package com.yongjincompany.anecdote.adapter.mynetwork

import com.mynetwork.sdk.AdListener
import com.mynetwork.sdk.LoadError
import com.yongjincompany.anecdote.signal.AdNetworkSignal
import com.yongjincompany.anecdote.signal.AdNetworkSignalSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

public class MyNetworkSignalSource : AdNetworkSignalSource {

    override val networkId: String = NETWORK_ID

    private val _signals = MutableSharedFlow<AdNetworkSignal>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    override val signals: SharedFlow<AdNetworkSignal> = _signals.asSharedFlow()

    override fun start() { /* no-op: 콜백 기반 signal source */ }
    override fun stop() { /* no-op */ }

    @JvmOverloads
    public fun adListener(delegate: AdListener? = null): AdListener = object : AdListener() {
        override fun onAdLoaded() {
            emitLoadSucceeded()
            delegate?.onAdLoaded()
        }

        override fun onAdFailed(error: LoadError) {
            emitLoadFailed(error)
            delegate?.onAdFailed(error)
        }

        // 다른 콜백은 delegate로만 forward
    }

    private fun emitLoadSucceeded() {
        _signals.tryEmit(
            AdNetworkSignal.LoadSucceeded(
                networkId = networkId,
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    private fun emitLoadFailed(error: LoadError) {
        _signals.tryEmit(
            AdNetworkSignal.LoadFailed(
                networkId = networkId,
                timestamp = System.currentTimeMillis(),
                errorType = error.toAnecdoteErrorType(),
                rawErrorCode = error.code,
                rawErrorMessage = error.message,
            )
        )
    }

    public companion object {
        public const val NETWORK_ID: String = "mynetwork"
    }
}
```

### 핵심 원칙

- **`start()` / `stop()`은 no-op** 인 경우가 대부분. 사용자 코드가 SDK 콜백을 어댑터에 연결하는 시점에 구독이 시작됨.
- **`tryEmit` 사용** — SDK 콜백이 동기적으로 불릴 수 있으므로 suspend 불필요.
- **`replay = 0, extraBufferCapacity = 64`** 기본값. 구독자가 없을 때 최근 신호는 버려지지만, `AdBlockDetector`가 `register` 시점부터 구독하므로 문제 없음.
- **`networkId` 충돌 방지**: `CustomSignalSource`와 다르게 `require()` 체크는 불필요 (호스트 앱이 직접 instantiate하지 않고 adapter 내부 고정 값).

---

## 4. `ErrorType` 매핑 함수

별도 파일로 분리하면 테스트하기 쉽다.

```kotlin
// MyNetworkErrorMapping.kt
package com.yongjincompany.anecdote.adapter.mynetwork

import com.mynetwork.sdk.LoadError
import com.yongjincompany.anecdote.signal.ErrorType

internal fun LoadError.toAnecdoteErrorType(): ErrorType = when (code) {
    LoadError.NETWORK_FAILURE -> ErrorType.NETWORK_ERROR
    LoadError.NO_INVENTORY     -> ErrorType.NO_FILL
    LoadError.TIMEOUT          -> ErrorType.TIMEOUT
    LoadError.BAD_REQUEST      -> ErrorType.INVALID_REQUEST
    LoadError.SERVER_ERROR     -> ErrorType.INTERNAL_ERROR
    else                       -> ErrorType.UNKNOWN
}
```

### 매핑 원칙

- **보수적으로 매핑**: 모호하면 `UNKNOWN`. 잘못된 매핑은 오탐보다 나쁘다 (잘못된 가중치 부여).
- **`NETWORK_ERROR`만 실제 네트워크 계층 에러로 한정**: "서버가 401 반환" 같은 건 `INTERNAL_ERROR` 또는 `INVALID_REQUEST`.
- **`NO_FILL`은 광고 재고 없음**: 차단 감지에 가중치가 낮음 (비즈니스 이유로 자주 발생). `NETWORK_ERROR`가 차단 의심도가 가장 높음.
- **`rawErrorCode` / `rawErrorMessage` 보존**: Firebase 리포터에서 디버깅용으로 사용.

---

## 5. 테스트 템플릿

### `MyNetworkErrorMappingTest`

```kotlin
class MyNetworkErrorMappingTest {

    private fun error(code: Int): LoadError = mockk {
        every { this@mockk.code } returns code
    }

    @Test
    fun `NETWORK_FAILURE maps to NETWORK_ERROR`() {
        assertEquals(ErrorType.NETWORK_ERROR, error(LoadError.NETWORK_FAILURE).toAnecdoteErrorType())
    }

    // ... 각 코드별 테스트

    @Test
    fun `unknown code maps to UNKNOWN`() {
        assertEquals(ErrorType.UNKNOWN, error(-999).toAnecdoteErrorType())
    }
}
```

### `MyNetworkSignalSourceTest`

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MyNetworkSignalSourceTest {

    @Test
    fun `onAdLoaded emits LoadSucceeded`() = runTest {
        val source = MyNetworkSignalSource()
        val listener = source.adListener()

        source.signals.test {
            listener.onAdLoaded()
            val signal = awaitItem() as AdNetworkSignal.LoadSucceeded
            assertEquals(MyNetworkSignalSource.NETWORK_ID, signal.networkId)
        }
    }

    @Test
    fun `forwards all callbacks to delegate`() {
        val delegate = mockk<AdListener>(relaxed = true)
        val source = MyNetworkSignalSource()
        val listener = source.adListener(delegate)

        listener.onAdLoaded()

        verify(exactly = 1) { delegate.onAdLoaded() }
    }
}
```

### 필수 테스트 케이스

- [ ] `onAdLoaded` 계열 → `LoadSucceeded` emit, networkId 올바름
- [ ] `onAdFailed` 계열 → `LoadFailed` emit, `errorType` 매핑 정확
- [ ] `rawErrorCode` / `rawErrorMessage` 보존 확인
- [ ] delegate 옵셔널 주입 시 모든 콜백이 delegate로 forward
- [ ] `start` / `stop` no-op 멱등

---

## 6. 미디에이션 분해 (advanced)

SDK 자체가 mediation이면 chain을 분해해서 개별 network signal을 추가 emit할 수 있다. 
`GmaSignalSource.emitAdapterSignals`와 `GmaMediationMapping`을 참고.

요점:
- Overall signal 1개 (해당 adapter의 `networkId`)
- 각 하위 adapter signal N개 (mediated network id)
- 자기 자신 (`networkId`) 이 chain에 들어있으면 **skip** — 중복 방지.

---

## 7. 샘플 앱에서 사용

```kotlin
val mynetSource = MyNetworkSignalSource()
detector.registerSignalSource(mynetSource)

myNetworkAd.listener = mynetSource.adListener(myExistingListener)
```

---

## 8. 체크리스트

어댑터 완성 전 확인:

- [ ] 모듈 디렉토리 + `build.gradle.kts` + `AndroidManifest.xml` + `consumer-rules.pro`
- [ ] `settings.gradle.kts`에 `include(":anecdote-adapter-xxx")` 추가
- [ ] `NETWORK_ID` 예약어 충돌 없음 (`probe`, `env`, `admob` 금지)
- [ ] `AdNetworkSignalSource` 구현 (`networkId`, `signals`, `start`, `stop`)
- [ ] `toAnecdoteErrorType()` 매핑 함수
- [ ] `@JvmOverloads` 로 Java 호환 확보 (delegate 생략 가능)
- [ ] 단위 테스트 작성 (에러 매핑 + source 동작 + delegate forwarding)
- [ ] `./gradlew :anecdote-adapter-xxx:testDebugUnitTest` 통과
- [ ] 전체 빌드 통과 (`./gradlew assembleDebug`)
- [ ] 샘플 앱에 통합 예제 1개 추가 (선택, 권장)

---

## 참고

- `anecdote-adapter-admob/` — 레퍼런스 구현
- `docs/phase1-architecture.md` — 모듈 규칙, 의존성 그래프
- `anecdote-core/src/main/java/com/yongjincompany/anecdote/signal/` — Signal/Source 인터페이스 정의
