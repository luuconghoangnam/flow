#!/usr/bin/env sh
set -u

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
full=0

if [ "${1:-}" = "--full" ]; then
    full=1
elif [ "${1:-}" != "" ]; then
    printf '%s\n' "Usage: $0 [--full]" >&2
    exit 2
fi

if [ -f "$repo_root/gradlew" ]; then
    gradle_wrapper="$repo_root/gradlew"
else
    printf '%s\n' "Gradle wrapper not found: $repo_root/gradlew" >&2
    exit 1
fi

tasks=':downloader:core:desktopTest :shared:utils:desktopTest'
if [ "$full" -eq 1 ]; then
    tasks="$tasks compileKotlinDesktop compileDebugKotlin :android:app:lintDebug"
fi

cd "$repo_root" || exit 1
# shellcheck disable=SC2086
exec sh "$gradle_wrapper" $tasks --no-daemon --continue
