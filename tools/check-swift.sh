#!/usr/bin/env bash
#
# Swift syntax check for iosApp/.
#
# IMPORTANT: `swiftc -parse` checks syntax ONLY. It finds NO type errors and
# resolves no imports (import CarPlay/UIKit/SwiftUI/Shared is not resolved,
# even if the frameworks were present). A green result means "parses
# cleanly" — not "compiles". A real compile is only possible on a Mac with
# Xcode (see AGENTS.md, section "Swift on this machine").
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IOS_APP_DIR="${REPO_ROOT}/iosApp"

if ! command -v swiftc >/dev/null 2>&1; then
    echo "Error: 'swiftc' was not found on PATH." >&2
    echo "No Swift compiler is installed on this machine (Linux)." >&2
    echo "Install with: sudo apt install swiftlang" >&2
    exit 127
fi

if [[ ! -d "${IOS_APP_DIR}" ]]; then
    echo "Error: directory '${IOS_APP_DIR}' does not exist." >&2
    exit 1
fi

# mapfile instead of `find ... | while read` — otherwise the counting runs in
# a subshell and the error counter is lost after the pipe.
mapfile -d '' -t SWIFT_FILES < <(find "${IOS_APP_DIR}" -type f -name '*.swift' -print0 | sort -z)

if [[ "${#SWIFT_FILES[@]}" -eq 0 ]]; then
    echo "No .swift files found under '${IOS_APP_DIR}'."
    exit 0
fi

echo "Checking ${#SWIFT_FILES[@]} Swift file(s) under '${IOS_APP_DIR}' (syntax only, swiftc -parse) ..."

ERROR_COUNT=0
CHECKED_COUNT=0

for file in "${SWIFT_FILES[@]}"; do
    CHECKED_COUNT=$((CHECKED_COUNT + 1))
    if swiftc -parse "${file}"; then
        echo "  OK    ${file#"${REPO_ROOT}"/}"
    else
        echo "  ERROR ${file#"${REPO_ROOT}"/}" >&2
        ERROR_COUNT=$((ERROR_COUNT + 1))
    fi
done

echo "---"
echo "Files checked: ${CHECKED_COUNT}, errors: ${ERROR_COUNT}"

if [[ "${ERROR_COUNT}" -gt 0 ]]; then
    exit 1
fi

exit 0
