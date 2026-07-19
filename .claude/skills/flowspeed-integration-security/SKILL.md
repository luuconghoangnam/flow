---
name: flowspeed-integration-security
description: Change or review Flow browser extension and localhost integration securely. Use for extension scripts or permissions, integration endpoints, CORS/origin policy, request parsing, URL ingestion, filenames, credentials, or REST contract changes.
---

# FlowSpeed Integration Security

## Scope

| Surface | Location |
|---|---|
| Chromium extension | `extension/` |
| Local integration server | `integration/server/` |
| Desktop request handling | `desktop/app/` |
| API contract | `REST-API.yml` |

Treat browser and page data as untrusted: URL, headers, cookies, referrer, filename, category, and metadata. Follow GitNexus impact rules before changing symbols.

## Invariants

- Bind loopback only by default.
- Accept documented methods, paths, content types, fields, and bounded body sizes.
- Reject malformed input and unsupported schemes; default network schemes are `http` and `https`.
- Keep CORS allowlist narrow. `Origin` is policy input, not authentication.
- Bound request queue and duplicate submission behavior.
- Never return or log stack traces, local paths, credentials, cookies, authorization headers, or full signed URLs.
- Do not forward arbitrary credentials or headers across hosts.
- Keep `REST-API.yml`, server parser, Desktop handler, and extension payload aligned.
- Manifest permissions and `host_permissions` use least privilege.
- Never render hostile DOM data through unsafe `innerHTML`.
- Service worker correctness does not rely on process globals; persist required state.
- Ship no remotely hosted executable code.
- Treat page/server filenames as hostile; prevent traversal, absolute paths, reserved names, and destination escape.

## Workflow

1. Map data from browser source through API parser to download creation.
2. Run impact analysis for changed handlers and protocol models.
3. Validate once at trust boundary; avoid inconsistent downstream validation.
4. Update contract and both protocol endpoints together.
5. Test valid flow plus malformed, hostile, unavailable-app, restart, and duplicate cases.

## Verify

Valid request reaches intended action. Invalid JSON, oversized body, unsupported method/path, disallowed origin, malicious scheme, embedded credentials, and traversal name fail safely. Test service-worker restart and loaded-unpacked Chromium when extension changes. Run integration tests and affected Desktop compile.

Use `flowspeed-release` for packaging/store assets and `flowspeed-download-engine` after trusted request becomes download state.
