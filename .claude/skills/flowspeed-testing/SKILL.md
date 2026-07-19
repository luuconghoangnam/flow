---
name: flowspeed-testing
description: Add and improve Flow Download Manager automated tests. Use for regression tests, deterministic fakes, coroutine tests, persistence recovery, DI or navigation smoke tests, architecture invariants, flaky tests, or CI test coverage.
---

# FlowSpeed Testing

Test risk and behavior, not line coverage. Existing tests live mainly under `downloader/core/src/commonTest/` and `shared/utils/src/commonTest/`.

Before changing production symbols, follow GitNexus impact rules in `CLAUDE.md`.

## Placement

- Put platform-independent KMP tests in `src/commonTest`.
- Use platform test source sets only for real platform contracts.
- Keep fixtures near owning test module; share only stable, reused helpers.
- Add dependencies to test source sets, never production solely for tests.

## Determinism

- No real internet, user filesystem, wall-clock sleep, or order-dependent global state.
- Prefer fake server/MockWebServer, temporary directories, fake clocks, controlled dispatchers, and explicit completion signals.
- Test externally visible state/result; avoid private call sequence unless sequence is contract.
- Every regression test must fail against broken behavior it protects.
- Clean up scopes, servers, files, and global registrations in failure paths.

## Coverage By Risk

Cover applicable success, boundary values, validation mismatch, retry exhaustion, cancellation, persisted restart, corrupted persistence, duplicate/idempotent events, cleanup, and concurrency transitions.

- DI smoke tests resolve critical bindings with controlled dependencies.
- Navigation smoke tests prove construction, key transitions, and restoration without full rendering where possible.
- Architecture tests enforce only high-value boundaries: core excludes UI/DI, platform APIs exclude `commonMain`, and dependency direction stays intended.
- Avoid subjective style rules and brittle package snapshots.

## Workflow

1. Name behavior and failure mode.
2. Reuse existing fixture or add smallest deterministic helper.
3. Prove test fails for intended defect, then passes with fix.
4. Run owning module test, then affected consumers.
5. Update CI when new test task is not already executed.

## Verify

```powershell
.\gradlew.bat :downloader:core:desktopTest
.\gradlew.bat :shared:utils:desktopTest
```

Use `flowspeed-gradle` for exact tasks and CI parity. Use `flowspeed-download-engine` for protocol cases and `flowspeed-a11y-ux` for accessibility review.
