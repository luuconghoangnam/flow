---
name: flowspeed-a11y-ux
description: Review Flow Download Manager user experience and accessibility. Use for interaction design, keyboard or screen-reader behavior, responsive states, error recovery, extension popup, or landing-page UX.
---

# FlowSpeed Accessibility And UX

Apply WCAG 2.2 AA and WCAG2ICT where platform support permits. Prefer measurable behavior over visual taste. Use `flowspeed-compose-ui` when changing Compose code.

## Scope

- Compose screens on Desktop and Android
- Extension popup, overlay, landing, and other user-facing surfaces
- Empty, loading, progress, disabled, failure, confirmation, and recovery states

## Define Behavior First

For each changed flow, state trigger, success result, progress feedback, failure recovery, cancel/undo behavior, empty/disabled states, Desktop keyboard path, and Android touch path.

Confirm destructive actions unless user explicitly selected a no-confirmation setting. Name affected item count and irreversible file effects.

## Invariants

- Interactive icons have useful labels; decorative icons keep `contentDescription = null`.
- Actions work without pointer where platform supports keyboard input.
- Focus order follows task order and returns sensibly after dialogs, menus, deletion, and navigation.
- Color never carries status alone.
- Dynamic status reaches assistive technology without announcing every speed refresh.
- Text remains readable under font scaling; controls do not clip labels.
- Errors identify affected item, cause category, and recovery action. Never expose raw exceptions.
- Motion is nonessential, nonflashing, and respects reduced-motion support.
- Unknown size uses indeterminate progress. Never invent percentage or ETA.
- Completion appears only after final file publication succeeds.

## Visual Guardrails

Preserve square cyber-industrial geometry, orange active state, high contrast, monospace download data, and project theme tokens. Test dark/light themes plus compact/wide layouts. Style never outranks clarity or contrast.

## Verify

1. Exercise empty, success, failure, cancel, retry, and completion paths.
2. Test keyboard-only Desktop flow, focus restoration, selection, and dialogs.
3. Inspect screen-reader labels and progress announcements.
4. Test scaled text, dark/light themes, and narrow/wide layouts.
5. Compile through `flowspeed-gradle`; compile alone does not prove interaction quality.

References: https://www.w3.org/TR/WCAG22/ and https://www.w3.org/WAI/standards-guidelines/wcag/.
