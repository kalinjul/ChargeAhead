#!/usr/bin/env bash
#
# Records or validates the screenshot goldens in the environment CI uses.
#
# Layoutlib's anti-aliasing is not the same on macOS and on Linux: a white
# wordmark on a dark drawer differs in ~1 % of its pixels between the two,
# ten times the tolerance. A golden recorded on a Mac fails on CI and one
# recorded on CI fails on the Mac, so there is exactly one place where
# goldens get rendered: a Linux x64 container with the JDK CI runs.
#
#   tools/screenshots.sh validate   # what CI does, also what testAll runs
#   tools/screenshots.sh update     # re-record, then look at the PNGs
#
# Extra arguments go to gradle (`--rerun-tasks`, `--info`).
#
# The host's Android SDK is mounted read-only; AGP fetches its own
# build-tools from Maven anyway. The container gets its own Gradle home and
# its own project `.gradle/` so it never fights the Mac daemon over locks.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="eclipse-temurin:25"        # CI: setup-java temurin 25
PLATFORM="linux/amd64"            # CI: ubuntu-latest, x64
LINUX_HOME="${CHARGEAHEAD_LINUX_HOME:-$HOME/.chargeahead-linux}"

case "${1:-validate}" in
  validate) TASK=":ui-tests:validateDebugScreenshotTest" ;;
  update) TASK=":ui-tests:updateDebugScreenshotTest" ;;
  *) echo "usage: $0 [validate|update] [gradle args]" >&2; exit 2 ;;
esac
shift || true

command -v docker >/dev/null || { echo "docker is not installed; screenshot tests only render faithfully in the Linux container" >&2; exit 127; }
docker info >/dev/null 2>&1 || { echo "docker is installed but not running" >&2; exit 1; }

sdk_dir() {
  for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    [ -n "$candidate" ] && [ -d "$candidate/platforms" ] && { echo "$candidate"; return; }
  done
  if [ -f "$REPO_ROOT/local.properties" ]; then
    sed -n 's/^sdk\.dir=//p' "$REPO_ROOT/local.properties" | sed 's/\\:/:/g'
  fi
}
SDK="$(sdk_dir)"
[ -n "$SDK" ] && [ -d "$SDK/platforms" ] || { echo "no Android SDK found (ANDROID_HOME, ~/Library/Android/sdk, local.properties)" >&2; exit 1; }

mkdir -p "$LINUX_HOME/gradle" "$LINUX_HOME/project-dot-gradle"
# Credentials for the api-model artifact come from the developer's
# gradle.properties; only those lines travel into the container.
{
  grep '^chargeahead\.maven\.' "$HOME/.gradle/gradle.properties" 2>/dev/null || true
  echo "org.gradle.daemon=false"
} > "$LINUX_HOME/gradle/gradle.properties"
# local.properties carries a Mac sdk.dir and the API keys; the container
# needs neither, just an sdk.dir that exists inside it.
echo "sdk.dir=/sdk" > "$LINUX_HOME/local.properties"

exec docker run --rm --platform "$PLATFORM" \
  --user "$(id -u):$(id -g)" \
  -e HOME=/gradle-home -e GRADLE_USER_HOME=/gradle-home -e ANDROID_HOME=/sdk \
  -v "$REPO_ROOT":/work \
  -v "$LINUX_HOME/project-dot-gradle":/work/.gradle \
  -v "$LINUX_HOME/local.properties":/work/local.properties:ro \
  -v "$LINUX_HOME/gradle":/gradle-home \
  -v "$SDK":/sdk:ro \
  -w /work "$IMAGE" \
  ./gradlew --console=plain --no-scan --no-daemon "$TASK" "$@"
