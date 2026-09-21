#!/usr/bin/env bash
set -euo pipefail

# Run from the checkout root. Only device-test selection is incremental; JVM tests are not.
base="${BASE_REF:-}"
head="${HEAD_REF:-HEAD}"
run_build=false
run_instrumentation=false
classes=(com.ytone.longcare.smoke.MainActivitySmokeTest)
add_class() {
  local candidate="$1" existing
  for existing in "${classes[@]}"; do
    [[ "$existing" == "$candidate" ]] && return 0
  done
  classes+=("$candidate")
}
all_smoke() {
  run_build=true
  run_instrumentation=true
  add_class com.ytone.longcare.navigation.Navigation3StateTest
  add_class com.ytone.longcare.platform.webview.NativeWebViewCloseBridgeTest
  add_class com.ytone.longcare.features.sales.SalesEvaluationMockFlowTest
  add_class com.ytone.longcare.features.service.ServiceTimeNotificationIntegrationTest
}

if [[ "${GITHUB_EVENT_NAME:-}" == workflow_dispatch ]]; then
  all_smoke
else
  if [[ -z "$base" && -n "${GITHUB_BASE_REF:-}" ]]; then base="origin/${GITHUB_BASE_REF}"; fi
  if [[ -z "$base" ]] || ! git rev-parse --verify "${base}^{commit}" >/dev/null 2>&1; then
    all_smoke
  elif ! changed="$(git diff --name-only "$base...$head")"; then
    all_smoke
  else
    while IFS= read -r file; do
      [[ -z "$file" ]] && continue
      case "$file" in
        *.md|docs/*|openspec/*|.agents/*) continue ;;
      esac
      run_build=true
      case "$file" in
        .github/workflows/android-ci.yml|.github/actions/*|.github/scripts/*|scripts/quality/select_ci_smoke.sh|scripts/quality/enable_kvm.sh|scripts/quality/run_ci_checks.sh|scripts/quality/run_jvm_tests.sh|*build.gradle.kts|settings.gradle.kts|constants.gradle.kts|gradle.properties|gradle/*|build-logic/*|app/src/main/AndroidManifest.xml|baselineprofile/*)
          all_smoke ;;
        app/src/main/*/com/ytone/longcare/navigation/*|app/src/androidTest/*/com/ytone/longcare/navigation/*)
          run_instrumentation=true
          add_class com.ytone.longcare.navigation.Navigation3StateTest ;;
        app/src/main/*/com/ytone/longcare/*webview/*|app/src/androidTest/*/com/ytone/longcare/platform/webview/*)
          run_instrumentation=true
          add_class com.ytone.longcare.platform.webview.NativeWebViewCloseBridgeTest ;;
        app/src/main/*/com/ytone/longcare/features/service/*|app/src/main/*/com/ytone/longcare/features/servicecountdown/*|feature/servicecountdown/*|feature/location/*|app/src/androidTest/*/com/ytone/longcare/features/service/*)
          run_instrumentation=true
          add_class com.ytone.longcare.features.service.ServiceTimeNotificationIntegrationTest ;;
        app/src/main/*/com/ytone/longcare/features/sales/*Screen*.kt|app/src/androidTest/*/com/ytone/longcare/features/sales/*)
          run_instrumentation=true
          add_class com.ytone.longcare.features.sales.SalesEvaluationMockFlowTest ;;
        app/src/main/*/com/ytone/longcare/platform/*|app/src/main/*/com/ytone/longcare/MainActivity.kt|app/src/main/*/com/ytone/longcare/app/*|app/src/main/*/com/ytone/longcare/features/login/*|app/src/main/res/*|app/src/androidTest/*|feature/carddiagnostics/*|feature/login/*)
          run_instrumentation=true ;;
      esac
    done <<< "$changed"
  fi
fi
echo "run_build=$run_build"
echo "run_instrumentation=$run_instrumentation"
echo "smoke_test_classes=$(IFS=,; echo "${classes[*]}")"
