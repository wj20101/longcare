#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/quality/verify_tech_stack_baseline.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-tech-stack.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

write_fixture() {
  local root="$1"
  local stale="$2"
  mkdir -p "${root}/gradle/wrapper" "${root}/gradle" "${root}/docs/architecture"
  cat > "${root}/settings.gradle.kts" <<'EOF'
android {
    compileSdk { version = release(37) }
    minSdk { version = release(24) }
    targetSdk { version = release(36) }
}
EOF
  cat > "${root}/constants.gradle.kts" <<'EOF'
extra.set("appJdkVersion", 21)
extra.set("appVersionCode", 58)
extra.set("appVersionName", "1.0.6")
EOF
  cat > "${root}/gradle/libs.versions.toml" <<'EOF'
[versions]
agp = "9.3.2"
kotlin = "2.4.10"
ksp = "2.3.11"
composeBom = "2026.08.00"
androidxNavigation = "2.10.0"
androidxCamera = "1.6.2"
coil = "3.6.0"
kotlinxDatetime = "0.8.0"
androidxBaselineProfile = "1.5.0-rc02"
androidxBenchmark = "1.5.0-rc02"
EOF
  cat > "${root}/gradle/wrapper/gradle-wrapper.properties" <<'EOF'
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
EOF
  local app_version='1.0.6 (58)'
  [[ "${stale}" == "true" ]] && app_version='1.0.6 (57)'
  cat > "${root}/docs/architecture/tech-stack.md" <<EOF
| 版本 | \`${app_version}\` |
| \`compileSdk\` | 37 | \`settings.gradle.kts\` |
| \`targetSdk\` | 36 | \`settings.gradle.kts\` |
| \`minSdk\` | 24 | \`settings.gradle.kts\` |
| JDK / JVM toolchain | 21 |
| Gradle Wrapper | 9.7.1 |
| Android Gradle Plugin | 9.3.2 |
| Kotlin | 2.4.10 |
| KSP | 2.3.11 |
| UI | Jetpack Compose BOM | 2026.08.00 |
| Navigation | Navigation Compose | 2.10.0 |
| Camera | CameraX | 1.6.2 |
| Images | Coil | 3.6.0 |
| Date/time | kotlinx-datetime | 0.8.0 |
| Performance | Baseline Profile / Macrobenchmark | 1.5.0-rc02 / 1.5.0-rc02 |
EOF
}

valid_root="${FIXTURE_ROOT}/valid"
write_fixture "${valid_root}" false
bash "${VERIFY_SCRIPT}" --project-root "${valid_root}" >/dev/null

stale_root="${FIXTURE_ROOT}/stale"
write_fixture "${stale_root}" true
if bash "${VERIFY_SCRIPT}" --project-root "${stale_root}" >"${stale_root}.log" 2>&1; then
  echo "[tech-stack-baseline-test][FAIL] stale documentation unexpectedly passed." >&2
  exit 1
fi
grep -Fq "application version" "${stale_root}.log"

echo "[tech-stack-baseline-test][PASS] all documentation drift fixtures passed."
