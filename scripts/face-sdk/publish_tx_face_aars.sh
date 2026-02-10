#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Publish Tencent face SDK local AAR files to Maven local or a remote Maven repository.

Usage:
  scripts/face-sdk/publish_tx_face_aars.sh [options]

Options:
  --mode <local|deploy>             Publish mode (default: local)
  --group-id <groupId>              Maven groupId (default: com.tencent.cloud.huiyansdkface)
  --live-aar <path>                 Live SDK AAR path
  --normal-aar <path>               Normal SDK AAR path
  --live-artifact-id <artifactId>   Live SDK artifactId (default: wbcloudface-live)
  --normal-artifact-id <artifactId> Normal SDK artifactId (default: wbcloudface-normal)
  --live-version <version>          Live SDK version (default: 6.6.2-8e4718fc)
  --normal-version <version>        Normal SDK version (default: 5.1.10-4e3e198)
  --repo-url <url>                  Remote Maven repository URL (required when mode=deploy)
  --repo-id <id>                    Maven repository id (default: tx-face-private)
  -h, --help                        Show this help

Examples:
  # Publish to Maven local (~/.m2/repository)
  scripts/face-sdk/publish_tx_face_aars.sh

  # Publish to private Maven repository
  scripts/face-sdk/publish_tx_face_aars.sh \
    --mode deploy \
    --repo-url https://repo.example.com/repository/maven-releases/
USAGE
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

MODE="local"
GROUP_ID="com.tencent.cloud.huiyansdkface"
LIVE_AAR="${ROOT_DIR}/app/libs/WbCloudFaceLiveSdk-face-v6.6.2-8e4718fc.aar"
NORMAL_AAR="${ROOT_DIR}/app/libs/WbCloudNormal-v5.1.10-4e3e198.aar"
LIVE_ARTIFACT_ID="wbcloudface-live"
NORMAL_ARTIFACT_ID="wbcloudface-normal"
LIVE_VERSION="6.6.2-8e4718fc"
NORMAL_VERSION="5.1.10-4e3e198"
REPO_URL=""
REPO_ID="tx-face-private"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      MODE="${2:-}"
      shift 2
      ;;
    --group-id)
      GROUP_ID="${2:-}"
      shift 2
      ;;
    --live-aar)
      LIVE_AAR="${2:-}"
      shift 2
      ;;
    --normal-aar)
      NORMAL_AAR="${2:-}"
      shift 2
      ;;
    --live-artifact-id)
      LIVE_ARTIFACT_ID="${2:-}"
      shift 2
      ;;
    --normal-artifact-id)
      NORMAL_ARTIFACT_ID="${2:-}"
      shift 2
      ;;
    --live-version)
      LIVE_VERSION="${2:-}"
      shift 2
      ;;
    --normal-version)
      NORMAL_VERSION="${2:-}"
      shift 2
      ;;
    --repo-url)
      REPO_URL="${2:-}"
      shift 2
      ;;
    --repo-id)
      REPO_ID="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ "${MODE}" != "local" && "${MODE}" != "deploy" ]]; then
  echo "Invalid --mode: ${MODE}. Expected local or deploy." >&2
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn command not found. Please install Maven first." >&2
  exit 1
fi

if [[ ! -f "${LIVE_AAR}" ]]; then
  echo "Live AAR not found: ${LIVE_AAR}" >&2
  exit 1
fi
if [[ ! -f "${NORMAL_AAR}" ]]; then
  echo "Normal AAR not found: ${NORMAL_AAR}" >&2
  exit 1
fi

if [[ "${MODE}" == "deploy" && -z "${REPO_URL}" ]]; then
  echo "--repo-url is required when --mode=deploy" >&2
  exit 1
fi

publish_one() {
  local file_path="$1"
  local artifact_id="$2"
  local version="$3"

  if [[ "${MODE}" == "local" ]]; then
    mvn -q install:install-file \
      -Dfile="${file_path}" \
      -DgroupId="${GROUP_ID}" \
      -DartifactId="${artifact_id}" \
      -Dversion="${version}" \
      -Dpackaging=aar \
      -DgeneratePom=true
  else
    mvn -q deploy:deploy-file \
      -Dfile="${file_path}" \
      -DgroupId="${GROUP_ID}" \
      -DartifactId="${artifact_id}" \
      -Dversion="${version}" \
      -Dpackaging=aar \
      -DgeneratePom=true \
      -Durl="${REPO_URL}" \
      -DrepositoryId="${REPO_ID}"
  fi
}

echo "Publishing Tencent face SDK AARs..."
publish_one "${LIVE_AAR}" "${LIVE_ARTIFACT_ID}" "${LIVE_VERSION}"
publish_one "${NORMAL_AAR}" "${NORMAL_ARTIFACT_ID}" "${NORMAL_VERSION}"

echo
if [[ "${MODE}" == "local" ]]; then
  echo "Publish succeeded to Maven local (~/.m2/repository)."
else
  echo "Publish succeeded to remote repo: ${REPO_URL}"
fi

echo
LIVE_COORD="${GROUP_ID}:${LIVE_ARTIFACT_ID}:${LIVE_VERSION}"
NORMAL_COORD="${GROUP_ID}:${NORMAL_ARTIFACT_ID}:${NORMAL_VERSION}"

echo "Set these Gradle properties (local or CI):"
echo "  TX_FACE_SDK_SOURCE=maven"
echo "  TX_FACE_LIVE_COORD=${LIVE_COORD}"
echo "  TX_FACE_NORMAL_COORD=${NORMAL_COORD}"
if [[ "${MODE}" == "deploy" ]]; then
  echo "  TX_FACE_MAVEN_REPO_URL=${REPO_URL}"
fi

echo
echo "Validation command:"
echo "  ./gradlew :app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest -PTX_FACE_SDK_SOURCE=maven -PTX_FACE_LIVE_COORD=${LIVE_COORD} -PTX_FACE_NORMAL_COORD=${NORMAL_COORD}"
