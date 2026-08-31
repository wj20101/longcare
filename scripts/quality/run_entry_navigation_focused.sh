#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEVICE_SERIAL=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device)
      DEVICE_SERIAL="${2:-}"
      shift 2
      ;;
    -h|--help)
      echo "Usage: bash scripts/quality/run_entry_navigation_focused.sh [--device <api-36-serial>]"
      exit 0
      ;;
    *)
      echo "[entry-navigation-focused][FAIL] unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

cd "${ROOT_DIR}"
bash scripts/quality/verify_entry_navigation_contracts.sh --project-root "${ROOT_DIR}"
./gradlew --no-daemon :app:testDebugUnitTest \
  --tests '*AppEntryStateTest*' \
  --tests '*PrivacyConsentProcessCoordinatorTest*' \
  --tests '*AuthenticationNavigationCoordinatorTest*' \
  --tests '*StartOrderNavigationContractTest*' \
  --tests '*HomeDestinationContractTest*' \
  --tests '*HomeCareNavigationContractTest*' \
  --tests '*SalesNavigationSnapshotTest*' \
  --tests '*SalesBackReducerTest*'

./gradlew --no-daemon :feature:home:testDebugUnitTest \
  --tests '*HomeExperienceTest*' \
  --tests '*HomeFeatureContractTest*'

APP_INSTRUMENTATION_CLASSES="com.ytone.longcare.navigation.EntryNavigationInstrumentationTest,com.ytone.longcare.navigation.HomeGraphOwnerInstrumentationTest,com.ytone.longcare.features.sales.SalesNavigationStateRestorationTest"
HOME_INSTRUMENTATION_CLASSES="com.ytone.longcare.features.home.ui.HomeExperienceContentTest"

if [[ -n "${DEVICE_SERIAL}" ]]; then
  ADB_BIN="$(command -v adb || true)"
  if [[ -z "${ADB_BIN}" ]]; then
    echo "[entry-navigation-focused][FAIL] adb is required to validate the requested device." >&2
    exit 1
  fi
  DEVICE_API="$("${ADB_BIN}" -s "${DEVICE_SERIAL}" shell getprop ro.build.version.sdk | tr -d '\r')"
  if [[ "${DEVICE_API}" != "36" ]]; then
    echo "[entry-navigation-focused][FAIL] device ${DEVICE_SERIAL} is API ${DEVICE_API}; API 36 is required." >&2
    exit 1
  fi
  env ANDROID_SERIAL="${DEVICE_SERIAL}" ./gradlew --no-daemon :app:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=${APP_INSTRUMENTATION_CLASSES}"
  env ANDROID_SERIAL="${DEVICE_SERIAL}" ./gradlew --no-daemon :feature:home:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=${HOME_INSTRUMENTATION_CLASSES}"
else
  ./gradlew --no-daemon :app:pixel6Api36DebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=${APP_INSTRUMENTATION_CLASSES}"
  ./gradlew --no-daemon :feature:home:pixel6Api36DebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=${HOME_INSTRUMENTATION_CLASSES}"
fi

echo "[entry-navigation-focused][PASS] app/Home JVM and API 36 test-APK contracts passed."
