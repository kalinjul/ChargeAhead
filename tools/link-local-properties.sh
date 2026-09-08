#!/usr/bin/env bash
#
# Gives a git worktree the main checkout's local.properties.
#
# The file is git-ignored (AGENTS.md, "API keys"), so a fresh worktree gets
# at most an `sdk.dir` from the tooling and none of the keys. The build then
# succeeds, but the app runs on demo data and the map stays a placeholder —
# a failure that looks like a bug in the code rather than a missing file.
#
# A symlink instead of a build-side fallback, because `sdk.dir` is read by
# the Android Gradle Plugin itself: only a real file at the worktree root
# covers the SDK path and both keys at once.
#
# Runs as a SessionStart hook and is idempotent; anything unexpected leaves
# the worktree untouched and exits 0, because a hook must never block a
# session.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="$REPO_ROOT/local.properties"

# A key with an empty value is as useless as no file at all, so match one
# that actually carries something.
has_key() {
    [[ -e "$1" ]] && grep -qE '^openChargeMapApiKey=.+' "$1"
}

has_key "$TARGET" && exit 0

command -v git >/dev/null 2>&1 || exit 0

# The first entry of `worktree list` is always the main checkout.
MAIN="$(git -C "$REPO_ROOT" worktree list --porcelain 2>/dev/null | sed -n '1s/^worktree //p')"
[[ -n "$MAIN" && "$MAIN" != "$REPO_ROOT" ]] || exit 0

SOURCE="$MAIN/local.properties"
has_key "$SOURCE" || exit 0

# Never overwrite a file someone maintained by hand. Only `sdk.dir` counts
# as generated — that is what the worktree tooling writes on its own.
if [[ -f "$TARGET" && ! -L "$TARGET" ]]; then
    if grep -qvE '^\s*(#.*)?$|^sdk\.dir=' "$TARGET"; then
        echo "local.properties in the worktree has its own entries — not linked to $SOURCE." >&2
        exit 0
    fi
fi

rm -f "$TARGET" && ln -s "$SOURCE" "$TARGET" \
    && echo "local.properties linked to $SOURCE (keys from the main checkout)."
exit 0
