# Huawei Compliance And Location Quality Design

Date: 2026-04-26

## Context

Huawei AppGallery rejected the app for privacy and permission behavior. The rejection notes cover:

- Reading ANDROID ID before the user agrees to the privacy policy.
- Privacy policy text missing ANDROID ID collection details.
- Requesting camera and location permissions on first home entry without a synchronized purpose notice.
- Requesting camera and location before the user actively uses the related feature.
- Re-requesting camera and location after denial when the app is relaunched.
- Empty taps on the Profile page entries: 信息上报, 个人信息, 设置.

Users have also reported location accuracy problems. The compliance fix must avoid making location startup, accuracy, or reporting less reliable.

## Goals

- Do not send any network request before the user confirms the privacy agreement.
- Do not read ANDROID ID before privacy agreement confirmation.
- Do not initialize location SDK behavior before privacy agreement confirmation.
- Request camera and location permissions only after a user-triggered feature action.
- Show a non-auto-dismissing purpose notice before system permission requests.
- Preserve service flow continuity when location fails.
- Notify the user when location is missing, stale, invalid, or low quality.
- Report location quality problems through the existing Bugly-based tracking path.
- Make Profile page option taps visibly respond.

## Non-Goals

- Do not add a new business backend endpoint for location quality reports.
- Do not block care service execution solely because location failed.
- Do not redesign the Profile page.
- Do not migrate route-bound UI between modules.
- Do not solve Huawei policy copy hosting in code beyond supporting the required behavior. The privacy policy URL/content must still be updated in AppGallery Connect and the hosted privacy policy page.

## Current Findings

### Privacy And ANDROID ID

`LoginViewModel` currently loads startup config from `init`. That request goes through `RequestInterceptor`, which adds `deviceId` to `BaseParamString`. `deviceId` calls `RequestDeviceInfoProvider.getAppInstanceId()`, which calls `DeviceUtils.getAppInstanceId()`. On first generation, `DeviceUtils` may read `Settings.Secure.ANDROID_ID`.

This means a login-page startup request can trigger ANDROID ID access before the user agrees to privacy terms.

### Permission Requests

`HomeScreen` currently checks `buildRequiredPermissions()` and launches the permission request on first composition. The list includes location and camera. This matches Huawei's rejection notes for early permission requests and re-request after relaunch.

### Location Quality

`DefaultLocationFacade.getCurrentLocation()` first returns a cache from `LocationStateManager` if it is within 30 seconds. That is efficient, but for NFC sign-in and service start it can use a recent-but-not-current location.

`LocationResult` currently contains latitude, longitude, provider, and accuracy. It does not expose a timestamp to callers. Location quality rules are therefore spread across state/cache behavior rather than being explicit at the call site.

`SharedOrderDetailViewModel` allows starting an order with empty longitude and latitude when location is unavailable. This preserves the workflow, but today it can happen without a user-facing warning or a location quality report.

### Profile Page

`ProfileOptionsComponents.OptionsCard` wires 信息上报, 个人信息, and 设置 to empty lambdas, so taps visibly do nothing.

## Recommended Approach

Use a focused compliance and location-quality update:

1. Gate login-page network activity behind explicit privacy agreement.
2. Remove home-entry camera/location permission requests.
3. Add user-triggered permission purpose prompts for camera and location entry points.
4. Add location-quality evaluation for critical location actions.
5. Allow the business action to continue when location is unreliable, but show a warning dialog and report the issue through Bugly.
6. Add visible responses for Profile page option taps.

This is smaller than a global privacy center rewrite and safer than only moving permission prompts.

## UX Behavior

### Login

Before the privacy checkbox is confirmed:

- The app does not call startup config, send SMS code, login, or any other API.
- The app does not read ANDROID ID.
- Privacy and user agreement links use local fallback URLs or a local fallback message if remote URLs have not been loaded.

After the user confirms the privacy agreement:

- The app may load startup config.
- The app may send SMS and login.
- Network headers may include `deviceId`.

### Permission Purpose Notices

Before requesting a permission, show an app-controlled dialog that does not disappear automatically.

Camera notice must include:

- Permission name: 相机权限.
- Feature: photo capture, face setup, or face verification depending on the entry.
- Purpose: take service or verification photos.

Location notice must include:

- Permission name: 定位权限.
- Feature: start service, NFC sign-in, or service location reporting.
- Purpose: record service location and support service compliance.

Only the user's confirmation launches the Android system permission dialog. If the user cancels, do not launch the system permission dialog.

### Location Failure Or Low Quality

For NFC sign-in and service start:

- Try to obtain a fresh location first.
- If fresh location fails, allow a short-lived, quality-checked cache fallback.
- If location is unavailable, stale, invalid, or low quality, continue the business action.
- Show a dialog explaining that location may be inaccurate or unavailable and that the action will continue.
- Track the issue through `LocationEventTracker` so Bugly receives it.

The warning should be user-facing but not blocking after acknowledgment.

## Location Quality Rules

Introduce a small location quality evaluation layer near `LocationFacade` consumers or inside the location module.

Minimum rules:

- Latitude and longitude must not both be `0.0`.
- Latitude must be in `-90..90`.
- Longitude must be in `-180..180`.
- Accuracy must be positive when provided.
- Fresh realtime location is preferred for NFC sign-in and service start.
- Cache fallback is allowed only when it is very recent and accuracy is acceptable.

Recommended thresholds:

- Critical action cache max age: 10 seconds.
- Acceptable accuracy for critical fallback: 100 meters or better.
- Continuous reporting can keep its current interval, but invalid points should be tracked and skipped or marked according to implementation risk.

## Data Flow

### Login Privacy Gate

1. Login screen starts with privacy unchecked.
2. No API request is made.
3. User checks privacy agreement or confirms the agreement dialog.
4. Login view model loads startup config.
5. User can send SMS and login.
6. Interceptor can generate `deviceId` only after this point.

### Critical Location Action

1. User taps the feature action.
2. App shows location purpose notice if permission is missing.
3. User confirms.
4. App requests system permission if needed.
5. App checks system location switch.
6. App tries fresh location.
7. App evaluates quality.
8. If reliable, continue with coordinates.
9. If unreliable, show warning, report to Bugly, then continue with best available coordinates or empty coordinates.

## Components

### Login Privacy Gate

Likely files:

- `feature/login/src/main/kotlin/com/ytone/longcare/features/login/vm/LoginViewModel.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreenComponents.kt`

Responsibilities:

- Move startup config loading out of `init`.
- Expose a method that loads config only after privacy agreement.
- Ensure send-code and login actions enforce privacy confirmation.

### Permission Notice Helpers

Likely files:

- `core/ui/src/main/kotlin/com/ytone/longcare/common/utils/UnifiedPermissionHelper.kt`
- Existing screen handler files for NFC, countdown, identification, photo/camera flows.

Responsibilities:

- Keep actual system permission launch user-triggered.
- Add reusable copy or a small helper for permission purpose dialog state.
- Avoid auto-retry on app relaunch after denial.

### Location Quality Evaluation

Likely files:

- `core/model/src/main/kotlin/com/ytone/longcare/model/LocationResult.kt`
- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationStateManager.kt`
- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacade.kt`
- New or existing location helper in `feature/location`.

Responsibilities:

- Make freshness and quality explicit.
- Provide critical-location behavior for NFC sign-in and service start.
- Track failure/degraded cases through `LocationEventTracker`.

### Business Consumers

Likely files:

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreenHandlers.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegate.kt`
- `core/ui/src/main/kotlin/com/ytone/longcare/shared/vm/SharedOrderDetailViewModel.kt`

Responsibilities:

- Continue service flow when location is missing or degraded.
- Show user warnings before or during continuation.
- Pass best available coordinates when available, or empty coordinates when not.

### Profile Option Responses

Likely files:

- `app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsComponents.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/profile/api/ProfileActions.kt`

Responsibilities:

- Replace empty click handlers with visible responses.
- If no real destination exists, show a toast or dialog explaining the entry status.

## Error Handling

- Permission denied: do not immediately re-request on relaunch. Re-request only after a later explicit user action.
- System location disabled: show settings guidance. If the user chooses not to enable it, allow the business flow to continue with a warning when appropriate.
- Location timeout: continue business flow, show warning, report Bugly event.
- Invalid coordinates: continue business flow, show warning, report Bugly event.
- Low accuracy: continue business flow, show warning, report Bugly event.
- Startup config unavailable after privacy agreement: keep login usable with fallback agreement URLs/messages where possible.

## Testing

Manual Huawei regression:

- Fresh install, open login page, do not check privacy: no API request and no ANDROID ID access.
- Tap send code without privacy: show privacy confirmation, no API request until confirmation.
- Confirm privacy: startup config and login APIs can run.
- Login and enter home: no automatic camera/location permission dialog.
- Deny location, kill app, relaunch: no automatic location permission dialog.
- Tap NFC/start service with missing location permission: app shows purpose notice before system permission dialog.
- Deny location from feature action: app warns and allows the flow to continue where business rules allow.
- Disable system location: app warns and allows the flow to continue where business rules allow.
- Simulate location timeout/invalid/low accuracy: app warns and sends Bugly tracking.
- Tap Profile entries 信息上报, 个人信息, 设置: each has visible response.

Automated or local checks:

- Unit test privacy gate so `loadStartConfig()` is not called from `LoginViewModel.init`.
- Unit test location quality evaluator for valid, stale, zero, out-of-range, and low-accuracy locations.
- Unit test profile option wiring if practical.
- Run `./gradlew :app:lintDebug :app:testDebugUnitTest` or the closest targeted tests after implementation.

## Privacy Policy Copy Requirement

The hosted privacy policy and AppGallery Connect privacy policy URL must disclose ANDROID ID collection details:

- Purpose: device identification, request signing/risk control, account/session security, and app operation support.
- Method: read through Android system secure settings after privacy agreement.
- Scope: ANDROID ID/device identifier used by the app backend request header and not collected before privacy agreement.

This document records the engineering behavior. The actual hosted privacy policy page must be updated outside the app code.

## Open Implementation Notes

- Keep unrelated existing worktree changes untouched.
- Prefer small changes around existing screen handler patterns.
- Avoid starting foreground location services before the user-triggered service flow needs location.
- Preserve existing offline location upload behavior.
