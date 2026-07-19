# Automated tests

Requires JBR 21 or JDK 21. Scripts use repository Gradle wrapper and keep its exit code.

## Fast unit suite

Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File tools/testing/run-tests.ps1
```

Linux/macOS:

```bash
sh tools/testing/run-tests.sh
```

Runs:

- `:downloader:core:desktopTest`
- `:shared:utils:desktopTest`

## Full verification

Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File tools/testing/run-tests.ps1 -Full
```

Linux/macOS:

```bash
sh tools/testing/run-tests.sh --full
```

Full mode also compiles Desktop and Android sources, then runs Android lint. Android SDK must be available.

## Reports

Gradle writes HTML reports under:

- `downloader/core/build/reports/tests/`
- `shared/utils/build/reports/tests/`
- `android/app/build/reports/`

Tests must remain deterministic: no real internet, user filesystem, wall-clock sleeps, or order-dependent global state.
