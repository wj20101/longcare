# Face Cache Clear On Logout Design

## Context

The project currently keeps local face-related data in at least one persistent cache path:

- `IdentificationFaceDataSource` stores a per-user face base64 value in DataStore under a key derived from `userId`

That cache is actively used by the service-person verification flow:

- verify service person
- prefer local cached face
- if no local cached face exists, fetch remote face information
- cache the result locally again

The logout flow currently clears only the user session. It does not clear the local face cache.

This means a user can log out while stale local face data remains on the device.

## Goal

Clear local persistent face cache on logout, while keeping the current login-time and verification-time face fetch logic unchanged.

## Non-Goals

This design does not include:

- refactoring all face flows into a new cache manager
- changing how face verification works during a logged-in session
- changing remote `getFace()` behavior
- clearing unrelated order, image, or general-purpose app caches
- redesigning the face verification UI

## Approved Direction

Use the existing logout chain as the integration point.

Recommended path:

- read current logged-in user before session is cleared
- clear the persistent face cache for that `userId`
- then clear the user session

This is preferred over broader refactors because it solves the actual problem with minimal risk and does not disturb the current verification flow.

## Design

### 1. What Must Be Cleared

The logout flow must clear the persistent per-user face cache maintained by `IdentificationFaceDataSource`.

That means adding a focused cache deletion API, for example:

- `clearUserFaceBase64(userId: Int)`

The target is the persisted face base64 cache keyed by user id.

### 2. What Must Not Be Changed

The existing verification decision order must remain:

1. read local face cache
2. if missing, fetch remote face source
3. if remote face exists, cache it locally again
4. if remote face does not exist, require face setup

This ensures that after logout and next login, the app behaves exactly as it does today, except the stale local face cache is gone and the remote fetch path is exercised again when needed.

### 3. Logout Integration Point

The cleanup should happen inside the logout chain closest to data responsibilities, not only inside a UI ViewModel.

Preferred integration point:

- `ProfileRepositoryImpl.logout()`

because it already orchestrates:

- remote logout call
- session clear

The repository can obtain the current user id before logout, clear the local face cache for that user, and then clear the session.

This also helps cover both:

- user-initiated logout
- any logout path that reuses the same repository logic

### 4. User Identity Timing

Because the face cache is keyed by `userId`, the implementation must capture the current logged-in user id before the session is removed.

The ordering requirement is:

1. resolve current user id from session
2. clear local face cache for that user
3. clear session

If no logged-in user is present, cache clearing can safely no-op.

### 5. Page-Local Temporary State

`FaceVerificationWithAutoSignScreen` also holds a temporary in-memory `sourcePhotoBase64` via Compose `remember`.

That is not the primary target of this requirement, because it is not the persistent local cache path that survives logout in storage. It should not expand the scope of the logout cleanup design.

If the screen leaves composition during logout, that local state naturally disappears. No extra design work is required here.

### 6. Failure Handling

Local face cache cleanup should be best-effort and should not block session logout indefinitely.

Desired behavior:

- if cache deletion succeeds, continue logout normally
- if cache deletion fails, log the failure
- logout should still complete so the user is not trapped in session state

This keeps logout resilient while still prioritizing cleanup.

## File Targets

Expected implementation focus:

- `feature/identification/src/main/kotlin/com/ytone/longcare/features/identification/data/IdentificationFaceDataSource.kt`
- `core/data/src/main/kotlin/com/ytone/longcare/data/repository/ProfileRepositoryImpl.kt`

Likely supporting files:

- `core/domain/src/main/kotlin/com/ytone/longcare/domain/profile/ProfileRepository.kt` only if a contract change becomes necessary
- any session-access helper file, only if needed for current user resolution

## Acceptance Criteria

1. Logging out clears the persistent per-user local face cache.
2. Logging in and re-entering service-person face verification still uses the existing decision order.
3. After logout, stale local face cache is not reused on the next login.
4. Logout still completes even if local cache cleanup fails.
5. No unrelated cache or verification logic is changed.

## Verification Strategy

Implementation should verify at least:

- local compile/test coverage for the modified repository and datasource code
- a targeted check that the cache entry exists before logout and is gone after logout
- a follow-up verification that the next login falls back to remote fetch when no local cache exists

## Risks and Controls

### Risk: Clearing Cache Too Late

If session is cleared before current user id is captured, the app may lose the ability to identify which cached face entry to delete.

Control:

- resolve current user id before session removal

### Risk: Over-Clearing Face Data

If cache cleanup is implemented too broadly, it may remove unrelated data or affect users beyond the current session.

Control:

- delete only the face cache entry for the current `userId`

### Risk: Breaking Existing Verification Flow

If the implementation changes cache read order or remote fetch fallback behavior, users may see new verification regressions.

Control:

- keep verification decision order unchanged
- limit implementation scope strictly to logout-time cache cleanup

## Rationale

This design solves the actual data retention problem at the right layer: persistent local face cache should not survive logout. It keeps the proven login-time and verification-time fetch logic intact, minimizes behavioral risk, and avoids unnecessary architectural expansion.
