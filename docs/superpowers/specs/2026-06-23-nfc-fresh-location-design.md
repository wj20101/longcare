# NFC Fresh Location Design

## Context

Users report severe distance deviation during NFC sign-in/sign-out. The NFC path sends `longitude` and `latitude` to the server, and the server compares against AMap coordinates. The server-side reference coordinates are also AMap coordinates, so coordinate-system mismatch is not the primary suspect.

The current client location facade uses a business cache before requesting a provider result:

- `LocationFacade.BUSINESS_LOCATION_CACHE_MAX_AGE_MS` is 5 minutes.
- `DefaultLocationFacade.getCurrentLocation()` returns that cache first.
- `ContinuousAmapLocationManager.getCurrentLocation()` reads the shared continuous Flow with `first()`.
- The shared Flow uses `shareIn(replay = 1)`, so a one-shot caller can receive an old replayed point immediately.

That behavior is acceptable for low-risk display scenarios, but it is risky for NFC distance validation because a stale location can be submitted as the user's current position.

AMap documentation supports a dedicated sign-in location mode and one-shot location configuration, including `AMapLocationPurpose.SignIn`, `setOnceLocation(true)`, `setOnceLocationLatest(true)`, and disabling the SDK location cache when callers need fresher data.

## Goal

Ensure NFC sign-in/sign-out distance validation submits a fresh AMap location, not an app-level cached point or a replayed continuous-location point.

## Non-Goals

- Do not change server distance validation rules.
- Do not change coordinate conversion behavior because server and client both use AMap coordinates.
- Do not rewrite the continuous location upload pipeline.
- Do not add client-side distance calculation.

## Proposed Approach

Add a fresh-location path to the location abstraction and route NFC through it.

1. Add a new method to `LocationFacade`, such as `suspend fun getFreshLocation(timeoutMs: Long = DEFAULT_FRESH_LOCATION_TIMEOUT_MS): LocationResult?`.
2. Keep `getCurrentLocation()` unchanged for existing low-risk callers that benefit from the current cache behavior.
3. Implement `getFreshLocation()` in `DefaultLocationFacade` without reading `LocationStateManager` first.
4. Add an AMap one-shot request path that creates or uses an isolated single-location request and waits for an actual callback instead of reading `shareIn(replay = 1)`.
5. Configure the NFC one-shot request for sign-in:
   - high accuracy mode,
   - one-shot location,
   - latest one-shot result,
   - no SDK location cache,
   - no address data,
   - mock locations disabled,
   - timeout around 10 seconds.
6. Route NFC `getCurrentLocationCoordinates()` through `getFreshLocation()`.
7. If fresh location fails or times out, keep the existing user-facing error flow: show location unavailable and do not submit stale coordinates.
8. After a fresh NFC location succeeds, it may update `LocationStateManager` so later low-risk callers can display the latest known location, but NFC itself must not depend on that cache.

## Data Flow

NFC scan -> permission and location-service checks -> `NfcWorkflowViewModel.getCurrentLocationCoordinates()` -> `LocationFacade.getFreshLocation()` -> AMap one-shot callback -> `longitude`/`latitude` -> `CheckOrder`, `BindLocation`, or `EndOrder`.

The continuous upload flow remains:

Location keep-alive or reporting owner -> `observeLocations()` -> local `order_locations` queue -> `AddPosition`.

## Error Handling

- Permission missing: keep current permission-request flow.
- Location service disabled: keep current settings prompt flow.
- Fresh location timeout or SDK error: return empty coordinates to the NFC layer, which maps to "无法获取位置信息，请稍后重试".
- Do not fall back to stale app cache for NFC, because submitting stale coordinates is worse than asking the user to retry.

## Testing

Add or update focused tests for:

- NFC delegate calls `LocationFacade.getFreshLocation()` instead of `getCurrentLocation()`.
- Fresh location does not read the 5-minute business cache before requesting AMap.
- Fresh location does not use the shared replayed continuous Flow path.
- NFC still maps blank coordinates to the existing location error.
- Existing cached `getCurrentLocation()` behavior remains covered for non-NFC callers.

## Open Decisions

- Use a 10 second NFC fresh-location timeout unless implementation shows the AMap SDK path already imposes a better bounded timeout.
- Do not enforce an accuracy threshold in the first pass. Poor accuracy should be handled by the server distance decision and normal user retry flow.
