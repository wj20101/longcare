#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
# Unqualified task selects every Android module; the two Kotlin/JVM modules use test.
./gradlew --no-daemon testDebugUnitTest :core:model:test :core:domain:test "$@"
