#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/quality/verify_project_r8_rules.py"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-project-r8.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

create_valid_fixture() {
  local root="${FIXTURE_ROOT}/valid"
  mkdir -p "${root}/app" "${root}/scripts/quality"
  cat >"${root}/app/build.gradle.kts" <<'KTS'
android {
    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "txkyc-face-consumer-proguard-rules.pro",
            )
        }
    }
}
KTS
cat >"${root}/app/proguard-rules.pro" <<'RULES'
-keepattributes Signature
-keep,allowoptimization,allowshrinking,allowobfuscation class com.ytone.longcare.model.result.ApiResult
-keep class com.example.vendor.** { *; }
RULES
  cat >"${root}/app/txkyc-face-consumer-proguard-rules.pro" <<'RULES'
# Vendor-owned fixture file; content is intentionally outside project-rule governance.
RULES
  cat >"${root}/scripts/quality/project_r8_package_keep_allowlist.json" <<'JSON'
{
  "version": 1,
  "rules": [
    {
      "rule": "-keep class com.example.vendor.** { *; }",
      "owner": "fixture-owner",
      "reason": "Fixture package is intentionally broad."
    }
  ]
}
JSON
}

add_rule() {
  local root="$1"
  local rule="$2"
  printf '\n%s\n' "${rule}" >>"${root}/app/proguard-rules.pro"
}

remove_default_rules() {
  local root="$1"
  python3 - "${root}/app/build.gradle.kts" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
path.write_text(
    text.replace(
        'getDefaultProguardFile("proguard-android-optimize.txt")',
        'getDefaultProguardFile("proguard-android.txt")',
    ),
    encoding="utf-8",
)
PY
}

remove_txkyc_file() {
  local root="$1"
  rm "${root}/app/txkyc-face-consumer-proguard-rules.pro"
}

remove_api_result_runtime_rule() {
  local root="$1"
  python3 - "${root}/app/proguard-rules.pro" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
required = (
    "-keep,allowoptimization,allowshrinking,allowobfuscation class "
    "com.ytone.longcare.model.result.ApiResult\n"
)
path.write_text(path.read_text(encoding="utf-8").replace(required, ""), encoding="utf-8")
PY
}

add_stale_allowlist_entry() {
  local root="$1"
  python3 - "${root}/scripts/quality/project_r8_package_keep_allowlist.json" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
payload = json.loads(path.read_text(encoding="utf-8"))
payload["rules"].append(
    {
        "rule": "-keep class com.example.stale.** { *; }",
        "owner": "fixture-owner",
        "reason": "Negative stale-entry fixture.",
    }
)
path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
PY
}

expect_failure() {
  local name="$1"
  local expected_file="$2"
  local expected_message="$3"
  local mutation="$4"
  shift 4
  local fixture="${FIXTURE_ROOT}/${name}"
  local log="${FIXTURE_ROOT}/${name}.log"
  cp -R "${FIXTURE_ROOT}/valid" "${fixture}"
  "${mutation}" "${fixture}" "$@"

  if python3 "${VERIFY_SCRIPT}" --project-root "${fixture}" >"${log}" 2>&1; then
    echo "[project-r8-test][FAIL] ${name} unexpectedly passed." >&2
    exit 1
  fi
  if ! grep -Fq "${expected_file}" "${log}" || ! grep -Fq "${expected_message}" "${log}"; then
    echo "[project-r8-test][FAIL] ${name} did not report the expected file and rule diagnostic." >&2
    sed 's/^/[fixture-output] /' "${log}" >&2
    exit 1
  fi
}

create_valid_fixture
python3 "${VERIFY_SCRIPT}" --project-root "${FIXTURE_ROOT}/valid" >/dev/null

expect_failure "dont-shrink" "app/proguard-rules.pro" "forbidden global directive '-dontshrink'" add_rule "-dontshrink"
expect_failure "dont-optimize" "app/proguard-rules.pro" "forbidden global directive '-dontoptimize'" add_rule "-dontoptimize"
expect_failure "dont-obfuscate" "app/proguard-rules.pro" "forbidden global directive '-dontobfuscate'" add_rule "-dontobfuscate"
expect_failure "default-rule" "app/proguard-rules.pro" "removed rule fingerprint 'generic native keep'" add_rule '-keepclasseswithmembernames class * { native <methods>; }'
expect_failure "androidx-rule" "app/proguard-rules.pro" "removed rule fingerprint 'AndroidX @Keep class rule'" add_rule '-keep @androidx.annotation.Keep class * { *; }'
expect_failure "kotlinx-rule" "app/proguard-rules.pro" "removed rule fingerprint 'Kotlinx global class-name rule'" add_rule '-keepnames class * { @kotlinx.serialization.Serializable <methods>; }'
expect_failure "unallowlisted-wide-rule" "app/proguard-rules.pro" "package-wide keep is not allowlisted" add_rule '-keep class com.example.unreviewed.** { *; }'
expect_failure "stale-allowlist" "scripts/quality/project_r8_package_keep_allowlist.json" "stale package-wide keep entry" add_stale_allowlist_entry
expect_failure "missing-default" "app/build.gradle.kts" "Release proguardFiles is missing optimized Android default rules" remove_default_rules
expect_failure "missing-txkyc" "app/txkyc-face-consumer-proguard-rules.pro" "required project R8 governance input is missing" remove_txkyc_file
expect_failure "missing-api-result-runtime-rule" "app/proguard-rules.pro" "Retrofit suspend ApiResult<T> generic signature rule" remove_api_result_runtime_rule

echo "[project-r8-test][PASS] valid fixture and fail-closed R8 governance fixtures passed."
