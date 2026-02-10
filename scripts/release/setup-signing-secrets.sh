#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./scripts/release/setup-signing-secrets.sh --jks /absolute/path/release.jks [options]

Options:
  --jks PATH            Path to release keystore (.jks/.keystore).
  --env NAME            GitHub Environment name. Default: release
  --repo OWNER/REPO     GitHub repository. Auto-detected from git remote.
  --gradle-props PATH   Local Gradle properties file. Default: ~/.gradle/gradle.properties
  --manual-file PATH    Optional output file for manual copy/paste secrets.
  --local-only          Only update local Gradle properties.
  --github-only         Only sync GitHub Environment secrets.
  --no-github           Disable GitHub sync (same as --local-only).
  --copy-base64         Copy ANDROID_KEYSTORE_BASE64 to clipboard (pbcopy/xclip).
  -h, --help            Show this help.

Environment variables (optional, avoids interactive prompt):
  LONGCARE_RELEASE_STORE_PASSWORD
  LONGCARE_RELEASE_KEY_ALIAS
  LONGCARE_RELEASE_KEY_PASSWORD
  RELEASE_STORE_PASSWORD
  RELEASE_KEY_ALIAS
  RELEASE_KEY_PASSWORD
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

ENV_NAME="release"
REPO=""
JKS_PATH=""
GRADLE_PROPS_FILE="${HOME}/.gradle/gradle.properties"
MANUAL_FILE=""
COPY_BASE64=0
WRITE_LOCAL=1
SYNC_GITHUB=1

RELEASE_STORE_PASSWORD="${LONGCARE_RELEASE_STORE_PASSWORD:-${RELEASE_STORE_PASSWORD:-}}"
RELEASE_KEY_ALIAS="${LONGCARE_RELEASE_KEY_ALIAS:-${RELEASE_KEY_ALIAS:-}}"
RELEASE_KEY_PASSWORD="${LONGCARE_RELEASE_KEY_PASSWORD:-${RELEASE_KEY_PASSWORD:-}}"

read_gradle_prop_raw() {
  local key="$1"
  local file="$2"
  [[ -f "${file}" ]] || return 0
  sed -n "s/^${key}=//p" "${file}" | tail -n 1
}

unescape_gradle_prop() {
  local value="$1"
  value="${value//\\\\/\\}"
  value="${value//\\ / }"
  value="${value//\\:/:}"
  value="${value//\\=/=}"
  value="${value//\\#/#}"
  value="${value//\\!/!}"
  printf '%s' "${value}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --jks)
      JKS_PATH="${2:-}"
      shift 2
      ;;
    --env)
      ENV_NAME="${2:-}"
      shift 2
      ;;
    --repo)
      REPO="${2:-}"
      shift 2
      ;;
    --gradle-props)
      GRADLE_PROPS_FILE="${2:-}"
      shift 2
      ;;
    --manual-file)
      MANUAL_FILE="${2:-}"
      shift 2
      ;;
    --local-only|--no-github)
      WRITE_LOCAL=1
      SYNC_GITHUB=0
      shift
      ;;
    --github-only)
      WRITE_LOCAL=0
      SYNC_GITHUB=1
      shift
      ;;
    --copy-base64)
      COPY_BASE64=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1"
      usage
      exit 1
      ;;
  esac
done

if [[ -z "${JKS_PATH}" ]]; then
  local_store_file_raw="$(read_gradle_prop_raw "LONGCARE_RELEASE_STORE_FILE" "${GRADLE_PROPS_FILE}")"
  if [[ -z "${local_store_file_raw}" ]]; then
    local_store_file_raw="$(read_gradle_prop_raw "RELEASE_STORE_FILE" "${GRADLE_PROPS_FILE}")"
  fi
  if [[ -n "${local_store_file_raw}" ]]; then
    JKS_PATH="$(unescape_gradle_prop "${local_store_file_raw}")"
  fi
fi

if [[ -z "${RELEASE_STORE_PASSWORD}" ]]; then
  store_password_raw="$(read_gradle_prop_raw "LONGCARE_RELEASE_STORE_PASSWORD" "${GRADLE_PROPS_FILE}")"
  if [[ -z "${store_password_raw}" ]]; then
    store_password_raw="$(read_gradle_prop_raw "RELEASE_STORE_PASSWORD" "${GRADLE_PROPS_FILE}")"
  fi
  if [[ -n "${store_password_raw}" ]]; then
    RELEASE_STORE_PASSWORD="$(unescape_gradle_prop "${store_password_raw}")"
  fi
fi

if [[ -z "${RELEASE_KEY_ALIAS}" ]]; then
  key_alias_raw="$(read_gradle_prop_raw "LONGCARE_RELEASE_KEY_ALIAS" "${GRADLE_PROPS_FILE}")"
  if [[ -z "${key_alias_raw}" ]]; then
    key_alias_raw="$(read_gradle_prop_raw "RELEASE_KEY_ALIAS" "${GRADLE_PROPS_FILE}")"
  fi
  if [[ -n "${key_alias_raw}" ]]; then
    RELEASE_KEY_ALIAS="$(unescape_gradle_prop "${key_alias_raw}")"
  fi
fi

if [[ -z "${RELEASE_KEY_PASSWORD}" ]]; then
  key_password_raw="$(read_gradle_prop_raw "LONGCARE_RELEASE_KEY_PASSWORD" "${GRADLE_PROPS_FILE}")"
  if [[ -z "${key_password_raw}" ]]; then
    key_password_raw="$(read_gradle_prop_raw "RELEASE_KEY_PASSWORD" "${GRADLE_PROPS_FILE}")"
  fi
  if [[ -n "${key_password_raw}" ]]; then
    RELEASE_KEY_PASSWORD="$(unescape_gradle_prop "${key_password_raw}")"
  fi
fi

if [[ -z "${JKS_PATH}" ]]; then
  read -r -p "Keystore path (--jks): " JKS_PATH
fi

if [[ "${JKS_PATH}" != /* ]]; then
  JKS_PATH="${PWD}/${JKS_PATH}"
fi
if [[ ! -f "${JKS_PATH}" ]]; then
  echo "Keystore file not found: ${JKS_PATH}"
  exit 1
fi
JKS_PATH="$(cd "$(dirname "${JKS_PATH}")" && pwd)/$(basename "${JKS_PATH}")"

if [[ -z "${RELEASE_STORE_PASSWORD}" ]]; then
  read -r -s -p "RELEASE_STORE_PASSWORD: " RELEASE_STORE_PASSWORD
  echo
fi
if [[ -z "${RELEASE_STORE_PASSWORD}" ]]; then
  echo "RELEASE_STORE_PASSWORD is required."
  exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool is required but not found in PATH."
  exit 1
fi

detect_key_alias() {
  local output alias
  set +e
  output="$(keytool -list -v -keystore "${JKS_PATH}" -storepass "${RELEASE_STORE_PASSWORD}" 2>/dev/null)"
  set -e
  alias="$(printf '%s\n' "${output}" | sed -nE 's/^Alias name: *(.+)$/\1/p' | head -n 1)"
  if [[ -z "${alias}" ]]; then
    alias="$(printf '%s\n' "${output}" | sed -nE 's/^别名名称: *(.+)$/\1/p' | head -n 1)"
  fi
  printf '%s' "${alias}"
}

if [[ -z "${RELEASE_KEY_ALIAS}" ]]; then
  RELEASE_KEY_ALIAS="$(detect_key_alias || true)"
fi
if [[ -z "${RELEASE_KEY_ALIAS}" ]]; then
  read -r -p "RELEASE_KEY_ALIAS: " RELEASE_KEY_ALIAS
fi
if [[ -z "${RELEASE_KEY_ALIAS}" ]]; then
  echo "RELEASE_KEY_ALIAS is required."
  exit 1
fi

if [[ -z "${RELEASE_KEY_PASSWORD}" ]]; then
  read -r -s -p "RELEASE_KEY_PASSWORD (leave empty to reuse store password): " RELEASE_KEY_PASSWORD
  echo
fi
if [[ -z "${RELEASE_KEY_PASSWORD}" ]]; then
  RELEASE_KEY_PASSWORD="${RELEASE_STORE_PASSWORD}"
fi

ANDROID_KEYSTORE_BASE64="$(base64 < "${JKS_PATH}" | tr -d '\r\n')"

escape_gradle_prop() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//:/\\:}"
  value="${value//=/\\=}"
  value="${value//#/\\#}"
  value="${value//!/\\!}"
  value="${value// /\\ }"
  printf '%s' "${value}"
}

write_local_gradle_props() {
  local start_marker="# >>> longcare release signing >>>"
  local end_marker="# <<< longcare release signing <<<"
  local escaped_store_file escaped_store_password escaped_key_alias escaped_key_password tmp

  escaped_store_file="$(escape_gradle_prop "${JKS_PATH}")"
  escaped_store_password="$(escape_gradle_prop "${RELEASE_STORE_PASSWORD}")"
  escaped_key_alias="$(escape_gradle_prop "${RELEASE_KEY_ALIAS}")"
  escaped_key_password="$(escape_gradle_prop "${RELEASE_KEY_PASSWORD}")"

  mkdir -p "$(dirname "${GRADLE_PROPS_FILE}")"
  touch "${GRADLE_PROPS_FILE}"
  chmod 600 "${GRADLE_PROPS_FILE}" || true

  tmp="$(mktemp)"
  awk -v start="${start_marker}" -v end="${end_marker}" '
    $0 == start { skip = 1; next }
    $0 == end { skip = 0; next }
    !skip { print }
  ' "${GRADLE_PROPS_FILE}" > "${tmp}"

  {
    cat "${tmp}"
    echo
    echo "${start_marker}"
    echo "LONGCARE_RELEASE_STORE_FILE=${escaped_store_file}"
    echo "LONGCARE_RELEASE_STORE_PASSWORD=${escaped_store_password}"
    echo "LONGCARE_RELEASE_KEY_ALIAS=${escaped_key_alias}"
    echo "LONGCARE_RELEASE_KEY_PASSWORD=${escaped_key_password}"
    echo "${end_marker}"
  } > "${GRADLE_PROPS_FILE}"
  rm -f "${tmp}"

  echo "Local signing config updated (LONGCARE_RELEASE_*): ${GRADLE_PROPS_FILE}"
}

detect_repo_from_git() {
  local origin
  origin="$(git -C "${PROJECT_ROOT}" remote get-url origin 2>/dev/null || true)"
  if [[ -z "${origin}" ]]; then
    return 0
  fi
  origin="${origin%.git}"
  origin="${origin#git@github.com:}"
  origin="${origin#https://github.com/}"
  if [[ "${origin}" == */* ]]; then
    printf '%s' "${origin}"
  fi
}

if [[ -z "${REPO}" ]]; then
  REPO="$(detect_repo_from_git || true)"
fi

sync_github_secrets() {
  if ! command -v gh >/dev/null 2>&1; then
    echo "gh CLI not found. Skip GitHub secret sync."
    echo "Install GitHub CLI first: https://cli.github.com/"
    echo "Then run: ./scripts/release/setup-signing-secrets.sh --jks \"${JKS_PATH}\" --github-only --env \"${ENV_NAME}\""
    return 0
  fi
  if [[ -z "${GH_TOKEN:-}" && -z "${GITHUB_TOKEN:-}" ]]; then
    if ! gh auth status >/dev/null 2>&1; then
      echo "gh is not authenticated. Run: gh auth login"
      echo "Or pass token via environment variable GH_TOKEN / GITHUB_TOKEN."
      return 1
    fi
  fi
  if [[ -z "${REPO}" ]]; then
    echo "Repository could not be auto-detected. Use --repo OWNER/REPO."
    return 1
  fi

  printf '%s' "${ANDROID_KEYSTORE_BASE64}" | gh secret set --repo "${REPO}" --env "${ENV_NAME}" ANDROID_KEYSTORE_BASE64
  printf '%s' "${RELEASE_STORE_PASSWORD}" | gh secret set --repo "${REPO}" --env "${ENV_NAME}" RELEASE_STORE_PASSWORD
  printf '%s' "${RELEASE_KEY_ALIAS}" | gh secret set --repo "${REPO}" --env "${ENV_NAME}" RELEASE_KEY_ALIAS
  printf '%s' "${RELEASE_KEY_PASSWORD}" | gh secret set --repo "${REPO}" --env "${ENV_NAME}" RELEASE_KEY_PASSWORD

  echo "GitHub Environment secrets synced: repo=${REPO}, env=${ENV_NAME}"
  if ! gh secret list --repo "${REPO}" --env "${ENV_NAME}"; then
    echo "Warning: secrets were synced, but listing secrets failed due to network/API issue."
  fi
}

if [[ -n "${MANUAL_FILE}" ]]; then
  {
    echo "ANDROID_KEYSTORE_BASE64=${ANDROID_KEYSTORE_BASE64}"
    echo "RELEASE_STORE_PASSWORD=${RELEASE_STORE_PASSWORD}"
    echo "RELEASE_KEY_ALIAS=${RELEASE_KEY_ALIAS}"
    echo "RELEASE_KEY_PASSWORD=${RELEASE_KEY_PASSWORD}"
  } > "${MANUAL_FILE}"
  chmod 600 "${MANUAL_FILE}" || true
  echo "Manual copy file generated: ${MANUAL_FILE}"
fi

if [[ "${COPY_BASE64}" -eq 1 ]]; then
  if command -v pbcopy >/dev/null 2>&1; then
    printf '%s' "${ANDROID_KEYSTORE_BASE64}" | pbcopy
    echo "Copied ANDROID_KEYSTORE_BASE64 to clipboard with pbcopy."
  elif command -v xclip >/dev/null 2>&1; then
    printf '%s' "${ANDROID_KEYSTORE_BASE64}" | xclip -selection clipboard
    echo "Copied ANDROID_KEYSTORE_BASE64 to clipboard with xclip."
  else
    echo "Clipboard utility not found (pbcopy/xclip)."
  fi
fi

if [[ "${WRITE_LOCAL}" -eq 1 ]]; then
  write_local_gradle_props
fi

if [[ "${SYNC_GITHUB}" -eq 1 ]]; then
  sync_github_secrets
fi

echo
echo "Done."
echo "Use one release key for both local and CI so update signatures stay consistent."
echo "Local uses LONGCARE_RELEASE_*; CI uses the same key material via ANDROID_KEYSTORE_BASE64 + RELEASE_* secrets."
if [[ -n "${MANUAL_FILE}" ]]; then
  echo "After using ${MANUAL_FILE}, delete it if no longer needed."
fi
