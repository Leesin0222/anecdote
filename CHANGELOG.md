# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
once it reaches `1.0.0`. Until then, breaking changes may land in any
release; they will always be called out under `### Changed` with a
**BREAKING** marker.

## [Unreleased]

### Added
- GitHub Actions CI running unit tests, Android Lint, and library AAR
  assembly on every push and pull request to `main`.
- Dependabot weekly updates for Gradle and GitHub Actions dependencies,
  grouped by `androidx`, `compose`, `kotlinx`, and `google-play`.
- Issue templates (bug report, feature request) and a pull-request template
  to scaffold the contributor flow.
- ktlint Gradle plugin enforces style on `*.kts` files.
- detekt + detekt-formatting enforce style and quality on `*.kt` files
  using the ktlint ruleset plus complexity, naming, and exception rules.
  Local auto-fix is available via `./gradlew detekt -Pdetekt.autoCorrect=true`.
- JaCoCo coverage report task (`./gradlew jacocoTestReport`) wired into
  each library module via AGP's `enableUnitTestCoverage` flag.
- `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` (Contributor Covenant v2.1),
  `SECURITY.md`, and this `CHANGELOG.md`.

### Changed
- Repository-wide code formatting applied via detekt-formatting
  (trailing commas, argument wrapping). No behavior changes.
- `HttpReachabilityChecker`: caught `IOException` variable renamed to
  `ignored` with an intent comment, satisfying the `SwallowedException`
  detekt rule. No behavior change.

## [0.1.1] - 2026-05-01

### Fixed

**Critical**
- `AdBlockDetector`: synchronize all lifecycle state (`builtInSources`,
  `dynamicSources`, `reporterForwardJobs`, `evaluationJob`) under a single
  lock; serialize `reevaluateInternal` with `@Synchronized` to prevent
  racing state transitions.
- `AdDomainProber`: catch any `Throwable` (not just `IOException`) in
  `probeDomain` and wrap the cycle loop with a guard so a single malformed
  URL or unexpected exception can no longer kill the probe loop
  permanently.

**High**
- `AdBlockDetector`: recreate the coroutine scope on each `start()` and
  cancel it on `stop()`, fixing the scope / `Context` leak that
  accumulated with every detector instance.
- `AdDomainProber`: accept a parent scope instead of creating its own;
  lifecycle now follows `AdBlockDetector`, eliminating the `SupervisorJob`
  leak across `start` / `stop` cycles.
- `AdBlockDetector.start()`: clear `builtInSources` and gate state so
  repeated `start()` calls cannot duplicate built-in sources.
- `SignalAggregator`: guard `subscriptionJobs` with a dedicated lock so
  `register` / `unregister` / `stop` are safe from concurrent callers.

**Medium**
- `DetectorGate`: `@Synchronized` on all mutator methods.
- `SignalAggregator.purgeOldEvents`: `removeAll` over the full deque
  instead of front-only pruning, so out-of-order timestamps from multiple
  clocks don't leave stale events behind.
- `CustomSignalSource.emit`: require `signal.networkId == source.networkId`
  to prevent cross-network statistic corruption.
- `PolicyConfig.Builder`: validate thresholds are in `0..100`,
  `thresholdSuspected <= thresholdBlocked`, and
  `maxEvaluationsPerSession > 0`.
- `NetworkEnvironmentSource`: `onBufferOverflow = DROP_OLDEST` so the
  latest env state is never lost to buffer pressure.

**Low**
- `AdBlockDetector`: share a single `OkHttpClient` across `start` / `stop`
  cycles instead of creating a new client (and connection pool / thread
  pool) on every `start`.
- `FirebaseEventMapping.toBundle`: fail fast with `error()` on unsupported
  value types instead of silently stringifying them.

### Added
- 8 new unit tests: `PolicyConfig` validation (6), `AdDomainProber`
  survival on bad input (1), `CustomSignalSource` cross-network mismatch
  (1).

## [0.1.0] - 2026-04-24

### Added
- Initial implementation of a modular Android ad-block detection SDK:
  - **`anecdote-core`** — detection engine, signal aggregator, scoring,
    grace-period and re-evaluation policy, `StateFlow<BlockState>`
    public API.
  - **`anecdote-adapter-admob`** — Google Mobile Ads adapter wrapping
    `AdListener`, `InterstitialAdLoadCallback`,
    `RewardedAdLoadCallback`; classifies errors (`NO_FILL` vs. real
    network failure) and decomposes mediation chains.
  - **`anecdote-reporter-logcat`** — `Log.d`-based reporter for
    development.
  - **`anecdote-reporter-firebase`** — Firebase Analytics reporter that
    emits `adblock_state_changed` and (optionally) `adblock_signal`
    events.
- Active Probe runner (HEAD requests against configurable ad / control
  domains), network-environment watcher (VPN + Private DNS), and a
  weighted scoring model with `Unknown` / `NotBlocked` / `Suspected` /
  `Blocked` verdict states each carrying a `Confidence`.
- `CustomSignalSource` public hook for hosts to feed in non-AdMob
  network failures without writing a full adapter.
- Cold-start grace period and per-session re-evaluation cap to suppress
  false positives during transient outages.
- Sample app exercising the SDK with `LogcatBlockEventReporter` and the
  AdMob adapter.

[Unreleased]: https://github.com/Leesin0222/anecdote/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/Leesin0222/anecdote/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/Leesin0222/anecdote/releases/tag/v0.1.0
