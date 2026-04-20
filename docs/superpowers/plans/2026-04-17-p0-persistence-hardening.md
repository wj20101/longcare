# P0 Persistence Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `subagent-driven-development` or `executing-plans` to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Eliminate high-risk persistence issues by removing sensitive plaintext storage, replacing face `base64` persistence with file-backed storage, and keeping face SDK secrets out of long-lived local storage.

**Architecture:** Keep the existing app behavior stable while introducing a small secure-storage layer and a file-backed face cache. Migrate old persisted values lazily on read where possible, and make cleanup behavior explicit on logout and user switch.

**Tech Stack:** Android DataStore, SharedPreferences, app-private files, existing `CryptoUtils`, Hilt, Room-compatible migration patterns, JUnit/Robolectric tests.

---

## Scope

- In:
  - Face cache persistence in `feature:identification`
  - Logged-in user persistence in `core:data`
  - System config persistence for face SDK secrets
  - Logout/user-switch cleanup for sensitive local files
  - Regression tests for migration and cleanup behavior
- Out:
  - Room schema redesign
  - Pending orders migration to Room
  - General cache performance tuning for Coil/OkHttp
  - Third-party SDK internal storage

## Task 1: Freeze Current Behavior With Tests

**Files:**
- Modify: [app/src/test/kotlin/com/ytone/longcare/features/identification/data/IdentificationFaceDataSourceTest.kt](/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/identification/data/IdentificationFaceDataSourceTest.kt)
- Create: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/data/repository/DefaultUserSessionRepositoryPersistenceTest.kt`
- Create: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/common/utils/SystemConfigManagerPersistenceTest.kt`

- [ ] Add a test that seeds legacy face `base64` data and verifies the new implementation can still read it once.
- [ ] Add a test that verifies logout removes both face metadata and face image files.
- [ ] Add a test that proves session persistence no longer stores `token` or `identityCardNumber` in plaintext preferences.
- [ ] Add a test that proves system config persistence does not retain decrypted `TxFaceAppSecret`.
- [ ] Run only the new persistence-focused tests and confirm they fail against current behavior before implementation starts.

## Task 2: Replace Face `base64` Persistence With File-Backed Storage

**Files:**
- Modify: [feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/data/IdentificationFaceDataSource.kt](/Users/wajie/StudioProjects/longcare/feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/data/IdentificationFaceDataSource.kt)
- Modify: [feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/data/SetupFaceGatewayImpl.kt](/Users/wajie/StudioProjects/longcare/feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/data/SetupFaceGatewayImpl.kt)
- Modify: [feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/data/VerifyServicePersonDataGatewayImpl.kt](/Users/wajie/StudioProjects/longcare/feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/data/VerifyServicePersonDataGatewayImpl.kt)
- Modify: [feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/vm/IdentificationFaceSetupPreparation.kt](/Users/wajie/StudioProjects/longcare/feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/vm/IdentificationFaceSetupPreparation.kt)
- Create: `/Users/wajie/StudioProjects/longcare/feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/data/FaceFileStore.kt`
- Create: `/Users/wajie/StudioProjects/longcare/feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/data/model/FaceCacheRecord.kt`

- [ ] Introduce `FaceFileStore` as the only component allowed to write/read persisted face image content under a single directory such as `filesDir/face_store/`.
- [ ] Change `IdentificationFaceDataSource` so persisted metadata stores only small values such as file name, hash, and timestamps rather than image `base64`.
- [ ] Keep a compatibility read path: if legacy `face_base64_user_*` exists, decode it, move it into the new file store, persist new metadata, and delete the old key.
- [ ] Keep the public read API returning `base64` only as a computed runtime value so existing face verification call sites can stay stable in phase 1.
- [ ] Update cleanup logic so logout deletes face metadata plus all user-scoped face files from the unified directory.
- [ ] Verify the service-person verification flow still works for all three cases: cached local face, remote face URL fallback, and first-time face setup.

## Task 3: Split Session Persistence Into Public Profile And Secure Secret Storage

**Files:**
- Modify: [core/data/src/main/kotlin/com/ytone/longcare/data/repository/DefaultUserSessionRepository.kt](/Users/wajie/StudioProjects/longcare/core/data/src/main/kotlin/com/ytone/longcare/data/repository/DefaultUserSessionRepository.kt)
- Modify: [core/model/src/main/kotlin/com/ytone/longcare/model/User.kt](/Users/wajie/StudioProjects/longcare/core/model/src/main/kotlin/com/ytone/longcare/model/User.kt)
- Modify: [core/data/src/main/kotlin/com/ytone/longcare/data/storage/AppDataStore.kt](/Users/wajie/StudioProjects/longcare/core/data/src/main/kotlin/com/ytone/longcare/data/storage/AppDataStore.kt)
- Create: `/Users/wajie/StudioProjects/longcare/core/data/src/main/kotlin/com/ytone/longcare/data/storage/SecureSessionStore.kt`
- Create: `/Users/wajie/StudioProjects/longcare/core/data/src/main/kotlin/com/ytone/longcare/data/storage/model/PersistedUserProfile.kt`
- Create: `/Users/wajie/StudioProjects/longcare/core/data/src/main/kotlin/com/ytone/longcare/di/SecureStorageModule.kt`

- [ ] Define a `PersistedUserProfile` type that excludes `token` and `identityCardNumber`.
- [ ] Add `SecureSessionStore` that encrypts sensitive session fields using the existing keystore-backed crypto utilities before persisting them.
- [ ] Update `DefaultUserSessionRepository` to persist the profile and secrets separately, and to reconstruct `SessionState.LoggedIn` by combining them on read.
- [ ] Add a one-time compatibility migration that reads the old `app_user` blob, splits it into profile + secret storage, then deletes the old blob.
- [ ] Ensure logout clears both the public profile storage and the secure secret storage.
- [ ] Verify cold start, login, update user, and logout all behave the same from the UI’s perspective.

## Task 4: Keep Decrypted Face SDK Secrets Out Of Long-Lived Disk Storage

**Files:**
- Modify: [core/data/src/main/kotlin/com/ytone/longcare/common/utils/SystemConfigManager.kt](/Users/wajie/StudioProjects/longcare/core/data/src/main/kotlin/com/ytone/longcare/common/utils/SystemConfigManager.kt)
- Modify: [core/model/src/main/kotlin/com/ytone/longcare/model/SystemConfigModel.kt](/Users/wajie/StudioProjects/longcare/core/model/src/main/kotlin/com/ytone/longcare/model/SystemConfigModel.kt)
- Modify: [core/model/src/main/kotlin/com/ytone/longcare/model/ThirdKeyReturnModel.kt](/Users/wajie/StudioProjects/longcare/core/model/src/main/kotlin/com/ytone/longcare/model/ThirdKeyReturnModel.kt)
- Create: `/Users/wajie/StudioProjects/longcare/core/data/src/main/kotlin/com/ytone/longcare/common/utils/SensitiveConfigCache.kt`

- [ ] Change `SystemConfigManager` persistence so only non-sensitive config fields or the original encrypted `thirdKeyStr` are written to disk.
- [ ] Move decrypted `ThirdKeyReturnModel` into an in-memory cache with explicit invalidation and a conservative TTL.
- [ ] Ensure `getFaceVerificationConfig()` uses the in-memory decrypted value and can lazily rehydrate from the encrypted on-disk payload when necessary.
- [ ] Remove any code path that serializes decrypted `TxFaceAppSecret` back into `SharedPreferences`.
- [ ] Verify app restart, config refresh, and face verification initialization still succeed.

## Task 5: Make Sensitive Cleanup Explicit On Logout And User Switch

**Files:**
- Modify: [core/data/src/main/kotlin/com/ytone/longcare/data/repository/ProfileRepositoryImpl.kt](/Users/wajie/StudioProjects/longcare/core/data/src/main/kotlin/com/ytone/longcare/data/repository/ProfileRepositoryImpl.kt)
- Modify: [feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/data/IdentificationFaceDataSource.kt](/Users/wajie/StudioProjects/longcare/feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/data/IdentificationFaceDataSource.kt)
- Modify: `/Users/wajie/StudioProjects/longcare/core/data/src/main/kotlin/com/ytone/longcare/data/storage/SecureSessionStore.kt`

- [ ] Centralize “sensitive local data cleanup” so logout clears secure session secrets, face metadata, and face files in one chain.
- [ ] Decide and document whether switching users without a full process death uses the same cleanup path; if not, add an explicit user-switch cleanup entry point.
- [ ] Ensure cleanup is best-effort for local files but never blocks logout completion.
- [ ] Add logs/metrics that make cleanup success or failure diagnosable without exposing sensitive content.

## Task 6: Validation And Rollout

**Files:**
- Modify as needed: tests under `app/src/test` and `feature/identification/src/test`
- Create: `/Users/wajie/StudioProjects/longcare/docs/architecture/persistence-hardening-rollout-notes.md`

- [ ] Run the full targeted test set for face persistence, session restore, system config restore, and logout cleanup.
- [ ] Add a one-off developer verification checklist for upgrading from an app state that still contains legacy `face_base64_user_*` and old `app_user`.
- [ ] Document the migration behavior, fallback behavior, and rollback considerations.
- [ ] Capture a short manual QA checklist covering login, restart, face verify, face setup, logout, and re-login.

## Validation Commands

- `./gradlew :feature:identification:testDebugUnitTest`
- `./gradlew :app:testDebugUnitTest --tests "*IdentificationFaceDataSourceTest" --tests "*DefaultUserSessionRepositoryPersistenceTest" --tests "*SystemConfigManagerPersistenceTest"`
- `./gradlew :app:assembleDebug`

## Done When

- [ ] Face image content is no longer persisted as `base64` in DataStore or SharedPreferences.
- [ ] Session token and identity card number are no longer stored in plaintext app preferences.
- [ ] Decrypted Tencent face SDK secret is not retained in disk-backed preferences.
- [ ] Logout removes both secure secrets and local face artifacts.
- [ ] Legacy persisted data can still be read once and migrated forward without breaking the main flows.
