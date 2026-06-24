# Task 3: Write Metadata During Reporting and Maintain Tracking State

Status: DONE_WITH_CONCERNS

## Summary

Implemented the reporting/state wiring for Task 3 on top of the existing uncommitted reporting/test work:

- Added `LocationStateManager.startTracking(orderKey: OrderKey)` and `stopTracking()`.
- Preserved `updateTrackingState(isTracking: Boolean)` for compatibility, delegating the stop path through `stopTracking()`.
- Updated `LocationReportingManager` to call `startTracking()` / `stopTracking()`.
- Preserved the existing partial Task 3 diagnostics already present in the workspace:
  - stale replay skip before enqueue
  - sample Bugly diagnostics
  - suspicious jump Bugly diagnostics
- Persisted Task 1 metadata into queued `OrderLocationEntity` rows:
  - `coordType`
  - `locationType`
  - `trustedLevel`
  - `locationTime`

## Files Changed

- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationStateManager.kt`
- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/reporting/LocationReportingManager.kt`
- `app/src/test/kotlin/com/ytone/longcare/features/location/manager/LocationStateManagerTest.kt`
- `app/src/test/kotlin/com/ytone/longcare/features/location/reporting/LocationReportingManagerTest.kt`

## Test Coverage Added / Updated

### `LocationStateManagerTest`

- `startTracking records active order and start time`
- `stopTracking clears active order and start time`

### `LocationReportingManagerTest`

- verifies reporting start uses `locationStateManager.startTracking(orderKey)`
- verifies reporting stop uses `locationStateManager.stopTracking()`
- verifies queued rows include metadata fields written from `LocationResult`
- preserves stale replay skip coverage before enqueue
- preserves retry coverage for failed upload -> success
- preserves cancellation behavior coverage during upload

## Commands Run

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.features.location.manager.LocationStateManagerTest' \
  --tests 'com.ytone.longcare.features.location.reporting.LocationReportingManagerTest'
```

Result: PASS

## Concern

The task brief asked for the literal `locationTime = 1_717_000_000_123L` in the success-path reporting test. On June 24, 2026, that timestamp is older than the required stale replay threshold and is therefore correctly skipped by `LocationReportingManager.shouldSkipStaleLocation(...)`. To keep both requirements true at once:

- the dedicated stale replay test covers old/stale timestamps being skipped
- the success-path metadata test uses a fresh runtime `locationTime` so the sample is enqueued and uploaded

No production behavior was relaxed to accommodate the test.
