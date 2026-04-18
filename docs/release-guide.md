# Release Guide

anecdote SDK의 릴리즈 워크플로를 안내드리는 문서입니다.

---

## 버전 규칙

[SemVer 2.0](https://semver.org/)을 따릅니다: `MAJOR.MINOR.PATCH`

| 변경 종류 | 예시 | 버전 증가 |
|-----------|------|----------|
| Public API를 깨는 변경 | `AdBlockDetector.Builder` 시그니처 변경, 클래스 제거 | MAJOR |
| 하위 호환되는 기능 추가 | 새 어댑터 모듈, 새 optional 파라미터, 새 `BlockState` variant | MINOR |
| 버그 수정, 내부 구현 개선 | 점수 계산 정확도 개선, 오탐 수정 | PATCH |

버전은 모든 `anecdote-*` 모듈이 **동일하게 증가**합니다. Core와 adapter 간 호환성이 꼬이는 걸 방지하기 위함입니다.

### 예외 규정

- `internal/**` 패키지 내부 변경은 public API에 노출되지 않으므로 **PATCH**로 처리합니다.
- 새 reserved `networkId`가 추가되는 경우, 유저 코드가 해당 ID를 사용할 가능성이 있어 **MINOR 이상**으로 처리해 주시기 바랍니다.
- Default threshold 값 변경 (`thresholdSuspected`, `thresholdBlocked` 등)은 행동이 바뀌는 변경이므로 **MINOR**로 처리하시길 권장합니다 (릴리즈 노트에도 반드시 명시해 주세요).
- Probe 기본 도메인이 변경되는 경우에도 MINOR로 처리합니다.

---

## 릴리즈 절차

### 1. 버전 bump

`build.gradle.kts` (루트)의 `anecdoteVersion` 상수를 수정합니다.

```kotlin
val anecdoteVersion: String by extra("0.2.0")
```

모든 `anecdote-*` 모듈이 subprojects 설정을 통해 자동으로 동일 버전으로 맞춰집니다.

### 2. 빌드 + 테스트

```bash
./gradlew clean
./gradlew assembleDebug testDebugUnitTest
```

**초록불을 꼭 확인해 주시기 바랍니다.**

### 3. AAR 수집

```bash
./gradlew collectAars
```

`build/artifacts/`에 버전이 붙은 AAR이 생성됩니다.

```
build/artifacts/
├── anecdote-core-0.2.0.aar
├── anecdote-adapter-admob-0.2.0.aar
├── anecdote-reporter-firebase-0.2.0.aar
└── anecdote-reporter-logcat-0.2.0.aar
```

### 4. 사내 앱에 배포

**옵션 A: AAR 파일 직접 전달 (현재 기본 방식)**

사내 앱의 `app/libs/`에 AAR을 복사한 뒤 아래처럼 등록해 주시면 됩니다.

```kotlin
dependencies {
    implementation(files("libs/anecdote-core-0.2.0.aar"))
    implementation(files("libs/anecdote-adapter-admob-0.2.0.aar"))
    // ...
}
```

AAR의 transitive 의존성(OkHttp 등)은 호스트 앱에서 직접 추가해 주셔야 합니다.

**옵션 B: 사내 Maven (추후)**

현재는 Maven Publish plugin 설정이 준비되어 있지 않습니다. 필요하신 경우 별도로 문서화하여 공유드리도록 하겠습니다.

### 5. 변경 로그

`CHANGELOG.md`에 버전별 변경사항을 기록해 주시면 좋습니다 (선택).

```markdown
## 0.2.0 (2026-04-18)

### Added
- GmaSignalSource: UMP consent 연동 (`ConsentChecker` / `UmpConsentChecker`)

### Changed
- 기본 `gracePeriodMs` 5_000 → 3_000

### Fixed
- 네트워크 변경 시 세션 reset 누락
```

### 6. git tag

```bash
git commit -m "release: v0.2.0"
git tag -a v0.2.0 -m "v0.2.0"
git push origin main --tags
```

---

## 호환성 매트릭스

Phase 2 이후부터는 core와 adapter 간 버전 호환성을 일대일로 맞춥니다. 즉 `anecdote-adapter-admob-0.2.0`은 `anecdote-core-0.2.0`과의 호환만 보장합니다.

**Mixing은 피해 주시기 바랍니다.** `core-0.1.0` + `adapter-admob-0.2.0`처럼 혼용하시면 내부 `signal` / `signal source` 인터페이스 변경으로 인해 런타임 에러가 발생할 수 있습니다.

Breaking change가 많아지는 시점에는 매트릭스 테이블을 별도로 유지할 예정입니다.

---

## Pre-release (optional)

실험적 기능을 먼저 테스트해 보고 싶으실 때는 아래 형식을 사용해 주시면 됩니다.

```
0.3.0-alpha.1
0.3.0-beta.1
0.3.0-rc.1
```

참여 의사가 있는 사내 앱 팀에만 먼저 배포하고, 안정화가 확인된 이후에 `0.3.0`을 정식 릴리즈하는 방식을 권장합니다.

---

## Rollback

문제가 발견된 경우 아래 순서로 대응해 주시면 됩니다.

1. 이전 버전 AAR을 다시 전달해 주시면 사내 앱이 바로 교체할 수 있습니다.
2. 긴급 PATCH 릴리즈(예: `0.2.1`)를 준비합니다.
3. git tag를 제거하고 재발행하는 방식은 지양해 주시기 바랍니다 — 새 PATCH 버전으로 올리는 편을 선호합니다.

---

## 체크리스트 (매 릴리즈마다 확인해 주세요)

- [ ] `anecdoteVersion` bump (올바른 MAJOR/MINOR/PATCH 선택)
- [ ] `./gradlew clean assembleDebug testDebugUnitTest` 통과 확인
- [ ] `./gradlew collectAars` 성공 및 `build/artifacts/`에 AAR 생성 확인
- [ ] `CHANGELOG.md` 업데이트 (선택)
- [ ] git commit + tag
- [ ] 사내 앱에 AAR 전달 및 통합 테스트
