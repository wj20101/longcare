#!/usr/bin/env bash
set -euo pipefail

MIN_FREE_MB=8192
CHECK_PATH="/"
DRY_RUN="false"

usage() {
  cat <<'EOF'
Usage: free_runner_disk_space.sh [--min-free-mb N] [--check-path PATH] [--dry-run]

Options:
  --min-free-mb N   Minimum free space in MB required after cleanup (default: 8192)
  --check-path PATH Filesystem path used for free-space check (default: /)
  --dry-run         Print cleanup targets without deleting
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --min-free-mb)
      MIN_FREE_MB="${2:-}"
      shift 2
      ;;
    --check-path)
      CHECK_PATH="${2:-}"
      shift 2
      ;;
    --dry-run)
      DRY_RUN="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if ! [[ "${MIN_FREE_MB}" =~ ^[0-9]+$ ]]; then
  echo "Invalid --min-free-mb value: ${MIN_FREE_MB}" >&2
  exit 1
fi

if [[ ! -d "${CHECK_PATH}" ]]; then
  echo "Invalid --check-path (directory not found): ${CHECK_PATH}" >&2
  exit 1
fi

cleanup_targets=(
  "/usr/share/dotnet"
  "/opt/ghc"
  "/usr/local/share/boost"
  "/opt/hostedtoolcache/CodeQL"
)

echo "[runner-disk] target path: ${CHECK_PATH}"
echo "[runner-disk] minimum free space: ${MIN_FREE_MB}MB"
echo "[runner-disk] disk usage before cleanup:"
df -h "${CHECK_PATH}"

if [[ "${DRY_RUN}" != "true" && "${CI:-}" != "true" ]]; then
  echo "[runner-disk] CI environment not detected. Refusing destructive cleanup outside CI."
  echo "[runner-disk] Re-run with --dry-run for local validation."
  exit 0
fi

for target in "${cleanup_targets[@]}"; do
  if [[ -e "${target}" ]]; then
    if [[ "${DRY_RUN}" == "true" ]]; then
      echo "[runner-disk][dry-run] would remove: ${target}"
    else
      echo "[runner-disk] removing: ${target}"
      sudo rm -rf "${target}" || true
    fi
  else
    echo "[runner-disk] skip missing: ${target}"
  fi
done

echo "[runner-disk] disk usage after cleanup:"
df -h "${CHECK_PATH}"

free_mb="$(df -Pm "${CHECK_PATH}" | awk 'NR==2 {print $4}')"
if [[ -z "${free_mb}" ]]; then
  echo "[runner-disk][FAIL] failed to parse free space from df output." >&2
  exit 1
fi

if (( free_mb < MIN_FREE_MB )); then
  echo "[runner-disk][FAIL] insufficient free space: ${free_mb}MB < ${MIN_FREE_MB}MB"
  exit 1
fi

echo "[runner-disk][PASS] free space ${free_mb}MB >= ${MIN_FREE_MB}MB"
