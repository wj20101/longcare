# Permission Resume Flow Fix Design

Date: 2026-04-27

## Context

After the Huawei compliance permission change, two user-triggered flows regress:

- On the identity verification page, tapping the elder "拍照验证" button shows the camera purpose dialog. After tapping "继续", the app navigates to a blank camera page. Restarting the app does not recover this flow.
- On start-order NFC sign-in, after location permission is granted, the device vibrates when a card is scanned but the flow does not advance and no useful prompt is shown. After backgrounding or restarting the app, scanning again can enter the next step.

Both issues come from permission handling that satisfies compliance requirements but does not preserve the user action that caused the permission request.

## Goals

- Keep permission requests user-triggered and preceded by an app purpose dialog.
- Make the elder photo verification camera open immediately when camera permission is already granted.
- Make the camera screen render the camera content when system camera permission is already granted, including after app restart.
- Resume the same NFC scan after location permission is granted instead of requiring the user to scan again.
- Show a clear prompt when the user denies required permission or location service is disabled.

## Non-Goals

- Do not redesign the identity verification UI.
- Do not change business API behavior for order start or location binding.
- Do not add a broad permission framework refactor.
- Do not change unrelated photo upload or face SDK behavior beyond the shared camera gate bug.

## Recommended Approach

Use focused state fixes at the affected entry points:

1. Add a local helper in identity verification to continue elder photo capture only after checking current camera permission.
2. Initialize `CameraPermissionGate` from the system camera permission instead of assuming `false`.
3. Store a pending NFC scan when location permission is missing, request permission through the existing purpose dialog, then replay the pending scan after permission is granted.

This is smaller and safer than a shared permission framework rewrite while still fixing both user-visible regressions.

## Identity Verification Camera Flow

When the user taps elder "拍照验证":

1. Check `Manifest.permission.CAMERA` with `ContextCompat.checkSelfPermission`.
2. If granted, generate the watermark data and navigate to the camera route.
3. If missing, show the existing "相机权限说明" dialog.
4. On dialog confirmation, launch the system camera permission request.
5. On permission result:
   - granted: generate watermark data and navigate to the camera route.
   - denied: show "需要相机权限才能拍照".

The camera route must also protect itself:

1. `CameraPermissionGate` initializes `hasPermission` from the current system camera permission.
2. When the system grants permission from inside the camera route, update `hasPermission` to true.
3. If permission is denied, keep the current request UI and purpose dialog available.

This prevents the blank camera page after permission has already been granted.

## NFC Location Permission Resume Flow

When a card is scanned:

1. If location permission exists and location service is enabled, continue the current flow.
2. If location service is disabled, show the existing location settings prompt and keep the user on the NFC page.
3. If location permission is missing, store the scanned card and current order context in a pending scan state, then show the existing location purpose dialog.
4. On permission granted, consume the pending scan and continue the same flow:
   - fetch current coordinates;
   - run the existing start-order or location binding decision;
   - navigate to identity verification after start-order success, as today.
5. On permission denied, clear the pending scan and show a clear location permission message.

The pending scan is in-memory UI state because it only needs to bridge the system permission dialog return. It should not survive process death.

## Error Handling

- Camera permission denied: show the existing short toast and stay on identity verification or camera permission UI.
- Camera unavailable despite permission: keep current camera screen behavior.
- Location permission denied: show "需要定位权限才能开始服务".
- Location service disabled: use the existing settings intent prompt.
- Location fetch failure after permission grant: show the existing NFC error state instead of silently staying idle.

## Testing

Add or update focused tests where practical:

- A permission policy test that `CameraPermissionGate` reads current system permission and does not only rely on local `remember { false }`.
- A test or small pure-function coverage for NFC pending scan behavior if the pending state is extracted into a helper.
- Existing NFC and identity verification tests should continue to pass.

Manual smoke checks:

- Fresh install: identity verification -> elder photo -> purpose dialog -> grant camera -> camera preview opens.
- App restart with camera already granted: identity verification -> elder photo -> camera preview opens without blank page.
- Fresh install: start-order NFC -> scan card -> grant location -> same scan continues into the next step.
- Deny location: user sees a permission message and can retry.
