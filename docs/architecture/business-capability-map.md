# Business Capability Map

Last verified: 2026-04-12

This map focuses on current implemented behavior and its technical ownership.

## 1) Login and session bootstrap

Status: implemented

- Main entry route:
  - `LoginRoute`
- Main flow:
  - login screen triggers login success -> `navigateToHomeFromLogin()`
  - app start route is session-driven (`Unknown/LoggedOut/LoggedIn`)
- Key dependencies:
  - `UserSessionRepository` (`core:domain` contract)
  - login state handling in `MainViewModel`
  - `:feature:login` actions/ViewModel support + `:app` login UI route screen

## 2) Service entry and order selection

Status: implemented

- Main entry routes:
  - `HomeRoute`
  - `CarePlansListRoute`
  - `ServiceRecordsListRoute`
  - `ServiceRoute`
  - `NursingExecutionRoute`
  - `SelectServiceRoute`
- Main flow:
  - home -> care plan or service record list
  - select order -> service details / nursing execution
  - select projects -> countdown start path
- Key dependencies:
  - home/order list/service-hours/nursing-execution screens (currently route-bound in `:app`)
  - shared home/order ViewModel patterns
  - order route typing via `OrderNavParams` and `OrderKey`

## 3) Identification and face-related capabilities

Status: implemented (with multiple paths)

- Main entry routes:
  - `IdentificationRoute`
  - `TxFaceRoute` (Tencent face verification test/flow)
  - `ManualFaceCaptureRoute`
  - `FaceRecognitionGuideRoute`
- Main flow:
  - service/NFC flow enters identification
  - identification supports capture, upload, setup, and verification transitions
  - manual face capture can return image path via navigation saved state
- Key dependencies:
  - Tencent face verification SDK (`WbCloudFaceVerifySdk`)
  - ML Kit face detection
  - identification domain/data/use cases in `:feature:identification`
  - route screens currently in `:app` for identification and related UI

## 4) Location tracking and reporting

Status: implemented

- Main entry route:
  - `LocationTrackingRoute`
- Main flow:
  - permissions and location-service check
  - start/stop tracking tied to service workflow and dedicated tracking screens
  - background/foreground location tracking service integration
- Key dependencies:
  - AMap location SDK and fallback system location provider
  - location facade and upload queue repositories
  - `LocationTrackingService` and location managers in `:feature:location`
  - location usage also wired into NFC, photo watermark, and countdown flows

## 5) Photo capture and upload

Status: implemented

- Main entry routes:
  - `PhotoUploadRoute`
  - `CameraRoute`
- Main flow:
  - service countdown can navigate to photo upload
  - photo upload can navigate to camera
  - results are exchanged via `savedStateHandle` keys
- Key dependencies:
  - camera/photo upload route screens currently in `:app`
  - `:feature:photoupload` module APIs, delegates, queue processing, and trackers
  - Coil and Bugly tracker integration
  - COS upload path via `core:data` COS repository

## 6) Service countdown and completion

Status: implemented

- Main entry routes:
  - `ServiceCountdownRoute`
  - `EndServiceSelectionRoute`
  - `NfcSignInRoute` (start/end order sign modes)
  - `ServiceCompleteRoute`
- Main flow:
  - selected projects -> countdown
  - end-service selection -> NFC sign-out flow
  - completion summary -> return home (stack clear)
- Key dependencies:
  - countdown UI route screen currently in `:app`
  - `:feature:servicecountdown` ViewModel/domain support
  - countdown foreground service, alarm receivers, notification managers (app-side runtime)
  - service-time alarms/workers and boot recovery components

## 7) NFC and device-related flows

Status: implemented (including test paths)

- Main entry routes:
  - `NfcSignInRoute`
  - `SelectDeviceRoute` (route exists but is not the active start-order hop)
  - `NfcTestRoute`
- Main flow:
  - start/end order NFC workflow with scan mode differences
  - current start-order path does not use a meaningful device-selection hop:
    `navigateToSelectDevice()` immediately forwards to `navigateToNfcSignInForStartOrder()`
  - NFC test/debug flows available from login/support routes
- Key dependencies:
  - Android NFC intent filters in app manifest
  - NFC workflow ViewModel/delegates in `:app` feature package
  - location/NFC coordination in workflow handlers
  - optional R65C HID-related debug capture surfaces

## 8) Supporting capabilities

Status: implemented

- Routes:
  - `WebViewRoute`
  - `UserListRoute`
  - `UserServiceRecordRoute`
- Dependencies:
  - home and user list/record feature screens
  - shared typed navigation parameters
  - route-bound support screens currently in `:app`

## 9) Capability summary by status

- implemented:
  - login/session
  - service entry + order/service selection
  - identification + face setup/verify + manual capture
  - location tracking/reporting
  - photo capture/upload
  - service countdown/end/complete
  - NFC start/end order workflows and test flow
  - user list, service records, webview, update dialog
- in-progress (technical, not user-facing capability gap):
  - modularization: route-bound UI ownership is still mixed between `:app` and `:feature:*`
