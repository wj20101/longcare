# Huawei Compliance And Location Quality Implementation Plan

> **For agentic workers:** Implement task-by-task. Keep changes small, preserve current service-flow behavior, and do not stage unrelated worktree changes.

**Goal:** Address Huawei AppGallery privacy/permission rejection while improving location-quality handling. Privacy agreement must gate all pre-login network activity and ANDROID ID access. Camera and location permissions must be requested only after user-triggered feature actions. Critical service flows must continue when location fails, but users must be warned and the issue must be reported through Bugly.

**Design:** `/Users/wajie/StudioProjects/longcare/docs/superpowers/specs/2026-04-26-huawei-compliance-location-design.md`

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, StateFlow, OkHttp interceptor, AMap location SDK, Bugly tracking, JUnit/MockK where practical.

---

## File Responsibility Map

- Modify: `/Users/wajie/StudioProjects/longcare/feature/login/src/main/kotlin/com/ytone/longcare/features/login/vm/LoginViewModel.kt`
  Purpose: remove startup-config loading from `init`, expose privacy-confirmed loading, and gate send-code/login.

- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt`
  Purpose: trigger startup-config loading only after privacy agreement, keep login/send-code actions behind consent, and preserve current agreement UI.

- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt`
  Purpose: remove home-entry camera/location permission request.

- Modify or remove: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreenPermissions.kt`
  Purpose: retire the old home-entry permission list if no longer used.

- Modify: `/Users/wajie/StudioProjects/longcare/core/ui/src/main/kotlin/com/ytone/longcare/common/utils/UnifiedPermissionHelper.kt`
  Purpose: support user-triggered location permission flows without auto-retry behavior.

- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreenHandlers.kt`
  Purpose: show location purpose notice before permission requests and surface degraded-location warnings.

- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegate.kt`
  Purpose: use critical-location behavior that reports degraded results instead of silently returning empty coordinates.

- Modify: `/Users/wajie/StudioProjects/longcare/core/ui/src/main/kotlin/com/ytone/longcare/shared/vm/SharedOrderDetailViewModel.kt`
  Purpose: allow service start to continue when location is degraded, but expose warning state and report to Bugly.

- Modify: `/Users/wajie/StudioProjects/longcare/core/model/src/main/kotlin/com/ytone/longcare/model/LocationResult.kt`
  Purpose: add timestamp or equivalent metadata if needed for explicit freshness checks.

- Modify: `/Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationStateManager.kt`
  Purpose: expose cache age/freshness safely for quality evaluation.

- Modify: `/Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacade.kt`
  Purpose: provide fresh-location-first behavior for critical actions.

- Modify or create under `/Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/`
  Purpose: add a small location quality evaluator and Bugly tracking helpers if existing event types are not enough.

- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsComponents.kt`
  Purpose: hide 信息上报, 个人信息, and 设置 while they have no implemented behavior.

- Update tests under `/Users/wajie/StudioProjects/longcare/app/src/test`, `/Users/wajie/StudioProjects/longcare/feature/location/src/test`, or existing module test folders as appropriate.

## Task 1: Gate Login Network Requests Behind Privacy Agreement

- [ ] Remove automatic `loadStartConfig()` from `LoginViewModel.init`.
- [ ] Add an idempotent `onPrivacyAgreementConfirmed()` or `loadStartConfigAfterAgreement()` API in `LoginViewModel`.
- [ ] Ensure `sendSmsCode(...)` and `login(...)` do not run unless privacy is confirmed.
- [ ] Trigger startup-config loading from `LoginScreen` only after the checkbox/dialog confirmation path.
- [ ] Keep agreement URL behavior usable before remote config loads by using existing fallback toast/copy or local fallback URLs if available.
- [ ] Add or update tests proving `LoginViewModel` does not call `getStartConfig()` at construction time.

**Acceptance:**

- Fresh launch on login page with privacy unchecked sends no API request.
- ANDROID ID is not read before privacy confirmation because the interceptor is not invoked.
- After privacy confirmation, startup config, SMS, and login continue to work.

## Task 2: Remove Home-Entry Camera And Location Permission Requests

- [ ] Remove the `LaunchedEffect(Unit)` block in `HomeScreen` that builds missing permissions and launches `permissionLauncher`.
- [ ] Remove unused camera/location permission launcher state from `HomeScreen`.
- [ ] Keep `homeSharedViewModel.reportHomeEntry()` and compatibility guides that do not request camera/location runtime permissions.
- [ ] Delete or narrow `HomeScreenPermissions.kt` if no longer used.

**Acceptance:**

- First home entry does not show camera or location system permission dialogs.
- Denying permissions and relaunching the app does not auto-request them again.
- Existing non-camera/location compatibility guidance remains intact if still required.

## Task 3: Add User-Triggered Permission Purpose Notices

- [ ] Identify camera entry points: identification, face capture/setup, photo upload/camera route.
- [ ] Identify location entry points: NFC sign-in, start service, service countdown/location reporting.
- [ ] Before system permission launch, show an app dialog naming the permission, related feature, and usage purpose.
- [ ] Launch Android system permission only after the user confirms the purpose dialog.
- [ ] If the user cancels the purpose dialog or denies the system permission, do not auto-retry until the next explicit feature action.

**Acceptance:**

- Permission requests are tied to user actions.
- Purpose notice does not auto-disappear.
- Huawei rejection items 3, 4, and 5 are covered.

## Task 4: Add Location Quality Evaluation And Bugly Reporting

- [ ] Add explicit freshness metadata to location handling, either on `LocationResult` or via a wrapper returned by the location module.
- [ ] Add a small evaluator for invalid coordinates, stale cache, missing accuracy, low accuracy, timeout, and provider failure.
- [ ] Prefer fresh realtime location for NFC sign-in and service start.
- [ ] Allow cache fallback only when it is recent and accuracy is acceptable.
- [ ] Report degraded/missing/invalid location cases through `LocationEventTracker` so Bugly receives them.
- [ ] Add unit tests for valid, zero-coordinate, out-of-range, stale, and low-accuracy cases.

**Acceptance:**

- Critical actions can distinguish fresh, cached, degraded, and missing location.
- Bugly receives a tracked event for degraded or missing critical location.
- Existing continuous location reporting still works.

## Task 5: Continue Business Flow With User Warning When Location Fails

- [ ] Update NFC sign-in flow so location failure or degradation produces a warning dialog and still allows the action to proceed.
- [ ] Update service-start flow so empty or degraded coordinates do not silently continue; show a warning while preserving continuation.
- [ ] Ensure warning copy is clear: location may be inaccurate/unavailable, but the service action will continue.
- [ ] Pass best available coordinates when valid; otherwise pass empty coordinates as current business behavior allows.

**Acceptance:**

- Location failure does not block care service execution.
- User sees a warning before or during continuation.
- The issue is reported through Bugly.

## Task 6: Hide Unimplemented Profile Options

- [ ] Update `ProfileOptionsComponents` to hide 信息上报, 个人信息, and 设置 while they have no destination or behavior.
- [ ] Remove unused actions from `ProfileActions` only if they become unused after the UI change.
- [ ] Keep user info, stats, logout, and version display unchanged.

**Acceptance:**

- Profile page no longer shows clickable rows that do nothing.
- Huawei item 6 is addressed by removing the non-functional UI surface.

## Task 7: Validation

- [ ] Run targeted unit tests for login privacy gating and location quality evaluator.
- [ ] Run `./gradlew :app:testDebugUnitTest` if feasible.
- [ ] Run `./gradlew :app:lintDebug` if feasible.
- [ ] Manually verify the Huawei regression checklist from the design doc.
- [ ] Confirm no unrelated files are staged.

## Risk Controls

- Do not change request encryption or interceptor behavior unless tests show it is necessary.
- Do not remove existing location keep-alive or upload queue behavior.
- Do not block service execution because of location failure.
- Keep privacy gating local and explicit before considering a global privacy center.
- Keep the Profile-page change minimal: hide only the unimplemented entries.
