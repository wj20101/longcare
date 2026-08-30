#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/quality/verify_android_build_baseline.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-build-baseline.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

write_fixture() {
  local root="$1"
  local scenario="$2"
  mkdir -p \
    "${root}/gradle" \
    "${root}/app" \
    "${root}/build-logic/convention/src/main/kotlin" \
    "${root}/feature/location" \
    "${root}/feature/photoupload" \
    "${root}/feature/servicecountdown" \
    "${root}/feature/identification"

  local compile_sdk=37
  local target_sdk=36
  local min_sdk=24
  local settings_plugin_version=9.3.2
  local agp_version=9.3.2
  local apply_line='    id("com.android.settings")'
  local constants_extra=""
  local app_override=""
  local jdk_usage='JavaVersion.toVersion(appJdkVersion)'
  local location_extra=""
  local location_viewmodel='    implementation(libs.androidx.lifecycle.viewmodel)'
  local photo_upload_extra=""
  local service_countdown_extra=""
  local identification_extra=""

  case "${scenario}" in
    success) ;;
    missing-target) target_sdk="" ;;
    invalid-order) min_sdk=37 ;;
    plugin-mismatch) settings_plugin_version=9.3.1 ;;
    plugin-not-applied) apply_line="" ;;
    module-override) app_override='    compileSdk = 37' ;;
    legacy-sdk-extra) constants_extra='extra.set("appTargetSdkVersion", 36)' ;;
    jdk-drift) jdk_usage='JavaVersion.toVersion(17)' ;;
    location-compose) location_extra='alias(libs.plugins.kotlin.compose)' ;;
    location-missing-viewmodel) location_viewmodel='' ;;
    photo-upload-coil) photo_upload_extra='    implementation(libs.bundles.coil)' ;;
    service-countdown-android) service_countdown_extra='    implementation(libs.kotlinx.coroutines.android)' ;;
    identification-bugly) identification_extra='    implementation(libs.crashreport)' ;;
    *) echo "unknown fixture scenario: ${scenario}" >&2; exit 2 ;;
  esac

  cat > "${root}/settings.gradle.kts" <<EOF
pluginManagement {
    plugins {
        id("com.android.settings") version "${settings_plugin_version}" apply false
    }
}
plugins {
${apply_line}
}
android {
    compileSdk {
        version = release(${compile_sdk})
    }
    minSdk {
        version = release(${min_sdk})
    }
    targetSdk {
        ${target_sdk:+version = release(${target_sdk})}
    }
}
EOF

  cat > "${root}/constants.gradle.kts" <<EOF
extra.set("appJdkVersion", 21)
extra.set("appVersionCode", 58)
extra.set("appVersionName", "1.0.6")
${constants_extra}
EOF

  cat > "${root}/gradle/libs.versions.toml" <<EOF
[versions]
agp = "${agp_version}"
EOF

  cat > "${root}/app/build.gradle.kts" <<EOF
plugins {}
android {
${app_override}
}
val appJdkVersion = 21
val configured = ${jdk_usage}
EOF

  cat > "${root}/build-logic/convention/src/main/kotlin/Convention.kt" <<'EOF'
class Convention
EOF

  cat > "${root}/feature/location/build.gradle.kts" <<EOF
plugins {}
${location_extra}
dependencies {
    implementation(libs.androidx.core.ktx)
${location_viewmodel}
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.amap.location)
}
EOF

  cat > "${root}/feature/photoupload/build.gradle.kts" <<EOF
plugins {}
dependencies {
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
${photo_upload_extra}
}
EOF

  cat > "${root}/feature/servicecountdown/build.gradle.kts" <<EOF
plugins {}
dependencies {
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
${service_countdown_extra}
}
EOF

  cat > "${root}/feature/identification/build.gradle.kts" <<EOF
plugins {}
dependencies {
    implementation(libs.kotlinx.coroutines.core)
${identification_extra}
}
EOF
}

expect_success() {
  local scenario="$1"
  local root="${FIXTURE_ROOT}/${scenario}"
  write_fixture "${root}" "${scenario}"
  if ! bash "${VERIFY_SCRIPT}" --project-root "${root}" >"${root}.log" 2>&1; then
    cat "${root}.log" >&2
    echo "[android-build-baseline-test][FAIL] expected success: ${scenario}" >&2
    exit 1
  fi
}

expect_failure() {
  local scenario="$1"
  local expected="$2"
  local root="${FIXTURE_ROOT}/${scenario}"
  write_fixture "${root}" "${scenario}"
  if bash "${VERIFY_SCRIPT}" --project-root "${root}" >"${root}.log" 2>&1; then
    echo "[android-build-baseline-test][FAIL] expected failure: ${scenario}" >&2
    exit 1
  fi
  if ! grep -Fq "${expected}" "${root}.log"; then
    cat "${root}.log" >&2
    echo "[android-build-baseline-test][FAIL] missing diagnostic '${expected}' for ${scenario}" >&2
    exit 1
  fi
}

expect_success success
expect_failure missing-target "target-sdk"
expect_failure invalid-order "invalid SDK order"
expect_failure plugin-mismatch "plugin version mismatch"
expect_failure plugin-not-applied "is not applied"
expect_failure module-override "module-level SDK override"
expect_failure legacy-sdk-extra "legacy SDK extra"
expect_failure jdk-drift "numeric JDK override"
expect_failure location-compose "location build capability/dependency"
expect_failure location-missing-viewmodel "location ViewModel dependency"
expect_failure photo-upload-coil "photo-upload dependency"
expect_failure service-countdown-android "service-countdown Coroutines Android dependency"
expect_failure identification-bugly "identification Bugly dependency"

echo "[android-build-baseline-test][PASS] all build baseline fixtures passed."
