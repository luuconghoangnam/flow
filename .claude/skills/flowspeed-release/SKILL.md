---
name: flowspeed-release
description: Package and release Flow Download Manager across Desktop, Android, and browser extension. Use for versions, tags, installers, artifacts, signing, publish workflow, changelog, store packages, or landing download links.
---

# FlowSpeed Release

## Sources Of Truth

| Concern | Location |
|---|---|
| Version resolution | `build.gradle.kts` |
| Release workflow | `.github/workflows/publish.yml` |
| Desktop packaging | `desktop/app/build.gradle.kts` |
| Android signing | `android/app/build.gradle.kts` |
| Extension package | `extension/` |
| Release notes | `CHANGELOG.md` |
| Download links | `landing/` |

Inspect current files before release. Do not duplicate changing artifact names or workflow matrices in this skill.

## Invariants

- Version comes from intended tag/branch logic; never edit generated output to force release.
- Artifact names, formats, architectures, and landing URLs match produced files exactly.
- Existing settings, persisted downloads, and integration protocol remain compatible unless migration exists.
- Release notes describe user-visible behavior and breaking changes.
- Release execution never prints or commits signing material.

Android signing uses environment configured by current Gradle script, including `FLOW_KEYSTORE_*` variables. Never print values, place secrets in `local.properties`, or commit them. Report missing prerequisites rather than claiming signed output.

Publishing, tag pushes, GitHub releases, and store uploads are outward-facing. Confirm immediately before execution unless user explicitly authorized exact release action.

## Workflow

1. Read current version logic, publish workflow, package config, and changelog.
2. Confirm target version, channels, platforms, architectures, and signing availability.
3. Run CI-parity compile, tests, lint, and package tasks through `flowspeed-gradle`.
4. Inspect produced artifacts: names, formats, size, architecture, and installer behavior.
5. Verify changelog and landing/store links against actual artifacts.
6. Publish only after explicit confirmation, then verify release assets and links.

Common aggregate package task is `.\gradlew.bat createReleaseFolderForCi`; inspect current Gradle config and `scripts/` before selecting platform helper.

Use `flowspeed-integration-security` for extension protocol/permission changes.

Done means checks pass, artifacts install on tested targets, release metadata agrees with produced files, missing platform/signing verification is reported, and publication had confirmation.
