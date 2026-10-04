#!/bin/sh
# Single documented local verification entry point (FR-006, contracts/verification.md).
# Runs the fixed check set `build testDebugUnitTest lint` with a prerequisite
# precheck and a consolidated pass/fail summary. Accepts no arguments.
# Warnings never affect the exit code (US2).
set -euo pipefail

# Always operate from the repository root, whatever the caller's CWD is.
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT"

die() {
    printf '%s\n' "verify: FAIL (precheck): $1" >&2
    exit 1
}

# --------------------------------------------------------------------------
# 1. Precheck: JAVA_HOME and Android SDK must be usable before Gradle runs.
# --------------------------------------------------------------------------

if [ -z "${JAVA_HOME:-}" ]; then
    die "JAVA_HOME is not set. Install JDK 21+ (Eclipse Temurin 21 LTS recommended) and point JAVA_HOME at it. See README.md > Prerequisites."
fi

if [ ! -x "$JAVA_HOME/bin/java" ]; then
    die "\$JAVA_HOME/bin/java does not exist or is not executable (JAVA_HOME='$JAVA_HOME'). Install JDK 21+ and fix JAVA_HOME. See README.md > Prerequisites."
fi

java_major=$("$JAVA_HOME/bin/java" -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
if [ -z "$java_major" ]; then
    die "Could not determine the version of \$JAVA_HOME/bin/java. Install JDK 21+ and fix JAVA_HOME. See README.md > Prerequisites."
fi
if [ "$java_major" -lt 21 ]; then
    die "JDK 21 or newer is required, but \$JAVA_HOME provides major version $java_major. Install JDK 21+ (Temurin 21 LTS recommended). See README.md > Prerequisites."
fi

sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$sdk_root" ]; then
    die "Neither ANDROID_HOME nor ANDROID_SDK_ROOT is set. Install the Android SDK (cmdline-tools, then: sdkmanager \"platforms;android-37.0\" \"platform-tools\", plus the build-tools AGP requests) and set ANDROID_HOME to the SDK root. See README.md > Prerequisites."
fi
if [ ! -d "$sdk_root/platforms" ] || [ ! -d "$sdk_root/build-tools" ]; then
    die "'$sdk_root' does not look like an Android SDK root (expected platforms/ and build-tools/ beneath it). Reinstall or repoint ANDROID_HOME/ANDROID_SDK_ROOT. See README.md > Prerequisites."
fi

# --------------------------------------------------------------------------
# 2. Fixed check set, in this exact order, fail-fast (contracts/verification.md).
# --------------------------------------------------------------------------
log=$(mktemp "${TMPDIR:-/tmp}/verify-gradle.XXXXXX")
trap 'rm -f "$log"' EXIT

printf '%s\n' "==> ./gradlew build testDebugUnitTest lint"
if ./gradlew build testDebugUnitTest lint 2>&1 | tee "$log"; then
    # ----------------------------------------------------------------------
    # 3. Consolidated output — success.
    # ----------------------------------------------------------------------
    printf '%s\n' "PASS: build, testDebugUnitTest, lint"
    exit 0
fi

# --------------------------------------------------------------------------
# 3. Consolidated output — failure: name the failing Gradle task(s) and the
#    failure detail each check itself reports. First failing check first.
# --------------------------------------------------------------------------
printf '\n%s\n' "FAIL: verification stopped at the first failing check."
grep -E '^> Task .*FAILED' "$log" | sed 's/^/  failed: /' || true

# Failing unit tests: class + method, from the JUnit XML report.
for xml in app/build/test-results/testDebugUnitTest/TEST-*.xml; do
    [ -e "$xml" ] || continue
    awk '
        /<testcase/ {
            cur = ""
            if (match($0, /[ \t]name="[^"]*"/)) {
                cur = substr($0, RSTART + 7, RLENGTH - 8)
            }
            if (match($0, /[ \t]classname="[^"]*"/)) {
                cur = substr($0, RSTART + 12, RLENGTH - 13) "." cur
            }
        }
        /<failure/ {
            printf "  failing test: %s\n", cur
        }
    ' "$xml"
done

# Lint errors: `<file>: <line>: Error: <message> [issue-id]` from the text report(s).
for report in app/build/reports/lint-results*.txt; do
    [ -e "$report" ] || continue
    grep -E ': Error: ' "$report" | sed 's/^/  lint error: /' || true
done

exit 1
