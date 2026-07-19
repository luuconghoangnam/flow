---
name: flowspeed-download-engine
description: Change Flow Download Manager HTTP or HLS behavior safely. Use for download creation, parts, retry, pause/resume, cancellation, validation, persistence, queues, naming, checksums, or finalization.
---

# FlowSpeed Download Engine

## Scope

| Concern | Location |
|---|---|
| HTTP/HLS engine and persistence | `downloader/core/` |
| Active-state monitoring | `downloader/monitor/` |
| Shared network/file utilities | `shared/utils/` |

Browser ingestion belongs to `flowspeed-integration-security`. Native background execution belongs to `flowspeed-platform-runtime`.

Before changing any symbol, follow GitNexus impact rules in `CLAUDE.md`. Engine changes can affect recovery and user files; change one responsibility per patch.

## Invariants

- Write to incomplete destination; publish final file only after validation succeeds.
- Persist recoverable state before exposing transition as restart-safe.
- Ranged responses use `206` and valid `Content-Range`; never append a `200` full body to partial data.
- Resume validates remote length plus ETag or Last-Modified through existing `If-Range` behavior.
- Treat `416` as complete only when verified local and remote lengths agree.
- Unknown `Content-Length` remains valid streaming mode, never zero-byte success.
- Cancellation wins over late worker callbacks; pause/cancel releases jobs and handles.
- Retries, parts, buffers, and concurrent connections remain bounded.
- Queue active count never exceeds configured cap; pending work remains schedulable.
- Duplicate naming follows configured strategy; unrelated files are never silently overwritten.
- Logs mask credentials, authorization, cookies, and signed URL data.

## Workflow

1. Trace owning execution flow and run upstream impact analysis.
2. Write characterization test before refactoring existing behavior.
3. Isolate one policy or responsibility through composition; preserve state transitions.
4. Cover success, protocol mismatch, failure, cancellation, and restart/resume where relevant.
5. Verify engine first, then target consumers.

Use deterministic fake server or MockWebServer. No real internet or sleep-based synchronization. Relevant cases include valid `206`, ranged `200`, changed validators, `416`, truncated body, cancellation, persisted restart, duplicate target, checksum/write failure, retry limit, and queue cap. Use `flowspeed-testing` for test structure.

HLS changes preserve ordering, relative URL resolution, playlist refresh, key handling, discontinuities, cancellation, and partial cleanup. Do not unify HTTP and HLS state behavior without tests for both.

## Verify

```powershell
.\gradlew.bat :downloader:core:desktopTest
.\gradlew.bat :shared:utils:desktopTest  # when shared utilities change
```

Compile affected Desktop and Android consumers through `flowspeed-gradle`. Done means tests prove contract, persisted files remain readable or migrated, final state matches file publication, and affected flows match intended scope.
