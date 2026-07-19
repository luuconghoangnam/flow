---
name: flowspeed-platform-runtime
description: Change Flow Download Manager native Android or Desktop runtime behavior. Use for Android services, permissions, boot, WebView, Desktop tray, notifications, single-instance startup, auto-start, native lifecycle, OS APIs, or expect/actual implementations.
---

# FlowSpeed Platform Runtime

## Scope

| Concern | Representative location |
|---|---|
| Android lifecycle/services/permissions | `android/app/src/main/` |
| Desktop startup/tray/notifications | `desktop/app/src/main/` |
| Shared platform abstractions | `shared/`, `desktop/shared/`, `desktop/app-utils/` |
| Auto-start/local server/updater | `shared/auto-start/`, `shared/local-server/`, `shared/updater/` |

Use `flowspeed-compose-ui` for rendering, `flowspeed-download-engine` for transfer semantics, and `flowspeed-integration-security` for browser request trust boundaries.

Before changing any symbol, follow GitNexus impact rules in `CLAUDE.md`. Warn before HIGH or CRITICAL lifecycle edits.

## Boundaries

- Platform APIs stay outside `commonMain`; expose narrow interfaces or `expect/actual` contracts.
- Business rules remain shared when platform-independent.
- Repeated start, stop, close, registration, and process recreation remain safe.
- Native notification/tray failure never corrupts download state or crashes background work.
- Permission denial and unavailable OS capability have explicit fallback.
- Avoid starting Compose/Skia solely for tray/background handling.

## Android Invariants

- Service and notification lifecycles satisfy OS requirements.
- Process death, task removal, boot, cancellation, and restart do not duplicate work.
- Permission and battery-optimization denial remain recoverable and visible.
- WebView/download interception validates untrusted input through integration rules.
- Manifest exports and intent filters use least privilege.

## Desktop Invariants

- Single-instance handoff handles startup races, duplicate requests, stale ports, and unavailable primary instance.
- Tray actions work while Compose UI is hidden; restore does not create duplicate app state.
- Native notifications are best effort and secret-safe.
- Auto-start and OS integration remain reversible and platform-scoped.
- Shutdown closes servers, scopes, tray resources, and file handles once.

## Workflow

1. Trace lifecycle from OS entry point through shared operation and cleanup.
2. Run context and upstream impact for entry point and lifecycle owner.
3. Preserve shared contract; isolate OS behavior behind current boundary.
4. Test unavailable capability, denial, repeated event, restart, and cleanup.
5. Compile/lint target and manually exercise native flow on affected OS.

## Verify

```powershell
.\gradlew.bat :android:app:compileDebugKotlin :android:app:lintDebug
.\gradlew.bat :desktop:app:compileKotlin
```

Done means lifecycle operations are idempotent, fallback exists, shared behavior stays platform-neutral, target checks pass, and native flow is exercised where environment permits.
