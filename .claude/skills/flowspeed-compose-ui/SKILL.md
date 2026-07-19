---
name: flowspeed-compose-ui
description: Change Flow Download Manager Compose UI across Desktop and Android. Use for composables, Decompose components, navigation, state wiring, responsive layouts, selection, dialogs, or shared UI resources.
---

# FlowSpeed Compose UI

Flow uses Kotlin Multiplatform Compose, Decompose, and Koin. Components own state and lifecycle; composables render state and dispatch actions.

Before changing any symbol, follow GitNexus impact rules in `CLAUDE.md`. Use `flowspeed-a11y-ux` for interaction and accessibility review.

## Boundaries

| Concern | Location |
|---|---|
| Shared UI and components | `shared/app/src/commonMain/` |
| Shared resources | `shared/resources/` |
| Desktop UI and wiring | `desktop/app/src/main/` |
| Android UI and wiring | `android/app/src/main/` |

Put code in `commonMain` only when imports and behavior work on both targets. Keep Activity, window, tray, pointer-specific, OS file, and notification APIs in platform code. Use `flowspeed-platform-runtime` for native lifecycle work.

## Invariants

- Component owns state, effects, navigation, and lifecycle-sensitive work.
- Composable stays declarative; it does not create repositories, download jobs, or unmanaged scopes.
- Preserve Decompose child ownership and state restoration.
- Reuse existing theme tokens, shared widgets, and component actions before adding variants.
- Keep platform behavior outside shared UI through narrow interfaces.
- Preserve Desktop keyboard/mouse selection and Android tap/long-press/back behavior where present.
- UI status reflects engine state, not optimistic dispatch.
- Do not move state or split files solely to reduce line count.

Canonical visual design lives in `.kiro/specs/cyber-industrial-layout-redesign/`. Inspect current source and specs; do not store temporary implementation status in this skill.

## Workflow

1. Locate owning component, composable, and platform consumers.
2. Run GitNexus context and upstream impact for changed symbols.
3. Preserve state/event contracts; change one behavior boundary at a time.
4. Apply `flowspeed-a11y-ux` checks.
5. Compile affected targets, then exercise interaction manually.

## Verify

```powershell
# Desktop
.\gradlew.bat :desktop:app:compileKotlin

# Android
.\gradlew.bat :android:app:compileDebugKotlin :android:app:lintDebug

# Shared UI
.\gradlew.bat :shared:app:compileKotlinDesktop :android:app:compileDebugKotlin
```

Done means state/lifecycle ownership stays explicit, affected interactions work, accessibility is reviewed, and relevant target checks pass.
