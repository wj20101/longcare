#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."
OUTPUT_DIR="build/quality-snapshot"
LINT_REPORT="app/build/reports/lint-results-debug.txt"
SOURCE_ROOT="app/src/main/kotlin"
WORKFLOW_FILE=".github/workflows/android-ci.yml"
SKIP_LINT_BOOTSTRAP="false"

usage() {
  cat <<'USAGE'
Usage: bash scripts/quality/run_quality_gate.sh [options]

Options:
  --project-root <path>   Project root path (default: .)
  --output-dir <path>     Quality snapshot output directory (default: build/quality-snapshot)
  --lint-report <path>    Lint report path (default: app/build/reports/lint-results-debug.txt)
  --source-root <path>    Kotlin source root for scans (default: app/src/main/kotlin)
  --workflow-file <path>  Workflow file used by target sdk gate (default: .github/workflows/android-ci.yml)
  --skip-lint-bootstrap   Skip lint bootstrap when lint report is missing
  -h, --help              Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      PROJECT_ROOT="${2:-}"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="${2:-}"
      shift 2
      ;;
    --lint-report)
      LINT_REPORT="${2:-}"
      shift 2
      ;;
    --source-root)
      SOURCE_ROOT="${2:-}"
      shift 2
      ;;
    --workflow-file)
      WORKFLOW_FILE="${2:-}"
      shift 2
      ;;
    --skip-lint-bootstrap)
      SKIP_LINT_BOOTSTRAP="true"
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[quality-gate][FAIL] unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

echo "[quality-gate] running unified quality gate entrypoint"
echo "[quality-gate] project-root=${PROJECT_ROOT}, output-dir=${OUTPUT_DIR}"

bash scripts/quality/collect_quality_snapshot.sh \
  --project-root "${PROJECT_ROOT}" \
  --output-dir "${OUTPUT_DIR}" \
  --lint-report "${LINT_REPORT}" \
  --source-root "${SOURCE_ROOT}" \
  --workflow-file "${WORKFLOW_FILE}" \
  $([[ "${SKIP_LINT_BOOTSTRAP}" == "true" ]] && echo "--skip-lint-bootstrap")
