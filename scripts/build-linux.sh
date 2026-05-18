#!/usr/bin/env bash
# Flow Download Manager — Linux build script
# Builds the .deb package and .tar.gz archive for distribution.
# Run from the project root directory.
#
# Requirements:
#   - JBR 21 (set JAVA_HOME or install via sdkman: sdk install java 21.0.6-jbr)
#   - dpkg-deb (for .deb packaging, comes with Debian/Ubuntu)
#
# Usage:
#   ./scripts/build-linux.sh
#
# Output:
#   build/ci-release/binaries/Flow_<version>_linux_<arch>.deb
#   build/ci-release/binaries/Flow_<version>_linux_<arch>.tar.gz

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_DIR"

echo "=== Flow Download Manager — Linux Build ==="
echo ""

# Check JAVA_HOME
if [ -z "${JAVA_HOME:-}" ]; then
  echo "Error: JAVA_HOME is not set."
  echo "Install JBR 21: sdk install java 21.0.6-jbr"
  echo "Or set: export JAVA_HOME=/path/to/jbr-21"
  exit 1
fi

echo "JAVA_HOME: $JAVA_HOME"
echo "Java version:"
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
echo ""

# Build the distributable
echo ">>> Building release distributable..."
./gradlew :desktop:app:createReleaseDistributable

# Create .deb package
echo ">>> Creating .deb package..."
./gradlew :desktop:app:packageReleaseDeb || {
  echo "Note: packageReleaseDeb not available. Using createReleaseFolderForCi instead."
  ./gradlew createReleaseFolderForCi
}

echo ""
echo "=== Build complete ==="
echo ""
echo "Output files:"
find build/ci-release -name "*.deb" -o -name "*.tar.gz" -o -name "*.AppImage" 2>/dev/null | while read f; do
  echo "  $f ($(du -h "$f" | cut -f1))"
done

echo ""
echo "Distributable app:"
echo "  desktop/app/build/compose/binaries/main-release/app/Flow/"
echo ""
echo "To install the .deb:"
echo "  sudo dpkg -i build/ci-release/binaries/Flow_*.deb"
echo ""
echo "To run directly:"
echo "  ./desktop/app/build/compose/binaries/main-release/app/Flow/bin/Flow"
