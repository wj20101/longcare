#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="."
ALLOWLIST_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-root)
      PROJECT_ROOT="${2:-}"
      shift 2
      ;;
    --allowlist)
      ALLOWLIST_FILE="${2:-}"
      shift 2
      ;;
    -h|--help)
      echo "Usage: verify_dependency_policy.sh [--project-root <path>] [--allowlist <path>]"
      exit 0
      ;;
    *)
      echo "[dependency-policy][FAIL] unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

PROJECT_ROOT="$(cd "${PROJECT_ROOT}" && pwd)"
CATALOG_FILE="${PROJECT_ROOT}/gradle/libs.versions.toml"
GRADLE_PROPERTIES="${PROJECT_ROOT}/gradle.properties"
APP_BUILD_FILE="${PROJECT_ROOT}/app/build.gradle.kts"
if [[ -z "${ALLOWLIST_FILE}" ]]; then
  ALLOWLIST_FILE="${PROJECT_ROOT}/scripts/quality/dependency_preview_allowlist.txt"
elif [[ "${ALLOWLIST_FILE}" != /* ]]; then
  ALLOWLIST_FILE="${PROJECT_ROOT}/${ALLOWLIST_FILE}"
fi

ERRORS=()
fail() { ERRORS+=("$1"); }

for required in "${CATALOG_FILE}" "${ALLOWLIST_FILE}"; do
  [[ -f "${required}" ]] || fail "required file is missing: ${required#"${PROJECT_ROOT}/"}"
done

VERSIONS_FILE="$(mktemp "${TMPDIR:-/tmp}/longcare-versions.XXXXXX")"
trap 'rm -f "${VERSIONS_FILE}"' EXIT

if [[ -f "${CATALOG_FILE}" ]]; then
  awk '
    /^\[versions\][[:space:]]*$/ { in_versions = 1; next }
    in_versions && /^\[/ { exit }
    in_versions && /^[[:space:]]*[A-Za-z0-9_-]+[[:space:]]*=[[:space:]]*"[^"]+"/ {
      line = $0
      alias = line
      sub(/[[:space:]]*=.*$/, "", alias)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", alias)
      version = line
      sub(/^[^=]*=[[:space:]]*"/, "", version)
      sub(/"[[:space:]]*(#.*)?$/, "", version)
      print alias "|" version
    }
  ' "${CATALOG_FILE}" > "${VERSIONS_FILE}"
fi

while IFS='|' read -r alias version owner reason validation exit_version extra; do
  [[ -z "${alias}" || "${alias}" == \#* ]] && continue
  if [[ -n "${extra:-}" ]]; then
    fail "allowlist entry ${alias} must contain exactly six fields"
  fi
  [[ -n "${version}" ]] || fail "allowlist entry ${alias} is missing exact-version"
  [[ -n "${owner}" ]] || fail "allowlist entry ${alias} is missing owner"
  [[ -n "${reason}" ]] || fail "allowlist entry ${alias} is missing reason"
  [[ -n "${validation}" ]] || fail "allowlist entry ${alias} is missing validation-scope"
  [[ -n "${exit_version}" ]] || fail "allowlist entry ${alias} is missing exit-version"
  if [[ "${alias}${version}" == *'*'* || "${alias}${version}" == *'?'* || "${version}" == *'+'* ]]; then
    fail "allowlist entry ${alias} is generalized; alias and version must be exact"
  fi
  if [[ -n "${exit_version}" && ! "${exit_version}" =~ ^[0-9]+(\.[0-9]+){1,3}$ ]]; then
    fail "allowlist entry ${alias} has invalid exit-version=${exit_version}"
  fi
  catalog_version="$(awk -F'|' -v wanted="${alias}" '$1 == wanted { print $2; exit }' "${VERSIONS_FILE}")"
  if [[ -z "${catalog_version}" ]]; then
    fail "allowlist entry ${alias} is stale; alias is absent from the version catalog"
  elif [[ "${catalog_version}" != "${version}" ]]; then
    fail "allowlist entry ${alias} is stale; catalog=${catalog_version}, allowlist=${version}"
  fi
done < "${ALLOWLIST_FILE}"

while IFS='|' read -r alias version; do
  lower_version="$(printf '%s' "${version}" | tr '[:upper:]' '[:lower:]')"
  if [[ "${lower_version}" == *'+'* || "${lower_version}" == "latest" || "${lower_version}" == "release" ]]; then
    fail "version alias ${alias} uses forbidden dynamic version ${version}"
  fi
  if [[ "${lower_version}" =~ (^|[.-])(alpha|beta|rc|snapshot|dev)([0-9.-]|$) || "${lower_version}" == *compat* ]]; then
    if ! grep -Fq "${alias}|${version}|" "${ALLOWLIST_FILE}"; then
      fail "preview/compat version alias ${alias}=${version} is not exactly allowlisted"
    fi
  fi
done < "${VERSIONS_FILE}"

if [[ -f "${APP_BUILD_FILE}" ]] && grep -Fq 'maxAgpVersion = false' "${APP_BUILD_FILE}"; then
  baseline_version="$(awk -F'|' '$1 == "androidxBaselineProfile" { print $2; exit }' "${VERSIONS_FILE}")"
  if [[ -z "${baseline_version}" ]] || ! grep -Fq "androidxBaselineProfile|${baseline_version}|" "${ALLOWLIST_FILE}"; then
    fail "maxAgpVersion=false requires an exact androidxBaselineProfile preview waiver"
  fi
fi

agp_version="$(awk -F'|' '$1 == "agp" { print $2; exit }' "${VERSIONS_FILE}")"
agp_major="${agp_version%%.*}"
if [[ -f "${GRADLE_PROPERTIES}" ]] && grep -Eq '^[[:space:]]*android\.enableJetifier[[:space:]]*=[[:space:]]*true[[:space:]]*$' "${GRADLE_PROPERTIES}"; then
  if [[ "${agp_major}" =~ ^[0-9]+$ ]] && (( agp_major >= 10 )); then
    fail "AGP ${agp_version} is blocked while android.enableJetifier=true and vendor AARs still require Jetifier"
  fi
fi

if [[ ${#ERRORS[@]} -gt 0 ]]; then
  for error in "${ERRORS[@]}"; do
    echo "[dependency-policy][FAIL] ${error}" >&2
  done
  exit 1
fi

echo "[dependency-policy][PASS] dependency stability and vendor boundaries are coherent."
