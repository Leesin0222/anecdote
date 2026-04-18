# 어댑터 작성 가이드

anecdote SDK에 새로운 광고 네트워크 어댑터를 추가하고 싶으실 때 참고하실 수 있는 문서입니다.

---

## TL;DR

1. 새 Gradle 모듈 `anecdote-adapter-<네트워크>`를 만들어 주세요.
2. `AdNetworkSignalSource`를 구현하고, 상황에 맞게 `LoadSucceeded` / `LoadFailed` signal을 emit 하도록 작성합니다.
3. 광고 SDK의 에러 코드를 `ErrorType`으로 매핑하는 함수를 작성합니다.
4. 단위 테스트를 함께 추가해 주시면 안심하고 사용하실 수 있습니다.
5. 마지막으로 `settings.gradle.kts`에 모듈을 등록해 주세요.

레퍼런스가 필요하시다면 `anecdote-adapter-admob/` 모듈을 참고해 주시면 좋습니다. 특히 `GmaSignalSource.kt`, `GmaErrorMapping.kt`가 가장 대표적인 예시입니다.

---

## 어댑터 vs CustomSignalSource

먼저 "어댑터를 만들지, 말지"부터 정하고 시작하시는 편을 추천합니다.

**`CustomSignalSource`로 충분한 경우** (가장 흔한 케이스입니다)

- 해당 광고 네트워크가 호스트 앱 한두 곳에서만 사용되는 경우입니다.
- 단순히 콜백 2~3개만 래핑하면 끝나는 경우입니다.
- 미디에이션 chain 분해 같은 심화 기능이 필요 없는 경우입니다.

→ 호스트 앱에서 `CustomSignalSource("my-network")`를 만들어 `emitLoadFailed()` / `emitLoadSucceeded()`만 호출하시면 충분합니다.

**어댑터를 별도 모듈로 만들어야 하는 경우**

- 사내 앱 2개 이상에서 같은 SDK를 재사용하는 경우입니다.
- SDK 에러 코드 → `ErrorType` 매핑에 도메인 지식이 필요한 경우입니다.
- 미디에이션 같은 SDK 내부 구조를 분해해야 하는 경우입니다.
- 여러 광고 포맷(Banner/Interstitial/Rewarded)을 공통으로 처리해야 하는 경우입니다.

→ 이런 상황이라면 별도 모듈로 분리해 여러 앱이 함께 사용하도록 구성하시는 편이 좋습니다.

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

### `settings.gradle.kts`에 등록

```kotlin
include(":anecdote-adapter-mynetwork")
```

---

## 2. `networkId` 결정

공개 상수로 정의해 주세요. 소문자로, 하이픈 없이, 짧고 명확하게 만드시는 걸 권장합니다.

```kotlin
public companion object {
    public const val NETWORK_ID: String = "mynetwork"
}
```

**예약어는 피해 주시기 바랍니다.** `"probe"`, `"env"`는 core 내부에서 이미 사용하고 있고, `"admob"`은 `GmaSignalSource` 전용입니다.

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

    override fun start() { /* no-op: 콜백 기반 signal source입니다 */ }
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

        // 나머지 콜백은 delegate로만 넘겨주시면 됩니다
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

### 구현할 때 기억해 두면 좋은 포인트

- **`start()` / `stop()`은 대체로 no-op로 두면 됩니다.** 사용자가 SDK 콜백을 어댑터에 연결하는 시점에 구독이 자연스럽게 시작됩니다.
- **`tryEmit`을 사용하세요.** SDK 콜백이 동기적으로 호출될 수 있어 suspend가 필요하지 않습니다.
- **기본값은 `replay = 0, extraBufferCapacity = 64`로 두세요.** 구독자가 없을 때 최근 신호가 버려지긴 하지만, `AdBlockDetector`가 `register` 시점부터 구독하기 때문에 실제로 문제가 되지는 않습니다.
- **`networkId` 충돌은 신경 쓰지 않아도 됩니다.** `CustomSignalSource`와 달리 `require()` 체크가 불필요합니다. 호스트 앱이 직접 instantiate하지 않고 adapter 내부 고정 값을 사용하기 때문입니다.

---

## 4. `ErrorType` 매핑 함수

매핑 로직은 별도 파일로 분리해 두시면 테스트가 훨씬 수월합니다.

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

### 매핑하실 때 참고해 주시면 좋은 점

- **모호하면 `UNKNOWN`으로 두시기 바랍니다.** 잘못된 매핑은 오탐보다 더 나쁜 결과(잘못된 가중치 부여)를 만들 수 있습니다.
- **`NETWORK_ERROR`는 실제 네트워크 계층 에러에만 한정해 주세요.** "서버가 401 반환" 같은 경우는 `INTERNAL_ERROR`나 `INVALID_REQUEST`가 더 적절합니다.
- **`NO_FILL`은 광고 재고가 없다는 뜻입니다.** 차단 감지에서는 가중치가 낮습니다 (비즈니스 이유로 흔히 발생합니다). 차단 의심도가 가장 높은 것은 `NETWORK_ERROR`입니다.
- **`rawErrorCode` / `rawErrorMessage`는 그대로 보존해 주세요.** Firebase 리포터에서 디버깅할 때 유용하게 활용됩니다.

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

    // ... 각 코드별로 테스트를 추가해 주세요

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

### 꼭 챙겨주시면 좋은 테스트 케이스

- [ ] `onAdLoaded` 계열 → `LoadSucceeded` emit, networkId가 올바른지 확인합니다
- [ ] `onAdFailed` 계열 → `LoadFailed` emit, `errorType` 매핑이 정확한지 확인합니다
- [ ] `rawErrorCode` / `rawErrorMessage`가 보존되는지 확인합니다
- [ ] delegate를 옵셔널로 주입했을 때 모든 콜백이 delegate로 잘 forward 되는지 확인합니다
- [ ] `start` / `stop`이 no-op로서 멱등하게 동작하는지 확인합니다

---

## 6. 미디에이션 분해 (advanced)

어댑터 대상 SDK 자체가 mediation이라면 chain을 분해해서 개별 network signal을 추가로 emit 하실 수 있습니다. `GmaSignalSource.emitAdapterSignals`와 `GmaMediationMapping`을 참고해 주시면 됩니다.

핵심만 정리해 드리면 다음과 같은 흐름입니다.

- Overall signal 1개 (해당 adapter의 `networkId`)
- 각 하위 adapter signal N개 (mediated network id)
- 자기 자신(`networkId`)이 chain에 들어있다면 **skip** 합니다 — 중복 방지용입니다.

---

## 7. 샘플 앱에서 사용

```kotlin
val mynetSource = MyNetworkSignalSource()
detector.registerSignalSource(mynetSource)

myNetworkAd.listener = mynetSource.adListener(myExistingListener)
```

---

## 8. 체크리스트

어댑터를 마무리하기 전에 아래 항목들을 한 번씩 점검해 주시면 좋습니다.

- [ ] 모듈 디렉토리 + `build.gradle.kts` + `AndroidManifest.xml` + `consumer-rules.pro`
- [ ] `settings.gradle.kts`에 `include(":anecdote-adapter-xxx")` 추가
- [ ] `NETWORK_ID`가 예약어와 겹치지 않는지 (`probe`, `env`, `admob` 금지)
- [ ] `AdNetworkSignalSource` 구현 (`networkId`, `signals`, `start`, `stop`)
- [ ] `toAnecdoteErrorType()` 매핑 함수 작성
- [ ] `@JvmOverloads`로 Java 호환성 확보 (delegate 생략이 가능하도록)
- [ ] 단위 테스트 작성 (에러 매핑 + source 동작 + delegate forwarding)
- [ ] `./gradlew :anecdote-adapter-xxx:testDebugUnitTest` 통과
- [ ] 전체 빌드 통과 (`./gradlew assembleDebug`)
- [ ] 샘플 앱에 통합 예제 1개 추가 (선택이지만 권장합니다)

---

## 참고

- `anecdote-adapter-admob/` — 레퍼런스 구현
- `docs/phase1-architecture.md` — 모듈 규칙과 의존성 그래프
- `anecdote-core/src/main/java/com/yongjincompany/anecdote/signal/` — Signal/Source 인터페이스 정의
