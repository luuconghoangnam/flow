[CmdletBinding()]
param(
    [switch]$Full
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$gradleWrapper = Join-Path $repoRoot "gradlew.bat"

$tasks = @(
    ":downloader:core:desktopTest"
    ":shared:utils:desktopTest"
)

if ($Full) {
    $tasks += @(
        "compileKotlinDesktop"
        "compileDebugKotlin"
        ":android:app:lintDebug"
    )
}

Push-Location $repoRoot
try {
    & $gradleWrapper @tasks "--no-daemon" "--continue"
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}
