# Fast Location and Camera Permission Design

## Background

Three user-visible delays or permission issues need to be fixed:

1. `NfcWorkflowScreen` waits around 40 seconds before location is available during the first NFC sign-in.
2. `PhotoUploadScreenContent` shows the camera permission purpose dialog even after camera permission is already granted.
3. `SelectServiceScreen` waits around 30 seconds after "开始服务".

The NFC and start-service delays share the same location path. `DefaultLocationFacade.getCurrentLocation()` first waits for AMap through `ContinuousAmapLocationManager.getCurrentLocation(timeoutMs = 10_000)`, then falls back to the Android system provider. The AMap method currently calls `startContinuousLocation().first()`, so it passively waits for the next shared continuous-location emission. If there is no fresh replayed location, the caller can wait for AMap and then wait again for GPS/network fallback.

The product decision for this round is: location should improve accuracy when available, but it must not block NFC sign-in or service start. If a quick location cannot be obtained, these flows may continue with empty longitude and latitude.

## Immediate Scope

This implementation will make location retrieval fast and non-blocking for NFC sign-in and service start, and will fix the photo upload camera permission notice.

### Location Behavior

- Increase the reusable location cache window for business entry points from the current 30 seconds to a longer window. The implementation should use a named constant, with a target of 5 minutes unless tests or product constraints indicate otherwise.
- Prefer cached location before starting a fresh location wait.
- For NFC sign-in and `starOrder`, use a short location wait budget, targeting 3 to 5 seconds.
- If the short wait returns no location, continue the business request with empty longitude and latitude.
- Keep existing permission and location-service checks:
  - If location permission is missing, request permission as the current flow does.
  - If location service is disabled, guide the user to settings as the current flow does.
  - After permission is granted, retry the pending NFC scan, but do not wait indefinitely.

### ContinuousAmapLocationManager

The current `getCurrentLocation()` behavior is passive because it waits for `startContinuousLocation().first()`. This can be acceptable for an already warm shared flow, but it is not a good single-shot guarantee.

For this round, do not split the AMap implementation into a new manager. Instead:

- Keep using the shared continuous-location flow.
- Treat it as a short opportunistic wait, not as a blocking prerequisite.
- Make the caller-controlled timeout meaningful by ensuring fallback work is also bounded by the same fast-flow expectation.
- Continue recording successful locations into `LocationStateManager` so later NFC/service actions can reuse the longer cache.

### System Location Fallback

The Android system provider fallback can also be slow. It should be bounded when used from `DefaultLocationFacade.getCurrentLocation()`.

- Wrap system fallback in a timeout.
- Use the remaining or configured short budget rather than allowing an unbounded GPS then network wait.
- If the timeout expires, return `null`.

### NfcWorkflowScreen

- Use the longer cached location first.
- Use the short location wait for NFC sign-in.
- If location cannot be obtained within the budget, allow the NFC business flow to continue with empty coordinates instead of staying in a loading state.
- Preserve existing permission dialogs and pending scan resume behavior.
- Keep or improve the existing loading message so users know the app is trying to obtain location when NFC processing takes a few seconds.

### SelectServiceScreen

- `SharedOrderDetailViewModel.starOrder()` should no longer block for tens of seconds on location.
- It should call the fast location path and then submit `starOrder` with coordinates when available, or empty strings when unavailable.
- The UI should remain responsive and show the existing loading state only for the short location wait plus API request.
- `updateSelectedProjects()` should continue to run after `starOrder` success before navigation.

### PhotoUploadScreenContent / PhotoUploadScreen

The camera permission purpose dialog should only be shown when camera permission is not granted.

- On add-photo click, set the current task type.
- If camera permission is already granted, generate watermark data and navigate to camera directly.
- If permission is not granted, show the purpose dialog, then launch the system permission request.
- If permission is denied, show the existing toast and do not navigate.

## Out Of Scope

- No large rewrite of AMap client ownership in this round.
- No changes to server API contracts for empty coordinates.
- No changes to service countdown or location reporting behavior unless required by tests.
- No UI redesign beyond necessary loading and permission behavior fixes.

## Later Thorough Location Redesign

The more complete long-term fix is to split single-shot location and continuous tracking into separate responsibilities.

### Target Architecture

- `SingleLocationProvider`
  - Owns one-time location requests.
  - Uses AMap single-location mode (`isOnceLocation = true`) or an equivalent active one-shot request.
  - Has explicit timeout, cache preference, and fallback policy.
  - Returns `LocationResult?` without starting or depending on continuous tracking collectors.

- `ContinuousLocationProvider`
  - Owns long-running service tracking.
  - Keeps the existing shared continuous AMap stream.
  - Handles foreground keep-alive, background reporting, and replayed latest location.

- `LocationFacade`
  - Exposes separate APIs:
    - `getFastLocation(maxCacheAgeMs, timeoutMs)`
    - `observeLocations(intervalMs)`
    - `getCachedLocation(maxAgeMs)`
    - keep-alive acquire/release APIs
  - Encodes fallback policy in one place instead of requiring screen/viewmodel callers to guess.

### Migration Steps

1. Add the new single-shot provider behind `LocationFacade`.
2. Move NFC and `starOrder` to `getFastLocation`.
3. Keep service countdown and reporting on continuous tracking.
4. Add tests for cache hit, AMap success, AMap timeout, system fallback success, system fallback timeout, and permission-denied behavior.
5. Remove any screen-level timeout duplication once the facade contract is stable.

### Benefits

- NFC and service start become predictable and fast.
- Continuous tracking remains optimized for long-running reporting.
- AMap single-shot and continuous modes no longer affect each other.
- Future callers can choose the right location behavior by API name.

## Testing

- Unit test the location facade fast path:
  - fresh cache returns immediately;
  - stale cache triggers a short location wait;
  - AMap timeout returns `null` or system fallback within the budget;
  - system fallback timeout returns `null`;
  - successful results refresh `LocationStateManager`.
- Unit or Compose-level test the photo upload permission decision:
  - granted permission navigates directly;
  - missing permission shows the purpose dialog first;
  - denied permission shows the existing toast.
- Unit test or targeted ViewModel test `starOrder`:
  - location success sends coordinates;
  - location timeout sends empty strings and still calls the API.
- Manual Android verification:
  - fresh install, NFC sign-in with no cached location;
  - NFC sign-in after permission grant;
  - start service with no cached location;
  - photo upload with camera already granted;
  - photo upload with camera not yet granted.
