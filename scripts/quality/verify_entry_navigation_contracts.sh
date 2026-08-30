#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      PROJECT_ROOT="${2:-}"
      shift 2
      ;;
    -h|--help)
      echo "Usage: bash scripts/quality/verify_entry_navigation_contracts.sh [--project-root <path>]"
      exit 0
      ;;
    *)
      echo "[entry-navigation-contracts][FAIL] unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ ! -d "${PROJECT_ROOT}" ]]; then
  echo "[entry-navigation-contracts][FAIL] project root missing: ${PROJECT_ROOT}" >&2
  exit 1
fi

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
CATALOG="${PROJECT_ROOT}/gradle/libs.versions.toml"
APP_BUILD="${PROJECT_ROOT}/app/build.gradle.kts"
ENTRY_GRAPHS="${PROJECT_ROOT}/app/src/main/kotlin/com/ytone/longcare/navigation/AppNavGraphsEntry.kt"
APP_NAVIGATION="${PROJECT_ROOT}/app/src/main/kotlin/com/ytone/longcare/navigation/AppNavigation.kt"
ERRORS=()

relative_path() {
  printf '%s' "${1#${PROJECT_ROOT%/}/}"
}

fail() {
  local rule_id="$1"
  local message="$2"
  ERRORS+=("rule=${rule_id} ${message}")
}

require_file() {
  local rule_id="$1"
  local path="$2"
  if [[ ! -f "${path}" ]]; then
    fail "${rule_id}" "missing=$(relative_path "${path}")"
  fi
}

matches() {
  local pattern="$1"
  local path="$2"
  if command -v rg >/dev/null 2>&1; then
    rg -q -- "${pattern}" "${path}"
  else
    grep -Eq -- "${pattern}" "${path}"
  fi
}

require_file "navigation-testing-test-only" "${CATALOG}"
require_file "navigation-testing-test-only" "${APP_BUILD}"
require_file "entry-renderer-internal" "${ENTRY_GRAPHS}"
require_file "entry-renderer-internal" "${APP_NAVIGATION}"

if [[ -f "${CATALOG}" ]] && ! matches \
  '^androidx-navigation-testing[[:space:]]*=[[:space:]]*\{[[:space:]]*module[[:space:]]*=[[:space:]]*"androidx\.navigation:navigation-testing",[[:space:]]*version\.ref[[:space:]]*=[[:space:]]*"androidxNavigation"[[:space:]]*\}' \
  "${CATALOG}"; then
  fail "navigation-testing-test-only" "catalog alias must share androidxNavigation"
fi

if [[ -f "${APP_BUILD}" ]]; then
  if ! matches \
    '^[[:space:]]*androidTestImplementation\(libs\.androidx\.navigation\.testing\)[[:space:]]*$' \
    "${APP_BUILD}"; then
    fail "navigation-testing-test-only" "missing androidTestImplementation alias in app/build.gradle.kts"
  fi
  if matches \
    '^[[:space:]]*(api|implementation|compileOnly|debugImplementation|releaseImplementation|testImplementation)\(libs\.androidx\.navigation\.testing\)' \
    "${APP_BUILD}"; then
    fail "navigation-testing-test-only" "navigation-testing leaked outside androidTestImplementation"
  fi
fi

if [[ -f "${ENTRY_GRAPHS}" ]] && ! matches \
  '^[[:space:]]*internal[[:space:]]+class[[:space:]]+EntryDestinationRenderers([[:space:]({]|$)' \
  "${ENTRY_GRAPHS}"; then
  fail "entry-renderer-internal" "EntryDestinationRenderers must remain internal"
fi

if [[ -f "${APP_NAVIGATION}" ]] && ! matches \
  '^[[:space:]]*internal[[:space:]]+fun[[:space:]]+AppNavHost\(' \
  "${APP_NAVIGATION}"; then
  fail "entry-renderer-internal" "AppNavHost test seam must remain internal"
fi

while IFS='|' read -r relative class_name; do
  [[ -n "${relative}" ]] || continue
  path="${PROJECT_ROOT}/${relative}"
  require_file "entry-navigation-test-contract" "${path}"
  if [[ -f "${path}" ]] && ! matches \
    "(^|[[:space:]])class[[:space:]]+${class_name}([[:space:](:<{]|$)" \
    "${path}"; then
    fail \
      "entry-navigation-test-contract" \
      "$(relative_path "${path}") does not declare ${class_name}"
  fi
done <<'TEST_CONTRACTS'
app/src/test/kotlin/com/ytone/longcare/navigation/AppEntryStateTest.kt|AppEntryStateTest
app/src/test/kotlin/com/ytone/longcare/navigation/AuthenticationNavigationCoordinatorTest.kt|AuthenticationNavigationCoordinatorTest
app/src/test/kotlin/com/ytone/longcare/navigation/StartOrderNavigationContractTest.kt|StartOrderNavigationContractTest
app/src/test/kotlin/com/ytone/longcare/features/home/ui/HomeExperienceTest.kt|HomeExperienceTest
app/src/test/kotlin/com/ytone/longcare/features/sales/SalesNavigationSnapshotTest.kt|SalesNavigationSnapshotTest
app/src/test/kotlin/com/ytone/longcare/features/sales/SalesBackReducerTest.kt|SalesBackReducerTest
app/src/androidTest/kotlin/com/ytone/longcare/navigation/EntryNavigationInstrumentationTest.kt|EntryNavigationInstrumentationTest
app/src/androidTest/kotlin/com/ytone/longcare/navigation/HomeGraphOwnerInstrumentationTest.kt|HomeGraphOwnerInstrumentationTest
app/src/androidTest/kotlin/com/ytone/longcare/features/home/ui/HomeExperienceContentTest.kt|HomeExperienceContentTest
app/src/androidTest/kotlin/com/ytone/longcare/features/sales/SalesNavigationStateRestorationTest.kt|SalesNavigationStateRestorationTest
TEST_CONTRACTS

if [[ ${#ERRORS[@]} -gt 0 ]]; then
  for error in "${ERRORS[@]}"; do
    echo "[entry-navigation-contracts][FAIL] ${error}" >&2
  done
  echo "[entry-navigation-contracts][HINT] keep navigation-testing test-only, internalize the renderer seam, and restore the focused test contract." >&2
  exit 1
fi

echo "[entry-navigation-contracts][PASS] dependency scope, internal renderer, and focused test contracts verified."
