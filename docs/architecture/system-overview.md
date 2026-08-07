# System Overview

Last verified: 2026-08-07

This document describes the current runtime architecture as implemented today.

## 1) Runtime shape

LongCare is an Android app with a shell-first architecture:

- `:app` is still the runtime host for:
  - app startup and `MainApplication`
  - `MainActivity`
  - typed route registration and navigation graph composition
  - many current feature screens under `app/src/main/kotlin/com/ytone/longcare/features/**`
- `:core:*` modules hold shared model/domain/data/common/ui capabilities.
- `:feature:*` modules are partially migrated business slices (not all own UI routes yet).

## 2) Module topology and current ownership

### App shell

- `:app`
  - owns `MainViewModel` session gating (`Unknown -> Splash`, `LoggedIn -> Home`, `LoggedOut -> Login`)
  - owns `NavHost` and route registration (`registerEntryNavGraphs`, `registerServiceFlowNavGraphs`, `registerSupportNavGraphs`)
  - owns Android manifest components (services, receivers, provider, activities)
  - still contains most route-bound Compose screens

### Core modules

- `:core:model`
  - shared data models and value objects
- `:core:domain`
  - repository/use-case contracts, intended pure Kotlin boundary
- `:core:data`
  - repository implementations, network/database/COS implementations, DI bindings
- `:core:ui`
  - shared UI helpers/components and UI support code
  - owns the single full-screen image preview implementation (`PhotoPreviewDialog`)
- `:core:common`
  - logging, runtime config, utility abstractions, common helpers
  - owns the unified image output pipeline, JPEG policies, managed storage, and cleanup

### Feature modules (current reality)

- `:feature:login`
  - has feature entry constant, actions, ViewModel/DI support
  - route screen is currently in `:app` (`features/login/ui/LoginScreen.kt`)
- `:feature:home`
  - has feature entry constant, actions, shared ViewModel/DI support
  - route screen is currently in `:app` (`features/home/ui/HomeScreen.kt`)
- `:feature:identification`
  - has feature entry constant, domain/data/use-case/ViewModel/DI support
  - route screen is currently in `:app` (`features/identification/ui/IdentificationScreen.kt`)
- `:feature:location`
  - currently includes route-bound UI (`LocationTrackingScreen`) and service/managers
- `:feature:photoupload`
  - currently provides APIs, domain support, ViewModel/delegates, trackers
  - owns the validated `PhotoCloudUploader` boundary used by photo-task and sales flows
  - route-bound UI still in `:app` (`PhotoUploadScreen`, `CameraScreen`)
- `:feature:servicecountdown`
  - currently provides APIs, domain support, ViewModel/delegates
  - route-bound UI still in `:app` (`ServiceCountdownScreen`)

## 3) Navigation shell and route registration

Navigation is typed (`@Serializable` route objects/data classes) and assembled in `:app`.

- Root route resolution:
  - `SessionState.Unknown -> SplashRoute`
  - `SessionState.LoggedIn -> HomeRoute`
  - `SessionState.LoggedOut -> LoginRoute`
- Route groups:
  - entry graph: login/home
  - service-flow graph: service/nursing/NFC/select-service/photo-upload/countdown/complete/end-selection
  - support graph: face-guide/identification/device/user lists/NFC test/camera/webview/location
- Feature route registry guard currently validates exactly 3 feature route constants:
  - `feature_login`
  - `feature_home`
  - `feature_identification`

## 4) Layer boundaries in practice

Project rules currently enforced by docs/scripts:

- domain should remain Android-free and contract-first
- feature/presentation should not directly depend on data implementations
- ViewModel handles state orchestration; long-lived UI state uses `StateFlow`; one-off events use `SharedFlow(replay = 0)`

Current codebase reality:

- architecture direction is enforced by quality scripts
- runtime UI ownership is still mixed between `:app` and feature modules

## 5) Android platform boundary

The Android component surface is defined in `app/src/main/AndroidManifest.xml`:

- activities:
  - `MainActivity`
  - `CountdownAlarmActivity`
  - debug-gated `FaceCaptureTestActivity`
- services:
  - location tracking foreground service
  - countdown foreground service
  - alarm ringtone foreground service
  - AMap `APSService`
- receivers:
  - countdown alarm/dismiss
  - service-time alarm
  - boot completed
- providers:
  - custom `FileProvider`
  - WorkManager startup initializer override/removal

## 6) External integrations currently in use

- AMap Location SDK (`com.amap.api:location`) for foreground/background location flows
- Tencent COS SDK (`com.qcloud.cos:cos-android`) via `core/data` COS repository layer
- Tencent face verification SDK (`WbCloudFaceVerifySdk`) via app common face verification manager
- ML Kit face detection (`com.google.mlkit:face-detection`) for face image processing/validation
- Bugly crash reporting (`com.tencent.bugly:crashreport`) in app startup and camera/countdown trackers
- QLZ assessment SDK 1.3.0.2 through the app-owned `QlzSdkClient`; Sale contracts and
  implementations remain in `core:domain` / `core:data`
- WorkManager for startup update checks and background jobs

## 7) Current-state architecture summary

The codebase is in a transitional but stable state:

- stable shell + typed navigation are in place
- domain/data/core boundaries are established and script-guarded
- service execution chain is implemented end-to-end
- UI/module ownership migration is still in progress, with significant route-bound UI remaining in `:app`

## 8) Unified image lifecycle

- Standard business photos use one CameraX route (`CameraRoute`) for capture and watermark rendering.
- The app converts every persistent JPEG through `UnifiedImagePipeline` / `UnifiedJpegEncoder`; policy values are centralized in `ImageProcessingPolicies`.
- Watermarked output is written atomically into the app-specific Pictures directory. Face output uses app-internal managed directories.
- Temporary captures are removed after processing, including failure and cancellation paths. Registration/task-owned files are removed when the user deletes or abandons them and after successful completion.
- `core:data` couples order-image row deletion with managed-file deletion, so every service-completion and order-cleanup caller gets the same local-file lifecycle automatically.
- All image thumbnails open the shared `PhotoPreviewDialog`; the former upload, sales, automatic-face, and manual-face preview implementations have been removed.
- ML-driven face capture retains specialized camera analysis because it has a different runtime contract, while its persistent output and preview still pass through the shared boundaries.
