# Contributing to anecdote

Thanks for taking the time to look at the source. This guide covers how to set
up the project, what we expect from contributions, and the local commands the
CI will run on your pull request.

## Table of contents

- [Code of Conduct](#code-of-conduct)
- [Ways to contribute](#ways-to-contribute)
- [Project layout](#project-layout)
- [Development setup](#development-setup)
- [Running the checks the CI runs](#running-the-checks-the-ci-runs)
- [Code style](#code-style)
- [Tests](#tests)
- [Pull request process](#pull-request-process)
- [Reporting bugs & requesting features](#reporting-bugs--requesting-features)
- [Security issues](#security-issues)

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By
participating you agree to uphold it.

## Ways to contribute

- **Bug reports** with a reliable repro and the `BlockState` / signal trail —
  these are the most valuable.
- **Adapters** for new ad SDKs (AppLovin, Unity, ironSource, …). Adapters live
  in their own module (`anecdote-adapter-<network>`) and depend only on
  `anecdote-core`'s `SignalSource` API.
- **Reporters** for new analytics backends. Reporters implement
  `BlockEventReporter` and can be added side-by-side with the existing ones.
- **Detection signals** — additional probes, network-environment indicators,
  or scoring refinements. Anything that touches the scoring math should ship
  with unit tests and a short note in the PR on how false-positive risk was
  considered.
- **Documentation, examples, KDoc.** Small wording PRs are welcome; a clearer
  README helps more users than another knob in the config.

If you want to do something larger (a new module, a public API change), please
open an issue or discussion first so we can align on the shape before code is
written.

## Project layout

```
anecdote-core/                detection engine, signal types, scoring, public API
anecdote-adapter-admob/       Google Mobile Ads adapter (optional)
anecdote-reporter-firebase/   Firebase Analytics reporter (optional)
anecdote-reporter-logcat/     Logcat reporter for dev/debug
sample/                       sample app exercising the SDK
build-logic/                  Gradle convention plugins (anecdote.android.*)
config/detekt/                detekt rule configuration
.github/                      CI, Dependabot, issue & PR templates
```

The Core has zero dependency on any ad network or analytics backend. Adapters
and reporters are opt-in and must never make the Core depend on them.

## Development setup

**Requirements**

- JDK 21 (the project uses Gradle's daemon JVM toolchain — `gradle/gradle-daemon-jvm.properties`)
- Android SDK with API 36 compileSdk and minSdk 24

**First-time setup**

```bash
git clone https://github.com/Leesin0222/anecdote.git
cd anecdote
./gradlew help   # downloads Gradle 9.3.1 + the JDK toolchain
```

Open the project root in Android Studio or IntelliJ IDEA. The `.editorconfig`
at the repo root configures Kotlin code style automatically.

## Running the checks the CI runs

The CI workflow in `.github/workflows/ci.yml` runs these in order. Reproducing
them locally before pushing avoids round-trips:

```bash
./gradlew ktlintCheck          # style check for *.kts (build scripts)
./gradlew detekt               # style + complexity check for *.kt
./gradlew test                 # unit tests across all modules
./gradlew jacocoTestReport     # coverage report (HTML + XML)
./gradlew lint                 # Android Lint
./gradlew :anecdote-core:assembleRelease \
          :anecdote-adapter-admob:assembleRelease \
          :anecdote-reporter-firebase:assembleRelease \
          :anecdote-reporter-logcat:assembleRelease
```

When detekt fails on a formatting rule (trailing commas, wrapping, etc.) you
can let it auto-fix the violations locally:

```bash
./gradlew detekt -Pdetekt.autoCorrect=true
```

This flag is intentionally disabled in CI — auto-fixed diffs must be reviewed
before they land.

## Code style

- **Kotlin official style**, enforced via ktlint (for `.kts`) and
  detekt-formatting (for `.kt`). The `.editorconfig` sets max line length to
  140 and enables trailing commas.
- **No wildcard imports.**
- **Top-level constants** are `SCREAMING_SNAKE_CASE`.
- **`@Composable` functions** are `PascalCase` (already whitelisted in
  detekt config).
- Don't introduce comments that describe *what* the code does — the names
  already say that. Comment on *why* something non-obvious is true: a hidden
  invariant, an intentionally-swallowed exception, a workaround for a known
  bug.

## Tests

- Unit tests for everything under `anecdote-core/src/main/kotlin` belong in
  `anecdote-core/src/test/kotlin` (mirror the package path).
- Use **JUnit 4**, **MockK**, **Turbine** (already wired in the version
  catalog) and **kotlinx-coroutines-test** for any `StateFlow` /
  `runTest`-based assertion.
- Anything that touches scoring math, thresholds, grace periods, or
  network-transition behavior must come with a test that locks the
  behavior down — these are the parts that quietly break user UX when they
  regress.
- Aim to keep `anecdote-core` line coverage roughly steady when you add code.
  Run `./gradlew jacocoTestReport` and open
  `anecdote-core/build/reports/jacoco/jacocoTestReport/html/index.html` to
  check.

## Pull request process

1. **Branch off `main`.** Keep PRs focused — one bug fix or one feature per
   PR is much easier to review than a "kitchen sink" diff.
2. **Update tests** alongside the code, in the same PR.
3. **Run the checks above locally.** If detekt or ktlint fails on style, use
   `./gradlew detekt -Pdetekt.autoCorrect=true` and re-run.
4. **Public API changes** (signatures in `anecdote-*/src/main/kotlin`)
   deserve a callout in the PR description and a note in `CHANGELOG.md`
   under `[Unreleased]`.
5. **Fill in the PR template.** The checkboxes are there to make review fast,
   not to be busywork.
6. **CI must be green** before review. If a check is failing due to flakiness
   unrelated to your change, say so in the PR — don't disable the check.

We try to give every PR at least an initial response within a week. If a PR
sits longer than that without feedback, feel free to ping it on the thread.

## Reporting bugs & requesting features

Use the issue templates in `.github/ISSUE_TEMPLATE/`:

- **Bug report**: include `BlockState` transitions, the relevant section of
  `LogcatBlockEventReporter` output, your SDK version, Android version, and
  whether VPN / Private DNS were active.
- **Feature request**: explain the *use case* first. The scoring model and
  signal API are the load-bearing parts of the SDK, so concrete user
  scenarios are what drives changes.

For questions, integration help, or general feedback, prefer
[Discussions](https://github.com/Leesin0222/anecdote/discussions) over an
issue.

## Security issues

Please **do not** open a public issue for security problems. See
[SECURITY.md](SECURITY.md) for how to report them privately via GitHub
Security Advisories.
