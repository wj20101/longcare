#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
bash scripts/quality/verify_no_tracked_keystore_files.sh .
bash scripts/quality/verify_gradle_stability.sh
bash scripts/quality/verify_ci_workflow_quality.sh
bash scripts/quality/verify_validation_app_isolation.sh .
bash scripts/lint/verify_lint_ignore_policy.sh app/lint.xml
bash scripts/quality/verify_jetpack_compat_apis.sh
bash scripts/quality/verify_baselineprofile_journeys.sh
bash scripts/quality/verify_cancellation_guards.sh app/src/main/kotlin
bash scripts/quality/verify_no_empty_catch_blocks.sh .
bash scripts/quality/verify_target_sdk_upgrade.sh constants.gradle.kts .github/workflows/android-ci.yml
bash scripts/quality/verify_exact_alarm_permission_config.sh app/src/main/AndroidManifest.xml
bash scripts/quality/verify_architecture_boundaries.sh .
bash scripts/quality/verify_module_dependency_whitelist.sh .
bash scripts/quality/verify_module_api_visibility.sh app/src/main/kotlin/com/ytone/longcare .
