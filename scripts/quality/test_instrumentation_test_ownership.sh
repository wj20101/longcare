#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY_SCRIPT="${ROOT_DIR}/scripts/quality/verify_instrumentation_test_ownership.sh"
RUN_SCRIPT="${ROOT_DIR}/scripts/quality/run_connected_instrumentation_suite.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/longcare-instrumentation-ownership.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

write_build_file() {
  local module_dir="$1"
  mkdir -p "${module_dir}"
  cat > "${module_dir}/build.gradle.kts" <<'EOF'
android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}
dependencies {
    androidTestImplementation(libs.androidx.test.runner)
}
EOF
}

write_test_source() {
  local module_dir="$1"
  local class_name="$2"
  mkdir -p "${module_dir}/src/androidTest/kotlin/fixture"
  cat > "${module_dir}/src/androidTest/kotlin/fixture/${class_name}.kt" <<EOF
package fixture
class ${class_name}
EOF
}

write_owner_list() {
  local root="$1"
  mkdir -p "${root}/scripts/quality"
  cat > "${root}/scripts/quality/instrumentation_test_modules.txt" <<'EOF'
# Connected test APK owners; selectors remain in the target matrix.
:app
:core:data
:feature:identification
:feature:login
EOF
}

write_valid_fixture() {
  local root="$1"
  mkdir -p "${root}"
  cat > "${root}/settings.gradle.kts" <<'EOF'
include(":app")
include(":core:data")
include(":feature:identification")
include(":feature:login")
EOF
  write_owner_list "${root}"
  write_build_file "${root}/app"
  write_build_file "${root}/core/data"
  write_build_file "${root}/feature/identification"
  write_build_file "${root}/feature/login"
  write_test_source "${root}/app" AppInstrumentedTest
  write_test_source "${root}/core/data" DataInstrumentedTest
  write_test_source "${root}/feature/identification" IdentificationInstrumentedTest
  write_test_source "${root}/feature/login" LoginInstrumentedTest
}

expect_failure() {
  local scenario="$1"
  local expected="$2"
  shift 2
  local log_file="${FIXTURE_ROOT}/${scenario}.log"
  if "$@" >"${log_file}" 2>&1; then
    echo "[instrumentation-test-ownership-test][FAIL] ${scenario} unexpectedly passed." >&2
    exit 1
  fi
  if ! grep -Fq "${expected}" "${log_file}"; then
    echo "[instrumentation-test-ownership-test][FAIL] ${scenario} did not report '${expected}'." >&2
    sed 's/^/[fixture-output] /' "${log_file}" >&2
    exit 1
  fi
}

valid_root="${FIXTURE_ROOT}/valid"
write_valid_fixture "${valid_root}"
bash "${VERIFY_SCRIPT}" \
  --project-root "${valid_root}" \
  --aggregate-script "${RUN_SCRIPT}" >/dev/null

fake_gradle="${FIXTURE_ROOT}/fake-gradle"
cat > "${fake_gradle}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$@" > "${FAKE_GRADLE_ARGS_FILE:?}"
printf '%s\n' "${ANDROID_SERIAL:-}" > "${FAKE_GRADLE_SERIAL_FILE:?}"
EOF
chmod +x "${fake_gradle}"

actual_args="${FIXTURE_ROOT}/actual-gradle-args.txt"
actual_serial="${FIXTURE_ROOT}/actual-serial.txt"
ANDROID_SERIAL=fixture-serial \
FAKE_GRADLE_ARGS_FILE="${actual_args}" \
FAKE_GRADLE_SERIAL_FILE="${actual_serial}" \
  bash "${RUN_SCRIPT}" \
    --project-root "${valid_root}" \
    --gradle-command "${fake_gradle}" \
    -- --dry-run >/dev/null

cat > "${FIXTURE_ROOT}/expected-gradle-args.txt" <<'EOF'
--no-daemon
:app:connectedDebugAndroidTest
:core:data:connectedDebugAndroidTest
:feature:identification:connectedDebugAndroidTest
:feature:login:connectedDebugAndroidTest
--dry-run
EOF
diff -u "${FIXTURE_ROOT}/expected-gradle-args.txt" "${actual_args}"
grep -Fxq 'fixture-serial' "${actual_serial}"

FAKE_GRADLE_ARGS_FILE="${actual_args}" \
FAKE_GRADLE_SERIAL_FILE="${actual_serial}" \
  bash "${RUN_SCRIPT}" \
    --project-root "${valid_root}" \
    --gradle-command "${fake_gradle}" >/dev/null
if grep -Fq -- '--dry-run' "${actual_args}"; then
  echo "[instrumentation-test-ownership-test][FAIL] empty Gradle argument seam retained a stale argument." >&2
  exit 1
fi

missing_root="${FIXTURE_ROOT}/missing-owner"
cp -R "${valid_root}" "${missing_root}"
grep -Fv ':core:data' "${missing_root}/scripts/quality/instrumentation_test_modules.txt" > "${missing_root}/owners.tmp"
mv "${missing_root}/owners.tmp" "${missing_root}/scripts/quality/instrumentation_test_modules.txt"
expect_failure missing-owner ':core:data has instrumentation source' \
  bash "${VERIFY_SCRIPT}" --project-root "${missing_root}" --aggregate-script "${RUN_SCRIPT}"

stale_root="${FIXTURE_ROOT}/stale-empty-owner"
cp -R "${valid_root}" "${stale_root}"
cat >> "${stale_root}/settings.gradle.kts" <<'EOF'
include(":feature:home")
EOF
write_build_file "${stale_root}/feature/home"
cat > "${stale_root}/scripts/quality/instrumentation_test_modules.txt" <<'EOF'
:app
:core:data
:feature:home
:feature:identification
:feature:login
EOF
expect_failure stale-empty-owner ':feature:home has no Kotlin/Java source below feature/home/src/androidTest' \
  bash "${VERIFY_SCRIPT}" --project-root "${stale_root}" --aggregate-script "${RUN_SCRIPT}"

missing_runner_root="${FIXTURE_ROOT}/missing-runner"
cp -R "${valid_root}" "${missing_runner_root}"
grep -Fv 'testInstrumentationRunner' "${missing_runner_root}/core/data/build.gradle.kts" > "${missing_runner_root}/build.tmp"
mv "${missing_runner_root}/build.tmp" "${missing_runner_root}/core/data/build.gradle.kts"
expect_failure missing-runner ':core:data test source core/data/src/androidTest' \
  bash "${VERIFY_SCRIPT}" --project-root "${missing_runner_root}" --aggregate-script "${RUN_SCRIPT}"

missing_dependency_root="${FIXTURE_ROOT}/missing-runner-dependency"
cp -R "${valid_root}" "${missing_dependency_root}"
grep -Fv 'androidTestImplementation' "${missing_dependency_root}/feature/login/build.gradle.kts" > "${missing_dependency_root}/build.tmp"
mv "${missing_dependency_root}/build.tmp" "${missing_dependency_root}/feature/login/build.gradle.kts"
expect_failure missing-runner-dependency ':feature:login test source feature/login/src/androidTest' \
  bash "${VERIFY_SCRIPT}" --project-root "${missing_dependency_root}" --aggregate-script "${RUN_SCRIPT}"

duplicate_root="${FIXTURE_ROOT}/duplicate-owner"
cp -R "${valid_root}" "${duplicate_root}"
cat >> "${duplicate_root}/scripts/quality/instrumentation_test_modules.txt" <<'EOF'
:feature:login
EOF
expect_failure duplicate-owner 'duplicate module :feature:login' \
  bash "${VERIFY_SCRIPT}" --project-root "${duplicate_root}" --aggregate-script "${RUN_SCRIPT}"

unknown_root="${FIXTURE_ROOT}/unknown-owner"
cp -R "${valid_root}" "${unknown_root}"
cat > "${unknown_root}/scripts/quality/instrumentation_test_modules.txt" <<'EOF'
:app
:core:data
:feature:ghost
:feature:identification
:feature:login
EOF
expect_failure unknown-owner 'unknown module :feature:ghost' \
  bash "${VERIFY_SCRIPT}" --project-root "${unknown_root}" --aggregate-script "${RUN_SCRIPT}"

unsorted_root="${FIXTURE_ROOT}/unstable-order"
cp -R "${valid_root}" "${unsorted_root}"
cat > "${unsorted_root}/scripts/quality/instrumentation_test_modules.txt" <<'EOF'
:app
:core:data
:feature:login
:feature:identification
EOF
expect_failure unstable-order 'is not in stable lexical order' \
  bash "${VERIFY_SCRIPT}" --project-root "${unsorted_root}" --aggregate-script "${RUN_SCRIPT}"

root_task_script="${FIXTURE_ROOT}/root-connected-task.sh"
cat > "${root_task_script}" <<'EOF'
#!/usr/bin/env bash
OWNERS="instrumentation_test_modules.txt"
./gradlew connectedDebugAndroidTest
EOF
expect_failure root-connected-task 'invokes root connectedDebugAndroidTest' \
  bash "${VERIFY_SCRIPT}" --project-root "${valid_root}" --aggregate-script "${root_task_script}"

empty_root="${FIXTURE_ROOT}/empty-owner-list"
cp -R "${valid_root}" "${empty_root}"
cat > "${empty_root}/scripts/quality/instrumentation_test_modules.txt" <<'EOF'
# No owners is invalid.
EOF
expect_failure empty-owner-list 'contains no modules' \
  bash "${RUN_SCRIPT}" --project-root "${empty_root}" --gradle-command "${fake_gradle}"

echo "[instrumentation-test-ownership-test][PASS] ownership, aggregate, ordering, runner, dependency, unknown, stale, and root-task fixtures passed."
