# System Overview

Last verified: 2026-08-14

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
  - shared data models, value objects, and transport-neutral `ApiResult`
  - compiled as a pure Kotlin/JVM module
- `:core:domain`
  - repository/use-case contracts, intended pure Kotlin boundary
  - compiled as a pure Kotlin/JVM module so Android dependencies fail at build time
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
  - owns the default CameraX/ML Kit face-verification page and `CheckFace` orchestration
  - identification host screen is currently in `:app`
    (`features/identification/ui/IdentificationScreen.kt`)
- `:feature:location`
  - owns location service/managers/reporting; tracking is embedded in service flows rather than exposed as a standalone route
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
  - support graph: face-guide/identification/device/user lists/camera/webview
- Feature route registry guard currently validates exactly 3 feature route constants:
  - `feature_login`
  - `feature_home`
  - `feature_identification`

## 4) Layer boundaries in practice

Project rules currently enforced by docs/scripts:

- domain should remain Android-free and contract-first
- feature/presentation should not directly depend on data implementations
- ViewModel handles state orchestration; durable UI state and user-visible actions use `StateFlow`
- user-visible actions remain queued until the UI acknowledges them; replay-zero flows are limited to loss-tolerant live signals
- Android services, alarms, installers, NFC sources, and SDK entry points are accessed through app-owned platform gateways/controllers
- Sale Retrofit methods and network-only DTOs are locked by method/path/annotation/JSON-key contract tests
- session mutations are suspend operations; login/logout callers cannot report completion before DataStore persistence finishes

Current codebase reality:

- architecture direction is enforced by quality scripts
- runtime UI ownership is still mixed between `:app` and feature modules

## 5) Android platform boundary

The Android component surface is defined in `app/src/main/AndroidManifest.xml`:

- activities:
  - `MainActivity`
  - `CountdownAlarmActivity`
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
  - custom `FileProvider` restricted to update APK directories
  - WorkManager startup initializer override/removal

## 6) External integrations currently in use

- AMap Location SDK (`com.amap.api:location`) for foreground/background location flows
- Tencent COS SDK (`com.qcloud.cos:cos-android`) via `core/data` COS repository layer
- CameraX + ML Kit face detection (`com.google.mlkit:face-detection`) provide the default
  service-person capture page; verification uses the documented `/V1/User/CheckFace` contract
- Tencent face verification SDK (`WbCloudFaceVerifySdk`) remains behind an app-owned UI controller
  for legacy compatibility routes and is not the default order-identification entry
- Bugly crash reporting (`com.tencent.bugly:crashreport`) behind `CrashReportGateway`; local diagnostics
  remain available in Debug, while remote reporting is enabled only after consent and successful SDK initialization
- QLZ assessment SDK 1.3.0.2 through app-owned UI/device controllers; Sale domain contracts remain
  in `core:domain`, while exact network DTOs and implementations remain in `core:data`
- WorkManager for startup update checks and background jobs; the UI observes only the exact latest startup request ID, so historical succeeded work cannot revive a withdrawn update

## 7) Current-state architecture summary

The codebase is in a transitional but stable state:

- stable shell + typed navigation are in place
- domain/data/core boundaries are established and script-guarded
- `:core:model` and `:core:domain` are Android-free JVM modules
- Room upgrades use explicit migrations and never delete the production database as an exception fallback
- WorkManager-backed update checks and downloads can reconnect after Activity/ViewModel recreation
- boot notification recovery resolves persisted session state inside `goAsync()` instead of reading the initial `Unknown` value
- app-owned routes hosted by `MainActivity` are portrait-only; the Android 16 restricted-resizability
  compatibility opt-out is scoped to that Activity for targetSdk 36, while SDK-owned Activities retain
  their own orientation policies
- top-level destinations use Material 3 `NavigationSuiteScaffold`, selecting bottom navigation or a rail from current window size/posture
- service execution chain is implemented end-to-end
- UI/module ownership migration is still in progress, with significant route-bound UI remaining in `:app`

## 8) Unified image lifecycle

- Standard business photos use one CameraX route (`CameraRoute`) for capture and watermark rendering.
- The app converts every persistent JPEG through `UnifiedImagePipeline` / `UnifiedJpegEncoder`; policy values are centralized in `ImageProcessingPolicies`.
- Watermarked output is written atomically into the app-specific Pictures directory. Face output uses app-internal managed directories.
- Temporary captures are removed after processing, including failure and cancellation paths. Registration/task-owned files are removed when the user deletes or abandons them and after successful completion.
- `core:data` couples order-image row deletion with managed-file deletion, so every service-completion and order-cleanup caller gets the same local-file lifecycle automatically.
- All image thumbnails open the shared `PhotoPreviewDialog`; the former upload, sales, automatic-face, and manual-face preview implementations have been removed.
- ML-driven face capture retains specialized lifecycle-bound CameraX analysis because it has a
  different runtime contract. The default verification path keeps the captured face in memory,
  compresses it under the shared 500 KiB face-comparison policy, and sends raw Base64 without a
  gallery or persistent-preview step.
- Face-verification preview/Base64 loading reads the already-compressed managed JPEG on an injected IO dispatcher; Compose only renders ViewModel state and does not decode or recompress on the main thread.
