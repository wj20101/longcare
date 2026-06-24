# Location Optimization Design

## Context

Recent production location data shows that server distance calculation matches the submitted coordinates: recomputing Haversine distance from the CSV differs from the server `distance` field by less than 0.05 meters. The severe distance deviation is more consistent with coordinate-system or source-data mismatch, especially when an apparent WGS84 reference coordinate is compared with AMap GCJ-02 client uploads.

The current client code has already moved the production location source toward AMap-only behavior:

- NFC sign-in/sign-out requests use `LocationFacade.getFreshLocation()`.
- Fresh NFC location uses an isolated AMap one-shot request with sign-in purpose and SDK cache disabled.
- Service-period location upload observes the continuous AMap Flow.
- Location upload writes to a local `order_locations` queue before calling `/V1/Service/AddPostion`.
- In the current working tree, `LocationResult` carries AMap diagnostic fields and reporting skips stale replayed samples.

The remaining risks are mostly about traceability and policy. The local queue and server API still carry only latitude and longitude for upload, so the system cannot reliably distinguish fresh vs delayed samples, AMap vs historical non-AMap coordinates, or high-quality vs low-quality positioning after the fact.

## Goal

Improve the location pipeline so client behavior is consistent, diagnosable, and resilient around NFC sign-in and service-period location upload, while preserving the current AMap-based source strategy.

## Non-Goals

- Do not reintroduce Android system `LocationManager` as a production fallback.
- Do not perform client-side coordinate conversion for fresh AMap locations.
- Do not change server distance validation rules in the first client-focused phase.
- Do not block uploads based only on accuracy unless the business explicitly accepts missed location points.
- Do not redesign the NFC workflow UI beyond existing loading and retry states.

## Current Flow

NFC flow:

1. `NfcWorkflowScreenHandlers` checks location permission and the system location switch.
2. NFC scan enters a loading state for location acquisition.
3. `NfcWorkflowViewModel` asks `LocationFacade.getFreshLocation()`.
4. `ContinuousAmapLocationManager.getFreshLocation()` creates an isolated AMap one-shot client.
5. The returned longitude and latitude are sent to start, bind, or end-order APIs.

Service upload flow:

1. Starting service tracking acquires location keep-alive.
2. `LocationReportingManager` collects `LocationFacade.observeLocations()`.
3. Each accepted sample is inserted into `order_locations`.
4. Pending and failed rows are uploaded to `/V1/Service/AddPostion`.
5. Successful rows are retained for a limited period, then cleaned.

## Design Options

### Option A: Client metadata and documentation hardening

Keep the location behavior unchanged, but make the data chain explicit and diagnosable.

- Persist `locationTime`, `coordType`, `locationType`, `trustedLevel`, `accuracy`, and `provider` with each queued upload row.
- Add these fields to Bugly diagnostics and local queue records.
- Maintain `LocationState.currentOrderId` and `startTime` when tracking starts and stops.
- Update location module documentation to remove stale references to system fallback.
- Add focused tests for metadata propagation and state updates.

This is the recommended first step because it is low risk and directly improves the ability to explain production deviations.

### Option B: Upload quality and stale-sample policy

Add explicit policy around which collected samples are allowed to reach the local queue and server.

- Keep skipping stale replayed continuous samples before enqueue.
- Add a configurable maximum upload age for failed queue rows, or mark them expired instead of uploading after a long delay.
- Consider a retry worker that flushes failed rows when network becomes available.
- Add Bugly events for expired uploads and repeated upload failure windows.

This reduces misleading delayed uploads, but it changes business behavior because some historical samples may be dropped.

### Option C: End-to-end coordinate governance

Coordinate with the server so all parties can identify coordinate type and sample time.

- Extend `/V1/Service/AddPostion` to accept sample time, coordinate type, provider, accuracy, and optional SDK quality fields.
- Ensure server-side reference coordinates are explicitly stored or marked as AMap GCJ-02.
- Add a migration or correction plan for historical reference coordinates that were saved in WGS84.
- Update server distance diagnostics to log both raw comparison and coordinate-type assumptions.

This is the most complete fix for the CSV-style 500 meter deviation, but it needs server schema and API work.

## Recommended Design

Implement Option A first, with the stale-sample skip from Option B retained. Then plan Option B and Option C as separate follow-up changes.

The first client change should make every location sample self-describing from SDK callback to local queue:

- `LocationResult` remains the in-memory carrier for coordinates and AMap quality metadata.
- `OrderLocationEntity` and the Room entity gain matching optional metadata fields with safe defaults.
- `LocationReportingManager.enqueueLocation()` writes the original SDK sample time and quality metadata, not only the local enqueue time.
- Bugly sample events include the same metadata, so a Bugly report can be correlated with a queued row.
- `LocationStateManager` gets an explicit tracking lifecycle method that records the active order and start time.

No extra validation should reject normal AMap samples in this first phase. If the SDK returns a successful location, the client should keep using it, while diagnostics record whether it was fresh, trusted, or suspicious.

## Data Model Changes

Add nullable or defaulted fields to location queue models:

- `location_time`: SDK sample time in milliseconds, when available.
- `coord_type`: SDK coordinate type, expected to identify AMap coordinate behavior.
- `location_type`: AMap location type.
- `trusted_level`: AMap trusted level.
- `provider`: already present, continue using values such as `amap_continuous`.
- `accuracy`: already present, continue persisting it.
- `timestamp`: keep as local enqueue time.

This separates "when the SDK sampled the position" from "when the client queued the row".

## API Strategy

Phase 1 does not require API changes. The client keeps sending the current order id, longitude, and latitude.

For Phase 3, add a backward-compatible request shape to the server. The server may ignore new fields initially, then start using them for diagnostics and distance validation once deployed.

## Error Handling

- NFC fresh location failure continues to surface as the existing location unavailable message.
- Missing permission and disabled location service continue using the existing permission/settings flow.
- Queue write failures continue to report to Bugly and do not crash the app.
- Upload failures continue to mark rows failed.
- Expired-row behavior is not introduced until Option B is explicitly implemented.

## Testing

Add or update focused tests for:

- Continuous AMap and fresh AMap fill `LocationResult` metadata.
- Reporting queue rows preserve SDK sample time and quality fields.
- Tracking start records current order id and start time; stop clears them.
- Stale replayed samples are skipped before enqueue.
- Existing successful upload and retry behavior remains unchanged.
- NFC still calls fresh location and does not fall back to cached current location.

## Rollout

1. Ship Option A as a client-only diagnostic and data consistency release.
2. Observe Bugly sample, jump, stale-skip, and upload failure events.
3. Compare server distance outliers with `coordType`, `locationTime`, `provider`, and `accuracy`.
4. Decide whether Option B should drop expired local rows or upload them with explicit sample time.
5. Coordinate Option C with server changes for historical coordinate cleanup and richer upload metadata.

## Success Criteria

- Every uploaded location row can be traced back to SDK sample time, provider, coordinate type, and quality fields locally.
- NFC location acquisition continues to use fresh AMap one-shot behavior.
- Continuous upload no longer uploads stale replayed first samples.
- The location module documentation matches production behavior.
- Future distance-deviation reports can be separated into server reference-coordinate issues, delayed upload issues, and SDK quality issues.
