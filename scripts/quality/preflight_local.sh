#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODE="local-fast"
BASE_REF_VALUE=""
CHANGED_ONLY_FALLBACK_ALL="false"
CHANGED_ONLY_FALLBACK_REASON=""
CHANGED_FILES=""

usage() {
  cat <<'USAGE'
Usage: bash scripts/quality/preflight_local.sh [mode]

Modes:
  (default)        local-fast baseline checks
  --local-fast     run baseline checks
  --changed-only   run changed-scope baseline checks
  --full           local-fast + compile/test
  --release        --full + run_quality_gate.sh
  -h, --help       show help
USAGE
}

if [[ $# -gt 1 ]]; then
  echo "[preflight][FAIL] expected zero or one mode argument." >&2
  usage >&2
  exit 1
fi

if [[ $# -eq 1 ]]; then
  case "$1" in
    --local-fast)
      MODE="local-fast"
      ;;
    --changed-only)
      MODE="changed-only"
      ;;
    --full)
      MODE="full"
      ;;
    --release)
      MODE="release"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[preflight][FAIL] unknown mode: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
fi

resolve_base_ref() {
  BASE_REF_VALUE=""
  if [[ -n "${BASE_REF:-}" ]]; then
    if git -C "${ROOT_DIR}" rev-parse --verify "${BASE_REF}" >/dev/null 2>&1; then
      BASE_REF_VALUE="${BASE_REF}"
      return 0
    fi
    CHANGED_ONLY_FALLBACK_REASON="invalid-BASE_REF:${BASE_REF}"
    return 1
  fi

  if [[ -n "${GITHUB_BASE_REF:-}" ]] && git -C "${ROOT_DIR}" rev-parse --verify "origin/${GITHUB_BASE_REF}" >/dev/null 2>&1; then
    BASE_REF_VALUE="origin/${GITHUB_BASE_REF}"
    return 0
  fi

  if git -C "${ROOT_DIR}" rev-parse --verify origin/master >/dev/null 2>&1; then
    BASE_REF_VALUE="origin/master"
    return 0
  fi

  if git -C "${ROOT_DIR}" rev-parse --verify origin/main >/dev/null 2>&1; then
    BASE_REF_VALUE="origin/main"
    return 0
  fi

  CHANGED_ONLY_FALLBACK_REASON="no-strong-base-ref"
  return 1
}

collect_changed_files() {
  CHANGED_ONLY_FALLBACK_ALL="false"
  CHANGED_ONLY_FALLBACK_REASON=""
  CHANGED_FILES=""

  if ! git -C "${ROOT_DIR}" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    CHANGED_ONLY_FALLBACK_ALL="true"
    CHANGED_ONLY_FALLBACK_REASON="not-a-git-work-tree"
    return 0
  fi

  local base_diff=""
  if resolve_base_ref; then
    local base_status=0
    if base_diff="$(git -C "${ROOT_DIR}" diff --name-only --diff-filter=ACMR "${BASE_REF_VALUE}...HEAD" 2>/dev/null)"; then
      base_status=0
    else
      base_status=$?
    fi
    if [[ "${base_status}" -gt 1 ]]; then
      CHANGED_ONLY_FALLBACK_ALL="true"
      CHANGED_ONLY_FALLBACK_REASON="git-diff-failed:${BASE_REF_VALUE}...HEAD"
      return 0
    fi
  else
    CHANGED_ONLY_FALLBACK_ALL="true"
    return 0
  fi

  local staged_diff=""
  if staged_diff="$(git -C "${ROOT_DIR}" diff --cached --name-only --diff-filter=ACMR 2>/dev/null)"; then
    :
  else
    CHANGED_ONLY_FALLBACK_ALL="true"
    CHANGED_ONLY_FALLBACK_REASON="git-diff-cached-failed"
    return 0
  fi

  local working_diff=""
  if working_diff="$(git -C "${ROOT_DIR}" diff --name-only --diff-filter=ACMR 2>/dev/null)"; then
    :
  else
    CHANGED_ONLY_FALLBACK_ALL="true"
    CHANGED_ONLY_FALLBACK_REASON="git-diff-working-tree-failed"
    return 0
  fi

  local untracked_diff=""
  if untracked_diff="$(git -C "${ROOT_DIR}" ls-files --others --exclude-standard 2>/dev/null)"; then
    :
  else
    CHANGED_ONLY_FALLBACK_ALL="true"
    CHANGED_ONLY_FALLBACK_REASON="git-ls-files-failed"
    return 0
  fi

  CHANGED_FILES="$(
    {
      printf "%s\n" "${base_diff}"
      printf "%s\n" "${staged_diff}"
      printf "%s\n" "${working_diff}"
      printf "%s\n" "${untracked_diff}"
    } | awk 'NF' | sort -u
  )"
}

has_changed_paths() {
  local regex="$1"
  if [[ -z "${CHANGED_FILES}" ]]; then
    return 1
  fi

  if command -v rg >/dev/null 2>&1; then
    if printf '%s\n' "${CHANGED_FILES}" | rg -q "${regex}"; then
      return 0
    fi
    local rg_status=$?
    if [[ "${rg_status}" -eq 1 ]]; then
      return 1
    fi
    echo "[preflight][FAIL] changed-path matcher failed via rg (exit ${rg_status}) for regex: ${regex}" >&2
    return 2
  fi

  if printf '%s\n' "${CHANGED_FILES}" | grep -Eq "${regex}"; then
    return 0
  fi
  local grep_status=$?
  if [[ "${grep_status}" -eq 1 ]]; then
    return 1
  fi
  echo "[preflight][FAIL] changed-path matcher failed via grep (exit ${grep_status}) for regex: ${regex}" >&2
  return 2
}

FAILURES=()

run_step() {
  local label="$1"
  shift

  echo "[preflight][RUN] ${label}"
  if "$@"; then
    echo "[preflight][PASS] ${label}"
  else
    echo "[preflight][FAIL] ${label}"
    FAILURES+=("${label}")
  fi
  echo
}

run_local_fast() {
  local new_files_guard_mode=""
  if [[ "${MODE}" == "changed-only" && "${CHANGED_ONLY_FALLBACK_ALL}" != "true" ]]; then
    new_files_guard_mode="--changed-only"
  fi

  run_step \
    "new-files-guard" \
    bash scripts/quality/check_new_files_guard.sh --project-root "${ROOT_DIR}" ${new_files_guard_mode}

  if [[ "${MODE}" == "changed-only" ]]; then
    if [[ "${CHANGED_ONLY_FALLBACK_ALL}" == "true" ]]; then
      echo "[preflight][WARN] changed-only base is not reliable (${CHANGED_ONLY_FALLBACK_REASON}); running full local-fast baseline to avoid false green."
      echo

      run_step \
        "architecture-boundaries" \
        bash scripts/quality/verify_architecture_boundaries.sh "${ROOT_DIR}"

      run_step \
        "module-dependency-whitelist" \
        bash scripts/quality/verify_module_dependency_whitelist.sh "${ROOT_DIR}"

      run_step \
        "module-api-visibility" \
        bash scripts/quality/verify_module_api_visibility.sh app/src/main/kotlin/com/ytone/longcare "${ROOT_DIR}"
      return 0
    fi

    if has_changed_paths '^(app/src/(main|debug|test)/kotlin/com/ytone/longcare/|core/|feature/|scripts/quality/(verify_architecture_boundaries\.sh|legacy_feature_files_allowlist\.txt|architecture_legacy_imports_allowlist\.txt|architecture_legacy_import_budget\.txt))'; then
      run_step \
        "architecture-boundaries" \
        bash scripts/quality/verify_architecture_boundaries.sh "${ROOT_DIR}"
    else
      local detect_status=$?
      if [[ "${detect_status}" -eq 1 ]]; then
        echo "[preflight][SKIP] architecture-boundaries (no relevant changed paths)"
        echo
      else
        echo "[preflight][FAIL] changed-path detection failed for architecture-boundaries"
        FAILURES+=("changed-path-detection:architecture-boundaries")
        echo
      fi
    fi

    if has_changed_paths '(^|/)build\.gradle\.kts$|^settings\.gradle\.kts$|^constants\.gradle\.kts$|^gradle\.properties$|^app/|^baselineprofile/|^core/|^feature/|^scripts/quality/(module_dependency_allowlist\.txt|verify_module_dependency_whitelist\.sh)$'; then
      run_step \
        "module-dependency-whitelist" \
        bash scripts/quality/verify_module_dependency_whitelist.sh "${ROOT_DIR}"
    else
      local detect_status=$?
      if [[ "${detect_status}" -eq 1 ]]; then
        echo "[preflight][SKIP] module-dependency-whitelist (no relevant changed paths)"
        echo
      else
        echo "[preflight][FAIL] changed-path detection failed for module-dependency-whitelist"
        FAILURES+=("changed-path-detection:module-dependency-whitelist")
        echo
      fi
    fi

    if has_changed_paths '^(app/src/(main|debug|test)/kotlin/com/ytone/longcare/|core/|feature/|scripts/quality/verify_module_api_visibility\.sh)'; then
      run_step \
        "module-api-visibility" \
        bash scripts/quality/verify_module_api_visibility.sh app/src/main/kotlin/com/ytone/longcare "${ROOT_DIR}"
    else
      local detect_status=$?
      if [[ "${detect_status}" -eq 1 ]]; then
        echo "[preflight][SKIP] module-api-visibility (no relevant changed paths)"
        echo
      else
        echo "[preflight][FAIL] changed-path detection failed for module-api-visibility"
        FAILURES+=("changed-path-detection:module-api-visibility")
        echo
      fi
    fi
    return 0
  fi

  run_step \
    "architecture-boundaries" \
    bash scripts/quality/verify_architecture_boundaries.sh "${ROOT_DIR}"

  run_step \
    "module-dependency-whitelist" \
    bash scripts/quality/verify_module_dependency_whitelist.sh "${ROOT_DIR}"

  run_step \
    "module-api-visibility" \
    bash scripts/quality/verify_module_api_visibility.sh app/src/main/kotlin/com/ytone/longcare "${ROOT_DIR}"
}

cd "${ROOT_DIR}"
collect_changed_files

echo "[preflight] project-root=${ROOT_DIR}"
echo "[preflight] mode=${MODE}"
echo "[preflight] gate-layer-summary:"
echo "[preflight] - local-fast = developer prevention checks"
echo "[preflight] - ci-required = merge-blocking CI checks"
echo "[preflight] - release-required = release confidence checks"
if [[ "${CHANGED_ONLY_FALLBACK_ALL}" == "true" ]]; then
  echo "[preflight] changed-files-count=unknown (fallback-all-files: ${CHANGED_ONLY_FALLBACK_REASON})"
elif [[ -n "${CHANGED_FILES}" ]]; then
  echo "[preflight] changed-files-count=$(printf '%s\n' "${CHANGED_FILES}" | awk 'NF' | wc -l | tr -d ' ')"
else
  echo "[preflight] changed-files-count=0"
fi
echo

run_local_fast

if [[ "${MODE}" == "full" || "${MODE}" == "release" ]]; then
  run_step "compile-debug-kotlin" ./gradlew --no-daemon :app:compileDebugKotlin
  run_step "test-debug-unit" ./gradlew --no-daemon :app:testDebugUnitTest
fi

if [[ "${MODE}" == "release" ]]; then
  echo "[preflight] entering release-required gate layer via run_quality_gate.sh"
  echo
  run_step "run-quality-gate" bash scripts/quality/run_quality_gate.sh --project-root "${ROOT_DIR}"
fi

if [[ "${#FAILURES[@]}" -gt 0 ]]; then
  echo "[preflight] failed checks:"
  printf '[preflight] - %s\n' "${FAILURES[@]}"
  exit 1
fi

echo "[preflight] all checks passed."
