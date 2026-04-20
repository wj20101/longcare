# Home Login Log Report Design

## Related Docs

- index: [`../README.md`](../README.md)
- login agreement design: [`2026-04-02-login-agreement-confirmation-design.md`](2026-04-02-login-agreement-confirmation-design.md)

## Background

The backend already provides a dedicated login-log reporting endpoint:

- `POST /V1/Login/Log`

The request body is:

- `phoneSystem`
- `phoneVersion`
- `networkType`
- `networkOperator`

The model and API declaration already exist in the codebase:

- [`LoginLogParamModel.kt`](../../../core/model/src/main/kotlin/com/ytone/longcare/model/LoginLogParamModel.kt)
- [`LongCareApiService.kt`](../../../core/data/src/main/kotlin/com/ytone/longcare/api/LongCareApiService.kt)

What is missing is the actual application-side reporting flow and the correct trigger point.

The requirement is:

- report this log when entering the home screen
- report once for each real home-entry event
- do not report again just because the app went to background and came back while the same home screen instance remained active

## Goal

Add a complete home-entry login-log reporting flow so that:

- every navigation-based or recreated entry into the home screen triggers one report
- background/foreground resume without a fresh home entry does not trigger a new report
- the request body is populated from real device and network state
- reporting failure does not block or disturb the home UI

## Non-Goals

This design does not:

- change the login API itself
- introduce a global analytics platform
- add retries, persistence, or offline queueing for this log
- block home rendering on report success
- treat generic app resume as a new home entry

## Current State

### 1. API and model already exist

The following are already present:

- [`LongCareApiService.recordLoginLog(...)`](../../../core/data/src/main/kotlin/com/ytone/longcare/api/LongCareApiService.kt)
- [`LoginLogParamModel`](../../../core/model/src/main/kotlin/com/ytone/longcare/model/LoginLogParamModel.kt)

So this feature does not need a new Retrofit endpoint or a new DTO.

### 2. Repository layer does not expose the capability

[`LoginRepository`](../../../core/domain/src/main/kotlin/com/ytone/longcare/domain/login/LoginRepository.kt) currently exposes:

- `login(...)`
- `sendSmsCode(...)`
- `getStartConfig(...)`

It does not yet expose:

- `recordLoginLog(...)`

### 3. Home screen already has a stable entry hook

[`HomeScreen`](../../../app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt) already has a `LaunchedEffect(Unit)` for one-time-on-entry side effects.

That is the right UI trigger boundary for this requirement because it matches:

- initial screen entry
- recreated screen entry

and does not naturally fire again for plain recomposition.

### 4. Home shared view model is the right orchestration point

[`HomeSharedViewModel`](../../../feature/home/src/main/kotlin/com/ytone/longcare/features/home/vm/HomeSharedViewModel.kt) already owns home-level cross-section state.

It is a better place than the composable itself for:

- coordinating one-shot reporting
- calling repository methods
- keeping UI code free from direct network work

## Approved Direction

Use the existing login repository boundary and add a new home-entry reporting call through the shared home view model.

The final flow should be:

1. `HomeScreen` enters
2. `LaunchedEffect(Unit)` calls `HomeSharedViewModel.reportHomeEntry()`
3. `HomeSharedViewModel` builds a `LoginLogParamModel` using a lightweight device/network info provider
4. `HomeSharedViewModel` calls `LoginRepository.recordLoginLog(...)`
5. the request is sent through the existing Retrofit service
6. failures are swallowed after local logging only

## Design

### 1. Trigger Rule

The reporting trigger should be:

- each real entry into the home screen route

The reporting trigger should not be:

- every app resume
- every recomposition
- every lifecycle transition while the same home screen instance remains active

Concretely:

- use `LaunchedEffect(Unit)` in [`HomeScreen`](../../../app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt)
- do not attach the call to `ON_RESUME`
- do not attach the call to a repeating lifecycle collector

This matches the approved requirement boundary exactly.

### 2. ViewModel-Level Orchestration

[`HomeSharedViewModel`](../../../feature/home/src/main/kotlin/com/ytone/longcare/features/home/vm/HomeSharedViewModel.kt) should expose:

- `reportHomeEntry()`

That function should:

- be safe to call from the home screen entry effect
- launch work inside `viewModelScope`
- ignore duplicate invocations within the same in-flight request window

This should be a lightweight in-memory in-flight guard only, not a cross-session dedupe mechanism.

Example intent:

- if the screen is entered once and reporting begins, a second accidental call before completion should not send a second parallel request
- but a later fresh entry into a newly created home screen instance should report again

### 3. Repository Boundary

[`LoginRepository`](../../../core/domain/src/main/kotlin/com/ytone/longcare/domain/login/LoginRepository.kt) should add:

- `recordLoginLog(param: LoginLogParamModel): ApiResult<Unit>`

[`LoginRepositoryImpl`](../../../core/data/src/main/kotlin/com/ytone/longcare/data/repository/LoginRepositoryImpl.kt) should implement it by reusing:

- [`LongCareApiService.recordLoginLog(...)`](../../../core/data/src/main/kotlin/com/ytone/longcare/api/LongCareApiService.kt)
- existing `safeApiCall(...)`
- existing dispatcher and event-bus patterns already used by the login repository

This keeps the new capability aligned with the rest of the login domain.

### 4. Device and Network Info Source

The request body should be built from a small dedicated provider, not ad-hoc string collection inside the composable.

Recommended new responsibility:

- a lightweight provider in app-layer utilities or home feature support code

Its job is only to return a [`LoginLogParamModel`](../../../core/model/src/main/kotlin/com/ytone/longcare/model/LoginLogParamModel.kt).

Field mapping:

- `phoneSystem`
  - constant `Android`
- `phoneVersion`
  - `Build.VERSION.RELEASE`
  - fallback to `""` when unavailable
- `networkType`
  - resolve via `ConnectivityManager`
  - normalize to one of:
    - `WIFI`
    - `CELLULAR`
    - `ETHERNET`
    - `NONE`
    - `UNKNOWN`
- `networkOperator`
  - resolve via `TelephonyManager.networkOperatorName`
  - fallback to `""` when unavailable, unsupported, or not applicable

The provider should be resilient and never throw outward for missing network or telephony state.

### 5. Failure Policy

This reporting call should be:

- non-blocking
- silent on failure

It should not:

- show toast
- alter home UI state
- block page rendering
- retry automatically

It may:

- write a local log message for diagnostics

The endpoint is operational logging, not user-visible business flow, so the failure mode should stay lightweight.

### 6. Home Entry Guard

The guard requirement is intentionally narrow.

Keep:

- one request per true home entry

Prevent:

- duplicate concurrent requests caused by repeated invocation during the same screen instance

Do not prevent:

- later reports from later home entries

This means a simple in-memory boolean or job-state check inside [`HomeSharedViewModel`](../../../feature/home/src/main/kotlin/com/ytone/longcare/features/home/vm/HomeSharedViewModel.kt) is enough.

### 7. Architectural Placement

Recommended file responsibilities:

- [`HomeScreen.kt`](../../../app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt)
  - trigger `reportHomeEntry()` from one-time entry effect
- [`HomeSharedViewModel.kt`](../../../feature/home/src/main/kotlin/com/ytone/longcare/features/home/vm/HomeSharedViewModel.kt)
  - orchestrate reporting and in-flight guard
- [`LoginRepository.kt`](../../../core/domain/src/main/kotlin/com/ytone/longcare/domain/login/LoginRepository.kt)
  - expose reporting contract
- [`LoginRepositoryImpl.kt`](../../../core/data/src/main/kotlin/com/ytone/longcare/data/repository/LoginRepositoryImpl.kt)
  - call Retrofit endpoint
- a new provider file in app or home feature layer
  - build `LoginLogParamModel` from system/network state

This keeps:

- UI trigger in UI
- request orchestration in ViewModel
- API call in repository
- environment sampling in a dedicated helper

## Testing Strategy

### Unit Tests

Add or update tests for:

- `HomeSharedViewModel.reportHomeEntry()` triggering one request
- in-flight duplicate calls during the same request window not launching parallel duplicate requests
- failure from `LoginRepository.recordLoginLog(...)` not escaping as a UI-breaking exception
- device/network info provider returning stable fallback strings when system info is unavailable

### Integration-Scope Verification

Verify that:

- entering home constructs the expected repository call
- the request uses the existing login API service

### Manual Verification

Manual verification should confirm:

- entering home after navigation sends one request
- leaving and re-entering home sends another request
- backgrounding and foregrounding without re-entering home does not send another request
- offline or failing network does not interrupt home rendering

## Consequences

This design is intentionally small and pragmatic.

It reuses:

- existing DTO
- existing Retrofit method
- existing login repository
- existing home-entry UI hook

That gives the feature the smallest viable footprint while still preserving clean layering.

## Next Step

If this design is approved, the implementation plan should:

1. add `recordLoginLog(...)` to the login repository contract and implementation
2. add a dedicated provider for `LoginLogParamModel`
3. extend `HomeSharedViewModel` with `reportHomeEntry()` and an in-flight guard
4. trigger the report from `HomeScreen` entry
5. add focused tests for repository invocation and guard behavior
