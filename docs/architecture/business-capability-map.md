# Business Capability Map

Last verified: 2026-08-14

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

Status: implemented

- Main entry routes:
  - `IdentificationRoute`
  - `DefaultFaceVerificationRoute`
  - `TxFaceRoute` (legacy Tencent compatibility/test flow; not the default order path)
  - `ManualFaceCaptureRoute` (legacy face-setup compatibility flow)
  - `FaceRecognitionGuideRoute`
- Main flow:
  - service/NFC flow enters identification
  - service-person verification opens `DefaultFaceVerificationRoute`, which performs a CameraX
    countdown/stability capture and calls documented `POST /V1/User/CheckFace` with exactly
    `orderId` and raw Base64 `faceImg`
  - successful default verification returns through navigation saved state and unlocks the elder
    record-photo step; elder capture continues to use the standard watermarked camera/upload flow
  - Tencent SDK and manual capture remain compatibility routes but are not selected by the default
    identification action
- Key dependencies:
  - CameraX + ML Kit face detection in `:feature:identification`
  - `CheckFaceUseCase` and `CheckFaceGateway` in `:feature:identification`
  - Tencent face verification SDK (`WbCloudFaceVerifySdk`) behind an app adapter for legacy flows
  - identification domain/data/use cases in `:feature:identification`
  - identification host screen remains in `:app`; the default face-verification screen and capture
    implementation live in `:feature:identification`

## 4) Location tracking and reporting

Status: implemented

- Main flow:
  - permissions and location-service check
  - start/stop tracking tied to service workflow
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
  - persistent output is oriented, watermarked, compressed, size-validated, and stored through
    the shared image pipeline; no gallery entry remains in standard business capture
- Key dependencies:
  - camera/photo upload route screens currently in `:app`
  - `:feature:photoupload` module APIs, delegates, queue processing, and trackers
  - common full-screen preview in `:core:ui`
  - image output policy and managed-file lifecycle in `:core:common`
  - validated COS upload facade in `:feature:photoupload`, backed by `core:data`

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

Status: implemented

- Main entry routes:
  - `NfcSignInRoute`
  - `SelectDeviceRoute` (route exists but is not the active start-order hop)
- Main flow:
  - start/end order NFC workflow with scan mode differences
  - current start-order path does not use a meaningful device-selection hop:
    `navigateToSelectDevice()` immediately forwards to `navigateToNfcSignInForStartOrder()`
- Key dependencies:
  - Android NFC intent filters in app manifest
  - NFC workflow ViewModel/delegates in `:app` feature package
  - location/NFC coordination in workflow handlers
  - Debug and Release both expose the internal `NfcValidationActivity` from the hidden login-logo
    validation sheet for native NFC and R65C external-reader verification; the Release component is
    not exported and Debug-only mock networking remains excluded

## 8) QLZ assessment and sales leads

Status: data, customer-facing sales UI, and SDK integration implemented

- Current entries:
  - sales accounts (`userIdentity == 2`) use `SalesExperienceScreen` inside `HomeRoute`
- Main flow:
  - add/search/select a prospective customer
  - customer registration accepts up to three camera photos; capture, watermark rendering,
    result delivery, and full-screen preview reuse the nursing `CameraRoute` workflow
  - deleting, abandoning, replacing, or successfully submitting a draft releases its managed
    local photo files; failed submission retains them for retry
  - sales watermarks identify the advisor and capture metadata while omitting insured-person
    and address rows
  - obtain a one-time assessment Token through the LongCare Sale API
  - initialize the QLZ SDK and open its built-in Bluetooth assessment UI
  - receive progress, completion, cancellation, and report callbacks
  - open the server-provided form-assessment `pgUrl` through the app-owned `WebViewRoute`;
    only device-based automatic assessment enters the QLZ SDK UI
  - after completion, refresh `/V1/Sale/GetUserLatentDetail` and open its `pgUrl`
    assessment report through the app-owned `WebViewRoute`
- Key dependencies:
  - QLZ SDK AAR 1.3.0.2 and protobuf Lite runtime
  - `SaleRepository` (`core:domain`) and `SaleRepositoryImpl` (`core:data`)
  - app-owned `QlzSdkClient` Android/vendor boundary
  - full details in `docs/integrations/qlz-sdk.md`

## 9) Supporting capabilities

Status: implemented

- Routes:
  - `WebViewRoute`
  - `UserListRoute`
  - `UserServiceRecordRoute`
- Dependencies:
  - home and user list/record feature screens
  - shared typed navigation parameters
  - route-bound support screens currently in `:app`

## 10) Capability summary by status

- implemented:
  - login/session
  - service entry + order/service selection
  - identification + face setup/verify + manual capture
  - location tracking/reporting
  - photo capture/upload
  - service countdown/end/complete
  - NFC start/end order workflows
  - QLZ Sale APIs, customer-facing sales UI, and assessment SDK flow
  - user list, service records, webview, update dialog
- in-progress (technical, not user-facing capability gap):
  - modularization: route-bound UI ownership is still mixed between `:app` and `:feature:*`
