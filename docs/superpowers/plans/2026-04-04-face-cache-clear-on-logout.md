# Face Cache Clear On Logout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clear the persistent per-user face cache when logging out, while keeping the existing login-time and verification-time face fetch order unchanged.

**Architecture:** Add a focused deletion API to `IdentificationFaceDataSource`, then invoke it from the repository logout chain after capturing the current logged-in `userId` but before clearing the session. Keep the service-person face verification decision order exactly as it is today.

**Tech Stack:** Kotlin, Android DataStore Preferences, Hilt-injected repositories/data sources, coroutine-based logout flow, Gradle compilation.

---

## File Structure

- `feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/data/IdentificationFaceDataSource.kt`
  Purpose: Own local per-user face cache read/write/delete operations.

- `core/data/src/main/kotlin/com/ytone/longcare/data/repository/ProfileRepositoryImpl.kt`
  Purpose: Orchestrate logout, including current-user lookup, local face cache cleanup, and session clearing.

- `feature/identification/src/test/kotlin/com/ytone/longcare/features/identification/data/IdentificationFaceDataSourceTest.kt`
  Purpose: Cover the new cache-clear behavior at the datasource level if a straightforward local test can be added without extra Android framework setup.

- `core/data/src/test/kotlin/com/ytone/longcare/data/repository/ProfileRepositoryImplTest.kt`
  Purpose: Verify logout clears the user face cache for the current logged-in user before clearing session, while still completing logout.

## Task 1: Add a Focused Face Cache Clear API

**Files:**
- Modify: `feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/data/IdentificationFaceDataSource.kt`
- Create: `feature/identification/src/test/kotlin/com/ytone/longcare/features/identification/data/IdentificationFaceDataSourceTest.kt` (only if feasible with the module’s current test setup)

- [ ] **Step 1: Add a cache deletion method to `IdentificationFaceDataSource`**

Implement a focused deletion API:

```kotlin
suspend fun clearUserFaceBase64(userId: Int) {
    try {
        val dataStore = getDataStoreForUser(userId)
        val key = stringPreferencesKey(FACE_BASE64_KEY_PREFIX + userId)
        dataStore.edit { prefs ->
            prefs.remove(key)
        }
        logD("成功清理人脸缓存 (userId=$userId)", tag = TAG)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logE("清理人脸缓存异常 (userId=$userId)", tag = TAG, throwable = e)
    }
}
```

- [ ] **Step 2: Add the smallest feasible datasource verification**

If the module’s current test setup can exercise the method directly, add a test that verifies:

```kotlin
1. writeUserFaceBase64(userId, "abc")
2. readUserFaceBase64(userId) == "abc"
3. clearUserFaceBase64(userId)
4. readUserFaceBase64(userId) == null
```

If that test setup turns out to require broader Android test infrastructure than this module currently has, skip creating the datasource test and note that the repository-level test will carry coverage for this feature.

- [ ] **Step 3: Run the narrowest meaningful verification**

Run:

```bash
./gradlew :feature:identification:compileDebugKotlin
```

If you added a unit test and it is runnable in this module:

```bash
./gradlew :feature:identification:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit the datasource change**

```bash
git add feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/data/IdentificationFaceDataSource.kt \
  feature/identification/src/test/kotlin/com/ytone/longcare/features/identification/data/IdentificationFaceDataSourceTest.kt
git commit -m "feat(face-cache): add clear API for local face cache"
```

If no datasource test file was created, omit it from the commit command.

## Task 2: Clear Face Cache During Logout

**Files:**
- Modify: `core/data/src/main/kotlin/com/ytone/longcare/data/repository/ProfileRepositoryImpl.kt`
- Create: `core/data/src/test/kotlin/com/ytone/longcare/data/repository/ProfileRepositoryImplTest.kt`

- [ ] **Step 1: Inject the face cache datasource into `ProfileRepositoryImpl`**

Update the constructor:

```kotlin
class ProfileRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
    private val userSessionRepository: UserSessionRepository,
    private val identificationFaceDataSource: IdentificationFaceDataSource,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val eventBus: AppEventBus
) : ProfileRepository
```

- [ ] **Step 2: Capture current user id before clearing session**

Use the current session snapshot before logout:

```kotlin
val currentUserId = userSessionRepository.sessionState.value.user?.userId
```

Do this before `userSessionRepository.logout()`.

- [ ] **Step 3: Clear face cache before clearing the session**

Update `logout()` to:

```kotlin
override suspend fun logout(): ApiResult<Unit> {
    val currentUserId = userSessionRepository.sessionState.value.user?.userId
    val result = safeApiCall(ioDispatcher, eventBus) { apiService.logout() }

    currentUserId?.let { userId ->
        identificationFaceDataSource.clearUserFaceBase64(userId)
    }

    userSessionRepository.logout()
    return result
}
```

This preserves the required order:

1. capture user id
2. perform best-effort face cache cleanup
3. clear session

- [ ] **Step 4: Add a repository test for logout cleanup order**

Create a focused unit test with a fake or mocked `UserSessionRepository` and a mocked `IdentificationFaceDataSource` to verify:

```kotlin
- when sessionState is LoggedIn(userId = 123)
- logout() calls identificationFaceDataSource.clearUserFaceBase64(123)
- logout() calls userSessionRepository.logout()
- logout() still returns the API result
```

Also add a no-user case:

```kotlin
- when sessionState is LoggedOut
- logout() does not call clearUserFaceBase64(...)
- logout() still calls userSessionRepository.logout()
```

- [ ] **Step 5: Run repository verification**

Run:

```bash
./gradlew :core:data:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit the logout integration**

```bash
git add core/data/src/main/kotlin/com/ytone/longcare/data/repository/ProfileRepositoryImpl.kt \
  core/data/src/test/kotlin/com/ytone/longcare/data/repository/ProfileRepositoryImplTest.kt
git commit -m "fix(logout): clear local face cache on sign out"
```

## Task 3: Regression Verification for Existing Face Fetch Order

**Files:**
- Modify: none unless a missing test is needed

- [ ] **Step 1: Re-read the current service-person verification decision order**

Confirm that `VerifyServicePersonUseCase.execute(...)` still follows:

```text
cached face -> remote face -> require face setup
```

The relevant file is:

```text
feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/domain/VerifyServicePersonUseCase.kt
```

- [ ] **Step 2: Add a narrow regression test only if coverage is missing**

If there is no existing test protecting the decision order, add one in the identification feature test suite that verifies:

```kotlin
- cached face present -> returns UseCachedFace
- cached face missing and remote face exists -> returns DownloadAndCache
- cached face missing and remote face missing -> returns RequireFaceSetup
```

If such a test already exists or equivalent coverage is present, do not duplicate it.

- [ ] **Step 3: Run the narrowest meaningful verification**

Run:

```bash
./gradlew :feature:identification:compileDebugKotlin :core:data:compileDebugKotlin
```

If you added a regression test:

```bash
./gradlew :feature:identification:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit only if you added a regression test**

```bash
git add feature/identification/src/test/kotlin/com/ytone/longcare/features/identification/domain/VerifyServicePersonUseCaseTest.kt
git commit -m "test(face-cache): preserve verification fetch order"
```

Skip this commit if no new test file was needed.

## Task 4: Final Verification

**Files:**
- Modify: none

- [ ] **Step 1: Run a full compile pass for touched modules**

Run:

```bash
./gradlew :feature:identification:compileDebugKotlin :core:data:compileDebugKotlin :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Run module unit tests that cover the logout/cache behavior**

Run:

```bash
./gradlew :core:data:testDebugUnitTest
```

If you added identification-side tests:

```bash
./gradlew :feature:identification:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Manual behavioral checklist**

Validate the behavior with a logged-in account that has face cache data:

```text
1. Trigger service-person face verification once so local face cache exists.
2. Log out.
3. Log back in.
4. Trigger service-person face verification again.
5. Confirm stale local face cache is not reused from the previous session.
6. Confirm the existing fallback logic still works (cache miss -> remote fetch / setup requirement).
```

- [ ] **Step 4: Commit only if final verification required small follow-up changes**

```bash
git status --short
git add <only-follow-up-files-if-needed>
git commit -m "fix(face-cache): polish logout cache cleanup"
```

Skip this step if verification required no code changes.

## Self-Review

- **Spec coverage:** The plan covers datasource deletion support, logout integration, current-user capture timing, best-effort cleanup, and regression protection for the existing verification fetch order.
- **Placeholder scan:** No `TODO`, `TBD`, or vague “handle appropriately” instructions remain.
- **Type consistency:** The plan consistently uses `clearUserFaceBase64(userId: Int)` as the local cache cleanup API and keeps `ProfileRepositoryImpl.logout()` as the logout integration point.
