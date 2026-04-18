# Release Guide

anecdote SDK 릴리즈 워크플로.

---

## 버전 규칙

[SemVer 2.0](https://semver.org/) 적용: `MAJOR.MINOR.PATCH`

| 변경 종류 | 예시 | 버전 증가 |
|-----------|------|----------|
| Public API 깨지는 변경 | `AdBlockDetector.Builder` 시그니처 변경, 클래스 제거 | MAJOR |
| 하위 호환 기능 추가 | 새 어댑터 모듈, 새 optional 파라미터, 새 `BlockState` variant | MINOR |
| 버그 수정, 내부 구현 개선 | 점수 계산 정확도 개선, 오탐 수정 | PATCH |

버전은 모든 `anecdote-*` 모듈이 **동일하게 증가**. Core와 adapter 간 호환성 꼬임 방지.

### 예외 규정

- `internal/**` 패키지 내부 변경 → public API에 노출 안 되므로 **PATCH**
- 새 reserved `networkId` 추가 → 유저 코드가 해당 ID를 쓸 가능성 있으므로 **MINOR** 이상
- Default threshold 값 변경 (`thresholdSuspected`, `thresholdBlocked` 등) → 행동 변경이라 **MINOR** 권장 (릴리즈 노트에 명시)
- Probe 기본 도메인 변경 → MINOR

---

## 릴리즈 절차

### 1. 버전 bump

`build.gradle.kts` (루트) 의 `anecdoteVersion` 상수 수정:

```kotlin
val anecdoteVersion: String by extra("0.2.0")
```

모든 `anecdote-*` 모듈이 subprojects 설정으로 자동 동일 버전.

### 2. 빌드 + 테스트

```bash
./gradlew clean
./gradlew assembleDebug testDebugUnitTest
```

**초록불 확인 필수.**

### 3. AAR 수집

```bash
./gradlew collectAars
```

`build/artifacts/` 에 versioned AAR 생성:
```
build/artifacts/
├── anecdote-core-0.2.0.aar
├── anecdote-adapter-admob-0.2.0.aar
├── anecdote-reporter-firebase-0.2.0.aar
└── anecdote-reporter-logcat-0.2.0.aar
```

### 4. 사내 앱에 배포

**옵션 A: AAR 파일 직접 전달 (현재 기본)**

사내 앱의 `app/libs/` 에 AAR 복사 후:
```kotlin
dependencies {
    implementation(files("libs/anecdote-core-0.2.0.aar"))
    implementation(files("libs/anecdote-adapter-admob-0.2.0.aar"))
    // ...
}
```

AAR의 transitive 의존성(OkHttp 등)은 호스트 앱이 직접 추가.

**옵션 B: 사내 Maven (미래)**

Maven Publish plugin 설정은 현재 없음. 필요 시 추가 문서화.

### 5. 변경 로그

`CHANGELOG.md` 에 버전별 변경사항 기록 (옵션):
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

Phase 2+ 에선 core와 adapter 간 버전 호환성을 일대일로 맞춤. 즉 `anecdote-adapter-admob-0.2.0` 은 `anecdote-core-0.2.0` 만 보장.

**Mixing 금지**: `core-0.1.0` + `adapter-admob-0.2.0` 같이 혼용하면 내부 `signal`/`signal source` 인터페이스 변경으로 런타임 에러 가능.

Breaking change가 많아지면 매트릭스 테이블을 별도 유지할 것.

---

## Pre-release (optional)

실험적 기능을 얼리 테스트할 때:
```
0.3.0-alpha.1
0.3.0-beta.1
0.3.0-rc.1
```

사내 앱 중 지원자 대상으로만 배포. 안정화 확인 후 `0.3.0` 정식.

---

## Rollback

문제 발견 시:
1. 이전 버전 AAR 다시 전달 (사내 앱이 바로 교체 가능)
2. 긴급 PATCH 릴리즈 (예: `0.2.1`) 준비
3. git tag 제거 후 재발행은 지양 — 새 PATCH 버전 선호

---

## 체크리스트 (매 릴리즈마다)

- [ ] `anecdoteVersion` bump (올바른 MAJOR/MINOR/PATCH 선택)
- [ ] `./gradlew clean assembleDebug testDebugUnitTest` 통과
- [ ] `./gradlew collectAars` 성공, `build/artifacts/` 에 AAR 생성 확인
- [ ] `CHANGELOG.md` 업데이트 (옵션)
- [ ] git commit + tag
- [ ] 사내 앱에 AAR 전달 및 통합 테스트
