#!/usr/bin/env bash
set -u -o pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUTPUT_FILE="${1:-$ROOT_DIR/docs/refactor/baseline-metrics.md}"
LOG_DIR="${2:-/tmp/longcare_baseline_logs}"
CLEAN_BEFORE_RUN="${BASELINE_CLEAN_BEFORE_RUN:-false}"

mkdir -p "$(dirname "$OUTPUT_FILE")"
mkdir -p "$LOG_DIR"

now() {
  date "+%Y-%m-%d %H:%M:%S %z"
}

run_task() {
  local task="$1"
  local safe_name
  local log_file
  local start_ts
  local end_ts
  local duration
  local status

  safe_name="$(echo "$task" | tr ':/' '__')"
  log_file="$LOG_DIR/${safe_name}.log"
  start_ts="$(date +%s)"

  if (cd "$ROOT_DIR" && ./gradlew "$task" --no-daemon >"$log_file" 2>&1); then
    status="PASS"
  else
    status="FAIL"
  fi

  end_ts="$(date +%s)"
  duration=$((end_ts - start_ts))

  echo "| \`$task\` | $status | ${duration}s | \`$log_file\` |"
}

module_count="$(cd "$ROOT_DIR" && rg -n "^include\\(" settings.gradle.kts | wc -l | tr -d ' ')"
host_info="$(uname -a)"
java_info="$(java -version 2>&1 | head -n 1)"
gradle_info="$(cd "$ROOT_DIR" && ./gradlew -v --no-daemon 2>/dev/null | awk '/^Gradle / {print $2; exit}')"
baseline_started_at="$(now)"

task_rows=()

if [ "${CLEAN_BEFORE_RUN}" = "true" ]; then
  clean_log_file="$LOG_DIR/clean.log"
  if ! (cd "$ROOT_DIR" && ./gradlew clean --no-daemon >"$clean_log_file" 2>&1); then
    echo "Baseline preparation failed while running clean. See: $clean_log_file"
    exit 1
  fi
fi

task_rows+=("$(run_task ":app:compileDebugKotlin")")
task_rows+=("$(run_task ":app:testDebugUnitTest")")
task_rows+=("$(run_task ":app:assembleDebug")")

apk_path="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
apk_size_human="N/A"
apk_size_bytes="N/A"
dex_file_count="N/A"
method_count="N/A（需要 apkanalyzer 或 dexcount，当前基线先不统计）"

if [ -f "$apk_path" ]; then
  apk_size_human="$(du -h "$apk_path" | awk '{print $1}')"
  apk_size_bytes="$(wc -c < "$apk_path" | tr -d ' ')"
  dex_file_count="$(unzip -l "$apk_path" "classes*.dex" 2>/dev/null | awk '/classes.*\.dex$/ {count++} END {print count+0}')"
fi

{
  echo ""
  echo "## Baseline Run - $baseline_started_at"
  echo ""
  echo "### Environment"
  echo ""
  echo "- Host: \`$host_info\`"
  echo "- Java: \`$java_info\`"
  echo "- Gradle: \`$gradle_info\`"
  echo "- Module Count (settings include): \`$module_count\`"
  echo ""
  echo "### Build Task Metrics"
  echo ""
  echo "| Task | Status | Duration | Log |"
  echo "|---|---|---:|---|"
  printf "%s\n" "${task_rows[@]}"
  echo ""
  echo "### Artifact Metrics"
  echo ""
  echo "| Metric | Value |"
  echo "|---|---|"
  echo "| APK Path | \`$apk_path\` |"
  echo "| APK Size | $apk_size_human ($apk_size_bytes bytes) |"
  echo "| Dex File Count | $dex_file_count |"
  echo "| Method Count | $method_count |"
  echo ""
  echo "### Commands"
  echo ""
  if [ "${CLEAN_BEFORE_RUN}" = "true" ]; then
    echo "- \`BASELINE_CLEAN_BEFORE_RUN=true ./scripts/quality/collect_build_baseline.sh\`"
    echo "- \`./gradlew clean --no-daemon\`"
  fi
  echo "- \`./gradlew :app:compileDebugKotlin --no-daemon\`"
  echo "- \`./gradlew :app:testDebugUnitTest --no-daemon\`"
  echo "- \`./gradlew :app:assembleDebug --no-daemon\`"
} >> "$OUTPUT_FILE"

echo "Baseline collected: $OUTPUT_FILE"
