---
name: flowspeed-architecture
description: Refactor Flow Download Manager module and dependency boundaries safely. Use for module moves, api versus implementation, KMP source sets, DI splitting, package moves, composition refactors, architecture tests, or large-class decomposition.
---

# FlowSpeed Architecture

Use for structural changes, not routine local edits. GitNexus rules in `CLAUDE.md` are mandatory: inspect context, run upstream impact before edits, warn on HIGH/CRITICAL risk, and run change detection before commit.

## Sources Of Truth

- Module graph: `settings.gradle.kts` and module `build.gradle.kts` files
- Build conventions: `compositeBuilds/`
- Platform composition roots: `android/app/src/main/.../di/` and `desktop/app/src/main/.../di/`
- Current risk and ROI notes: `IMPROVEMENTS.md`

## Boundaries

- `downloader/core` stays independent from Compose, app UI, and Koin.
- Platform APIs do not leak into `commonMain`.
- UI depends on shared/domain contracts; core does not depend upward on UI.
- Platform composition roots own OS-specific wiring.
- `expect/actual` contracts stay narrow; shared business rules do not move into actual implementations.
- Persistence formats remain backward-compatible or gain explicit migration.

## Refactor Rules

- Refactor one ownership boundary per patch.
- Add characterization tests before extracting behavior with races, lifecycle, persistence, or protocol state.
- Prefer composition/delegation over extension functions when protected inherited state is involved.
- Split by responsibility and ownership, never line count alone.
- Before `api()` to `implementation()`, inspect all consumers, including wildcard and transitive imports; compile each consumer.
- Never batch package/module moves through text replacement; use GitNexus-aware rename/refactor tools.
- Avoid full Clean Architecture, one-use interfaces, and boilerplate without measured benefit.
- Measure before caching storage, changing polling, or restructuring build graph for performance.

## Workflow

1. State current pain, intended boundary, invariant, and measurable payoff.
2. Query execution flows and inspect context for owning symbols.
3. Run upstream impact and report callers, processes, and risk.
4. Add required safety test or measurement.
5. Move one responsibility while preserving API/state behavior.
6. Compile every affected consumer and run owning tests.
7. Run GitNexus change detection before commit and compare affected flows with intended scope.

Use `flowspeed-testing` for safety nets and `flowspeed-gradle` for exact tasks. Done means dependency direction improves, behavior remains protected, consumers compile, affected flows match intended scope, and refactor has clear payoff.
