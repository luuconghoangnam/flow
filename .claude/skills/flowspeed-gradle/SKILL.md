---
name: flowspeed-gradle
description: Build, test, lint, and diagnose Flow Download Manager Gradle or Kotlin Multiplatform work. Use for task selection, dependency setup, CI parity, build failures, convention plugins, or Gradle deprecations.
---

# FlowSpeed Gradle

Use repository wrapper with JBR/JDK 21 from repository root.

```powershell
.\gradlew.bat <task>
```

## Task Selection

Run smallest task proving changed behavior, then broader affected consumers.

| Change | Command |
|---|---|
| Download core | `.\gradlew.bat :downloader:core:desktopTest` |
| Shared utilities | `.\gradlew.bat :shared:utils:desktopTest` |
| Desktop app | `.\gradlew.bat :desktop:app:compileKotlin` |
| Android app | `.\gradlew.bat :android:app:compileDebugKotlin` |
| Android UI/resources | `.\gradlew.bat :android:app:lintDebug` |
| Shared Compose UI | `.\gradlew.bat :shared:app:compileKotlinDesktop :android:app:compileDebugKotlin` |
| Build logic/cross-module | `.\gradlew.bat compileKotlinDesktop compileDebugKotlin --continue` |

Inspect `settings.gradle.kts` and module build files instead of maintaining duplicate module catalog here.

## Rules

- Put platform-independent KMP tests in `src/commonTest`; keep test libraries out of production source sets.
- Before changing `api()` to `implementation()`, inspect every direct consumer, including wildcard and transitive usage.
- Change dependency exposure one item at a time and compile every consumer.
- Keep convention-wide compiler configuration in convention plugins, not copied module scripts.
- Follow GitNexus impact rules before editing Kotlin symbols; use `flowspeed-architecture` for module boundaries.

## Diagnose

1. Read exact failing task and first actionable error.
2. Separate source failure from environment, signing, toolchain, or third-party plugin failure.
3. Fix root cause; do not suppress warnings or disable tasks without evidence.
4. Rerun smallest failing task, then required broader task.
5. Report skipped tasks and unavailable prerequisites exactly.

For Gradle 10 readiness run `.\gradlew.bat <task> --warning-mode all`, then classify warnings as project script, convention plugin, or external plugin.

Read current `.github/workflows/build-check.yml` and `.github/workflows/publish.yml` for CI parity. Do not duplicate changing workflow task lists in this skill.

Done means affected tests pass, target consumers compile, Android lint covers Android UI/resource changes, and new tests run in CI or omission is reported.
