#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GRADLE_PROPERTIES_FILE="${1:-${ROOT_DIR}/gradle.properties}"
DAEMON_JVM_FILE="${2:-${ROOT_DIR}/gradle/gradle-daemon-jvm.properties}"
CONSTANTS_FILE="${3:-${ROOT_DIR}/constants.gradle.kts}"
EXIT_CODE=0

read_property() {
  local file_path="$1"
  local key="$2"
  awk -F= -v wanted_key="${key}" '$1 == wanted_key {print substr($0, index($0, "=") + 1)}' "${file_path}" | tail -n1
}

require_equals() {
  local file_path="$1"
  local key="$2"
  local expected="$3"
  local actual
  actual="$(read_property "${file_path}" "${key}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "[gradle-stability][FAIL] ${key} expected '${expected}' but got '${actual:-<missing>}' (${file_path})"
    EXIT_CODE=1
  else
    echo "[gradle-stability][PASS] ${key}=${actual}"
  fi
}

require_non_empty() {
  local file_path="$1"
  local key="$2"
  local actual
  actual="$(read_property "${file_path}" "${key}")"
  if [[ -z "${actual}" ]]; then
    echo "[gradle-stability][FAIL] ${key} is missing or empty (${file_path})"
    EXIT_CODE=1
  else
    echo "[gradle-stability][PASS] ${key} is configured"
  fi
}

echo "[gradle-stability] checking Gradle stability configuration..."
echo "[gradle-stability] gradle.properties: ${GRADLE_PROPERTIES_FILE}"
echo "[gradle-stability] daemon jvm file: ${DAEMON_JVM_FILE}"

require_equals "${GRADLE_PROPERTIES_FILE}" "org.gradle.configuration-cache" "true"
require_equals "${GRADLE_PROPERTIES_FILE}" "org.gradle.configuration-cache.problems" "warn"
require_equals "${GRADLE_PROPERTIES_FILE}" "org.gradle.caching" "true"
require_equals "${GRADLE_PROPERTIES_FILE}" "org.gradle.parallel" "true"
require_equals "${GRADLE_PROPERTIES_FILE}" "org.gradle.vfs.watch" "true"
require_equals "${GRADLE_PROPERTIES_FILE}" "kotlin.incremental" "true"
require_equals "${GRADLE_PROPERTIES_FILE}" "kotlin.compiler.execution.strategy" "daemon"
require_non_empty "${GRADLE_PROPERTIES_FILE}" "org.gradle.jvmargs"
require_non_empty "${GRADLE_PROPERTIES_FILE}" "kotlin.daemon.jvmargs"
require_non_empty "${DAEMON_JVM_FILE}" "toolchainVendor"
require_non_empty "${DAEMON_JVM_FILE}" "toolchainVersion"

expected_jdk="$(sed -nE 's/.*appJdkVersion by extra\(([0-9]+)\).*/\1/p' "${CONSTANTS_FILE}" | head -n1)"
daemon_jdk="$(read_property "${DAEMON_JVM_FILE}" "toolchainVersion")"

if [[ -z "${expected_jdk}" ]]; then
  echo "[gradle-stability][FAIL] failed to parse appJdkVersion from ${CONSTANTS_FILE}"
  EXIT_CODE=1
elif [[ "${expected_jdk}" != "${daemon_jdk}" ]]; then
  echo "[gradle-stability][FAIL] appJdkVersion (${expected_jdk}) != toolchainVersion (${daemon_jdk})"
  EXIT_CODE=1
else
  echo "[gradle-stability][PASS] appJdkVersion matches daemon toolchainVersion (${expected_jdk})"
fi

if [[ "${EXIT_CODE}" -ne 0 ]]; then
  echo "[gradle-stability] verification failed."
  exit "${EXIT_CODE}"
fi

echo "[gradle-stability] verification passed."
