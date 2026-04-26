# Huawei Review Fix Implementation Plan

Date: 2026-04-26

## Goal

Fix the Huawei AppGallery rejection items with the smallest client-side change set needed for review. This plan only covers the six rejection points in `huawei.log`.

## Scope

In scope:

- Prevent client-side API requests and `ANDROID ID` reads before the user agrees to the privacy policy.
- Remove automatic camera and location permission requests from first home entry.
- Show a persistent, app-controlled purpose notice before camera or location permission requests.
- Ensure denied camera/location permissions are not requested again on app restart or passive page entry.
- Hide the Profile page entries that currently have no response: 信息上报, 个人信息, 设置.
- Add focused tests and manual review steps that map to Huawei's rejection flow.

Out of scope:

- Hosted privacy policy page content and AppGallery Connect privacy policy URL updates. These are online/backend release tasks, not client code tasks.
- Location accuracy optimization, critical-location quality fallback, and Bugly location quality reporting.
- Redesigning the Profile page or implementing new destinations for hidden entries.
- Adding new backend APIs.

## Rejection Mapping

| Huawei item | Client fix |
| --- | --- |
| 1. `ANDROID ID` collected before privacy agreement | Gate all startup/login API calls behind explicit privacy agreement, so request interceptors cannot read `ANDROID ID` early. |
| 2. Privacy policy missing `ANDROID ID` disclosure | No client code change. Update the hosted online privacy policy and AppGallery Connect entry outside this plan. |
| 3. Camera/location permission requested without synchronized purpose notice | Add app-controlled purpose notices before every camera/location system permission request. |
| 4. Camera/location permission requested before active feature use | Remove home-entry permission requests. Keep permission requests only inside user-triggered feature actions. |
| 5. Denied camera/location permission re-requested after relaunch | Remove passive permission retry paths. Relaunch/home entry must not trigger camera/location permission dialogs. |
| 6. Profile entries have no response | Hide 信息上报, 个人信息, 设置 until real behavior exists. |

## Architecture

Keep the fix local to existing feature boundaries:

- Login feature owns privacy agreement gating for startup config, send-code, and login.
- Home feature stops requesting camera/location permissions on composition or relaunch.
- Shared UI permission helpers provide reusable camera/location purpose copy and launch sequencing.
- Camera/location feature entry screens call the purpose notice first, then launch the Android permission dialog only after user confirmation.
- Profile feature hides unimplemented rows instead of leaving visible no-op click targets.

The plan avoids a global privacy center rewrite. The key rule is simple: no client operation that may collect personal information, request sensitive permission, or read device identifiers runs until a user action makes it valid.

## Task 1: Gate Login API Calls Behind Privacy Agreement

**Huawei items:** 1

**Files to inspect/modify:**

- `feature/login/src/main/kotlin/com/ytone/longcare/features/login/vm/LoginViewModel.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreenComponents.kt`
- `core/data/src/main/kotlin/com/ytone/longcare/network/interceptor/RequestInterceptor.kt`
- `core/common/src/main/kotlin/com/ytone/longcare/common/utils/DeviceUtils.kt`

**Implementation steps:**

- Remove `loadStartConfig()` from `LoginViewModel.init`.
- Add explicit privacy-confirmed state in `LoginViewModel`.
- Add `onPrivacyAgreementConfirmed()` and load startup config only once after the user checks/confirms privacy agreement.
- Block `sendSmsCode()` and `login()` when privacy is not confirmed, showing a clear toast such as `请先阅读并同意用户协议和隐私政策`.
- Keep local fallback behavior for agreement links before startup config is loaded.
- Do not change `RequestInterceptor` or `DeviceUtils` unless testing reveals another pre-consent caller. The primary client fix is to prevent pre-consent network calls from reaching the interceptor.

**Tests:**

- Add a ViewModel unit test proving construction does not call `LoginRepository.getStartConfig()`.
- Add a ViewModel unit test proving `onPrivacyAgreementConfirmed()` calls `getStartConfig()` once even if invoked repeatedly.
- Add tests proving send-code/login do not call repository methods before agreement.

**Manual verification:**

- Fresh install, open login page, do not check privacy: no startup config request is sent.
- Tap send-code without privacy agreement: only privacy prompt/toast appears, no API request is sent.
- Confirm privacy: startup config may load; later send-code/login may call APIs.

## Task 2: Track Online Privacy Policy Update As External Release Work

**Huawei items:** 2

**Client behavior:**

- No Android client code change is required for this item.

**External release requirement:**

- Update the hosted online privacy policy page.
- Update the privacy policy URL/content submitted in AppGallery Connect if needed.
- The online policy must describe `ANDROID ID` collection purpose, method, and scope.

**Plan handling:**

- Add this as a release checklist item, not an implementation task.
- During final verification, confirm the client opens the expected online privacy policy URL after privacy agreement links become available.

## Task 3: Remove Home-Entry Camera And Location Permission Requests

**Huawei items:** 3, 4, 5

**Files to inspect/modify:**

- `app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreenPermissions.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreenPermissionDialogs.kt`

**Implementation steps:**

- Remove the `RequestMultiplePermissions` launcher used for first home entry camera/location permission requests.
- Remove `buildRequiredPermissions()` usage from `HomeScreen`.
- Delete `HomeScreenPermissions.kt` if no longer referenced.
- Keep existing overlay, popup, battery, and manufacturer compatibility guidance if those do not request camera/location permissions.
- Ensure `HomeScreen` still reports home entry and refreshes compatibility guidance.

**Tests:**

- Add a static policy test for `HomeScreen.kt` that fails if it contains:
  - `buildRequiredPermissions()`
  - `ActivityResultContracts.RequestMultiplePermissions`
  - `Manifest.permission.CAMERA`
  - `Manifest.permission.ACCESS_FINE_LOCATION`

**Manual verification:**

- Fresh install and enter home: no camera permission dialog appears.
- Fresh install and enter home: no location permission dialog appears.
- Deny camera/location from a feature action, kill app, relaunch, enter home: no camera/location permission dialog appears.

## Task 4: Add Purpose Notices Before Camera And Location Permission Requests

**Huawei items:** 3, 4

**Files to inspect/modify:**

- `core/ui/src/main/kotlin/com/ytone/longcare/common/utils/UnifiedPermissionHelper.kt`
- Camera entry points under:
  - `app/src/main/kotlin/com/ytone/longcare/features/photoupload/`
  - `app/src/main/kotlin/com/ytone/longcare/features/face/`
  - `app/src/main/kotlin/com/ytone/longcare/features/facecapture/`
  - `app/src/main/kotlin/com/ytone/longcare/features/identification/`
- Location entry points under:
  - `app/src/main/kotlin/com/ytone/longcare/features/nfc/`
  - `core/ui/src/main/kotlin/com/ytone/longcare/shared/vm/SharedOrderDetailViewModel.kt`
  - Other feature screens found by searching for `ACCESS_FINE_LOCATION`

**Implementation steps:**

- Add reusable permission purpose data/copy for camera and location.
- Before launching a system permission dialog, show an app-controlled dialog that does not auto-dismiss.
- The notice must include:
  - permission name, such as `相机权限` or `定位权限`
  - specific feature, such as service photo capture, face verification, NFC sign-in, or service start
  - purpose, such as taking service/verification photos or recording service location
- Launch the Android system permission dialog only after the user taps confirm in the purpose notice.
- If the user cancels the purpose notice, do not launch the system permission dialog.
- Keep permission requests attached to user-triggered actions only.

**Suggested copy:**

- Camera: `需要相机权限用于拍摄服务照片或进行人脸核验。本权限仅在您使用拍照、上传或核验功能时申请。`
- Location: `需要定位权限用于记录到岗位置和服务位置。本权限仅在您进行NFC签到、开始服务或提交服务位置时申请。`

**Tests:**

- Add lightweight unit/static tests for permission purpose copy to ensure it includes permission name, feature, and purpose.
- Add static policy tests for direct permission launch call sites where practical, ensuring they are routed through a purpose notice path.

**Manual verification:**

- Trigger a camera feature: app purpose notice appears first; Android permission dialog appears only after confirm.
- Trigger a location feature: app purpose notice appears first; Android permission dialog appears only after confirm.
- Cancel the purpose notice: Android permission dialog does not appear.

## Task 5: Prevent Passive Re-Requests After Permission Denial

**Huawei items:** 5

**Files to inspect/modify:**

- Same permission entry files from Task 3 and Task 4.

**Implementation steps:**

- Search for all camera/location permission launchers.
- Remove any launcher invocation from `LaunchedEffect(Unit)`, screen composition, lifecycle resume, app startup, or home entry.
- Keep retries only behind explicit user clicks on the related feature action.
- Where a permission has been denied, show guidance in the feature flow instead of automatically launching again.

**Tests:**

- Extend static policy tests to detect camera/location permission launches from home entry.
- Add targeted tests for helper functions where permission launch is represented by callbacks.

**Manual verification:**

- Deny camera permission from a camera feature, kill app, relaunch: no camera permission dialog appears until the user taps a camera feature again.
- Deny location permission from a location feature, kill app, relaunch: no location permission dialog appears until the user taps a location feature again.

## Task 6: Hide Profile Entries With No Implemented Behavior

**Huawei items:** 6

**Files to inspect/modify:**

- `app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsComponents.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/profile/api/ProfileActions.kt`
- Profile previews/tests that reference `OptionsCard`

**Implementation steps:**

- Hide the rows labeled:
  - `信息上报`
  - `个人信息`
  - `设置`
- Do not leave visible clickable rows with empty lambdas.
- Prefer making `OptionsCard()` render no UI or removing its call from `ProfileScreen`, depending on which keeps previews/tests cleanest.
- Leave logout and existing implemented Profile behavior unchanged.

**Tests:**

- Add a static policy test proving `ProfileOptionsComponents.kt` no longer exposes the three labels with empty click handlers.
- Update previews/tests if they assumed the options card was visible.

**Manual verification:**

- Open Profile page: 信息上报, 个人信息, 设置 are not visible.
- Profile page layout remains stable; implemented controls still work.

## Task 7: Final Review Verification

**Automated checks:**

- Run focused unit/static tests added in Tasks 1, 3, 4, and 6.
- Run relevant existing app unit tests if time allows.
- Run lint if the project state supports it.

**Manual Huawei-flow checklist:**

1. Fresh install, open app to login page.
2. Before privacy agreement, confirm no API request is sent and no `ANDROID ID` path is reached.
3. Tap send-code before privacy agreement: request is blocked.
4. Agree to privacy policy: login-related APIs may proceed.
5. Enter home page for the first time: no camera/location permission request appears.
6. Trigger camera feature: persistent purpose notice appears before Android permission dialog.
7. Trigger location feature: persistent purpose notice appears before Android permission dialog.
8. Deny camera/location permission, kill app, relaunch, enter home: no automatic camera/location permission request appears.
9. Open Profile page: 信息上报, 个人信息, 设置 are hidden.
10. Confirm the hosted privacy policy and AppGallery Connect policy entry have been updated outside the Android client release.

## Commit Strategy

Use small commits aligned to the review items:

1. `fix(login): gate requests behind privacy agreement`
2. `fix(home): stop requesting camera and location on entry`
3. `fix(permissions): add purpose notices before sensitive requests`
4. `fix(profile): hide unimplemented option entries`
5. `test(review): cover Huawei compliance flows`

Keep unrelated local changes out of these commits.
