# UI And Screen Map

Last verified: 2026-08-14

This document lists route-bound screens and practical ownership in the current codebase.

`MainActivity` is fixed to portrait and hosts all app-owned Compose routes. Third-party SDK Activities
keep their own orientation declarations rather than inheriting an app-wide override.

## 1) Route groups and screens

### Entry routes

- `LoginRoute` -> `LoginScreen` (`:app`, `features/login/ui/LoginScreen.kt`)
- `HomeRoute` -> `HomeScreen` (`:app`, `features/home/ui/HomeScreen.kt`)
  - sales accounts render `SalesExperienceScreen` and its home, customer, registration,
    confirmation, reminder, and assessment pages within the same route
  - sales registration opens the shared `CameraRoute` for watermarked capture and receives
    the resulting URI through the `HomeRoute` saved-state handle

### Service-flow routes

- `ServiceRoute` -> `ServiceHoursScreen` (`:app`)
- `NursingExecutionRoute` -> `NursingExecutionScreen` (`:app`)
- `CarePlansListRoute` -> `ServiceOrdersListScreen` (`:app`)
- `ServiceRecordsListRoute` -> `ServiceOrdersListScreen` (`:app`)
- `NfcSignInRoute` -> `NfcWorkflowScreen` (`:app`)
- `SelectServiceRoute` -> `SelectServiceScreen` (`:app`)
- `PhotoUploadRoute` -> `PhotoUploadScreen` (`:app`)
- `ServiceCountdownRoute` -> `ServiceCountdownScreen` (`:app`)
- `EndServiceSelectionRoute` -> `EndServiceSelectionScreen` (`:app`)
- `ServiceCompleteRoute` -> `ServiceCompleteScreen` (`:app`)

### Support routes

- `TxFaceRoute` -> `FaceVerificationWithAutoSignScreen` (`:app`, legacy compatibility/test route)
- `UserListRoute` -> `UserListScreen` (`:app`)
- `UserServiceRecordRoute` -> `UserServiceRecordScreen` (`:app`)
- `FaceRecognitionGuideRoute` -> `FaceRecognitionGuideScreen` (`:app`)
- `SelectDeviceRoute` -> route/screen exists (`SelectDeviceScreen`, `:app`), but current start-order navigation path bypasses it
- `IdentificationRoute` -> `IdentificationScreen` (`:app`)
- `DefaultFaceVerificationRoute` -> `DefaultFaceVerificationScreen`
  (`:feature:identification`, default service-person order verification page)
- `CameraRoute` -> `CameraScreen` (`:app`)
- `ManualFaceCaptureRoute` -> `ManualFaceCaptureScreen` (`:app`)
- `WebViewRoute` -> `WebViewScreen` (`:app`)

### Non-route modal/UI overlays

- app update modal:
  - `AppUpdateDialog` (`:app`, shown from `MainApp`)

### Hidden validation entry points

- Long-pressing the login logo opens a Material 3 `功能验证` sheet in both Debug and Release builds.
  Release does not expose a launcher icon or normal navigation entry for this sheet.
- `FaceVerificationValidationActivity` (`:app` main source set)
  - accepts an order ID and reuses the production `DefaultFaceVerificationScreen` plus real API path
  - shows the final uploaded JPEG dimensions and compressed byte size after a valid face capture
  - is internal (`exported=false`) in Release; Debug keeps its additional launcher overlay
- `NfcValidationActivity` (`:app` main source set)
  - uses native NFC foreground dispatch on NFC-capable devices and the R65C external-reader input
    path on devices without system NFC
  - is internal (`exported=false`) in both build variants and opens from the hidden sheet
- the same shared sheet provides real shortcuts to the production camera, backup face verification,
  and manual face-capture flows. Mock networking and Debug dependency injection remain excluded from
  Release.

## 2) Route type inventory

Typed routes defined under `navigation/`:

- object routes:
  - `LoginRoute`, `HomeRoute`, `CarePlansListRoute`, `ServiceRecordsListRoute`, `TxFaceRoute`, `ManualFaceCaptureRoute`
- parameterized routes:
  - `ServiceRoute`, `NursingExecutionRoute`, `WebViewRoute`, `SelectServiceRoute`,
    `PhotoUploadRoute`, `FaceRecognitionGuideRoute`, `SelectDeviceRoute`,
    `IdentificationRoute`, `DefaultFaceVerificationRoute`, `UserListRoute`,
    `UserServiceRecordRoute`, `CameraRoute`
- service-flow parameterized routes:
  - `NfcSignInRoute`, `ServiceCountdownRoute`, `ServiceCompleteRoute`, `EndServiceSelectionRoute`

Shared route payloads:

- `OrderNavParams`
- `ServiceCompleteData`
- `EndOderInfo`
- `WatermarkData`

Current note on `SelectDeviceRoute` usage:

- `navigateToSelectDevice()` currently forwards directly to `navigateToNfcSignInForStartOrder()`
- treat `SelectDeviceRoute` as present in route inventory, but not an active meaningful hop in the start-order path

## 3) Ownership map by module

### `:app` currently owns most route-bound UI

Major screen packages currently under `app/src/main/kotlin/com/ytone/longcare/features/**`:

- login, home
- servicehours, serviceorders
- nursingexecution, selectservice
- servicecountdown, endservice, servicecomplete
- identification, facerecognition, face/manual capture, shared face verification
- nfc
- photoupload + camera
- userlist, userservicerecord, webview

### `:feature:*` modules current UI ownership

- `:feature:location`
  - owns location service/managers/VM; tracking is embedded in service execution and has no standalone route
- `:feature:login`, `:feature:home`, `:feature:identification`
  - currently provide feature entry constants, actions, domain/VM/DI support
  - `:feature:identification` also owns `DefaultFaceVerificationScreen` and its shared CameraX/ML Kit
    capture UI; its `IdentificationScreen` host remains in `:app`
- `:feature:photoupload`, `:feature:servicecountdown`
  - currently provide API/domain/viewmodel/delegate layers
  - route-bound Compose screens still in `:app`

## 4) Shared UI patterns in current code

- typed navigation with `composable<RouteType>()` and `toRoute<RouteType>()`
- `savedStateHandle` key handoff for cross-screen results (camera/photo upload/face capture)
- action-interface pattern for screen navigation callbacks (for example `*Actions` contracts)
- ViewModel + `collectAsStateWithLifecycle()` + `StateFlow` state projection
- screen decomposition into `Screen`, `Components`, `Sections`, `Dialogs`, `Handlers` files
- one shared `PhotoPreviewDialog` in `:core:ui` for URI/File/URL/Bitmap previews across photo
  upload, sales registration, automatic face capture, and manual face capture
- one standard watermarked `CameraRoute` reused by nursing and sales; face-analysis screens keep
  their specialized capture UI while sharing preview and persistent-image processing
- service-person verification from `IdentificationRoute` uses `DefaultFaceVerificationRoute`; its
  result returns through `DEFAULT_FACE_VERIFICATION_RESULT_KEY`, while the elder step remains the
  standard watermarked `CameraRoute`
- Material 3 adaptive navigation suite keeps phone bottom navigation and selects a navigation rail
  for larger windows without duplicating destination state

## 5) Legacy app/features footprint (still active)

Legacy here means "inside app feature package instead of dedicated feature module UI ownership."
This remains active and route-critical for:

- login/home route UI
- full service-flow route UI (except location)
- identification/face/NFC route UI
- photo upload + camera route UI
- user/webview support route UI

This is expected in current architecture state and should be treated as in-progress migration work, not a broken runtime condition.
