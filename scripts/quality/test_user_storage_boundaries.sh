#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GUARD="${ROOT_DIR}/scripts/quality/verify_user_storage_boundaries.sh"
TMP_ROOT="$(mktemp -d)"
PASS_COUNT=0

cleanup() {
  if [[ -n "${TMP_ROOT:-}" && -d "${TMP_ROOT}" ]]; then
    rm -rf -- "${TMP_ROOT}"
  fi
}
trap cleanup EXIT

fail() {
  echo "[user-storage-boundaries-test][FAIL] $1" >&2
  exit 1
}

write_kotlin() {
  local root="$1"
  local relative_path="$2"
  local content="$3"
  mkdir -p "$(dirname "${root}/${relative_path}")"
  printf '%s\n' "${content}" > "${root}/${relative_path}"
}

expect_success() {
  local label="$1"
  local root="$2"
  local output=""
  local status=0
  output="$(bash "${GUARD}" --project-root "${root}" 2>&1)" || status=$?
  if [[ "${status}" -ne 0 || "${output}" != *"verification passed"* ]]; then
    printf '%s\n' "${output}" >&2
    fail "${label}: expected success"
  fi
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[user-storage-boundaries-test][PASS] ${label}"
}

expect_failure() {
  local label="$1"
  local root="$2"
  local rule_id="$3"
  local output=""
  local status=0
  output="$(bash "${GUARD}" --project-root "${root}" 2>&1)" || status=$?
  if [[ "${status}" -eq 0 ]]; then
    printf '%s\n' "${output}" >&2
    fail "${label}: expected non-zero exit"
  fi
  if ! grep -Fq -- "rule=${rule_id}" <<< "${output}"; then
    printf '%s\n' "${output}" >&2
    fail "${label}: expected rule=${rule_id}"
  fi
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[user-storage-boundaries-test][PASS] ${label}"
}

new_fixture() {
  local name="$1"
  local root="${TMP_ROOT}/${name}"
  mkdir -p "${root}"
  printf '%s' "${root}"
}

GOOD_ROOT="$(new_fixture good)"
write_kotlin "${GOOD_ROOT}" \
  "core/data/src/main/kotlin/com/ytone/longcare/data/userstorage/UserDatabaseFactory.kt" \
  'package fixture; fun open() = Room.databaseBuilder(context, Db::class.java, "longcare_user_v1_digest.db")'
write_kotlin "${GOOD_ROOT}" \
  "core/data/src/main/kotlin/com/ytone/longcare/data/userstorage/UserDataStoreRegistry.kt" \
  'package fixture; fun open() = PreferenceDataStoreFactory.create { namespaceFile }'
write_kotlin "${GOOD_ROOT}" \
  "core/data/src/main/kotlin/com/ytone/longcare/di/ProcessSessionDataStoreModule.kt" \
  'package fixture; fun session() = PreferenceDataStoreFactory.create { encryptedSessionFile }'
write_kotlin "${GOOD_ROOT}" \
  "app/src/main/kotlin/com/ytone/longcare/features/service/ScopedTask.kt" \
  'package fixture; fun schedule(orderId: Long, taskCodec: ServiceTaskCodec) = enqueueUniqueWork(taskCodec.workName(orderId))'
expect_success "approved factories and scoped task" "${GOOD_ROOT}"

ROOM_ROOT="$(new_fixture room)"
write_kotlin "${ROOM_ROOT}" \
  "core/data/src/main/kotlin/com/ytone/longcare/di/DatabaseModule.kt" \
  'package fixture; fun database() = Room.databaseBuilder(context, Db::class.java, "global.db")'
expect_failure "Room builder outside user factory" "${ROOM_ROOT}" "room-creation-outside-user-factory"

DATASTORE_ROOT="$(new_fixture datastore)"
write_kotlin "${DATASTORE_ROOT}" \
  "feature/profile/src/main/kotlin/com/ytone/longcare/profile/ProfileStore.kt" \
  'package fixture; fun store() = PreferenceDataStoreFactory.create { profileFile }'
expect_failure "DataStore builder outside registry" "${DATASTORE_ROOT}" "datastore-creation-outside-registry"

DAO_ROOT="$(new_fixture dao)"
write_kotlin "${DAO_ROOT}" \
  "core/data/src/main/kotlin/com/ytone/longcare/di/DatabaseModule.kt" \
  'package fixture; class DatabaseModule { fun provideOrderDao(): OrderDao = database.orderDao() }'
expect_failure "global business DAO provider" "${DAO_ROOT}" "direct-business-dao-access"

BARE_USER_ROOT="$(new_fixture bare-user)"
write_kotlin "${BARE_USER_ROOT}" \
  "feature/profile/src/main/kotlin/com/ytone/longcare/profile/ProfileFiles.kt" \
  'package fixture; fun path(userId: Long) = "user_${userId}_prefs"'
expect_failure "bare user id filename" "${BARE_USER_ROOT}" "bare-user-id-filename"

PREFS_ROOT="$(new_fixture prefs)"
write_kotlin "${PREFS_ROOT}" \
  "app/src/main/kotlin/com/ytone/longcare/features/orders/OrderPreferences.kt" \
  'package fixture; fun prefs(context: Context) = context.getSharedPreferences("orders", 0)'
expect_failure "unscoped business SharedPreferences" "${PREFS_ROOT}" "unscoped-user-business-shared-preferences"

BACKGROUND_ROOT="$(new_fixture background)"
write_kotlin "${BACKGROUND_ROOT}" \
  "app/src/main/kotlin/com/ytone/longcare/features/service/LegacyOrderWorker.kt" \
  'package fixture; fun schedule(orderId: Long) = enqueueUniqueWork("service_${orderId}")'
expect_failure "orderId-only background identity" "${BACKGROUND_ROOT}" "order-id-only-background-identity"

echo "[user-storage-boundaries-test] all ${PASS_COUNT} fixtures passed."
