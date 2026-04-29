# NFC Entry Location and Dashboard Company Refresh Design

## Background

Two flows need behavior adjustments:

1. `NfcWorkflowScreen` currently requests location only after a card scan. Even with the recent fast-location timeout work, first location acquisition can still make the first scan feel slow.
2. `MainDashboardScreen` shows the company name in the top-left header. `MainDashboardViewModel.loadCompanyName()` currently skips loading once a non-empty value exists, so returning to the home dashboard may keep stale company data.

The desired behavior is deliberately simple:

- NFC location preparation should happen when entering the NFC workflow page, including permission request and location-service handling.
- Company name should be fetched once when entering the home dashboard and should update cache/UI with the latest API result. It should not refetch on every recomposition or ordinary UI refresh.

## Current Behavior

### NFC

`NfcWorkflowScreen` builds `rememberNfcWorkflowLocationHandlers()`, then passes `getCurrentLocationCoordinates()` into `NfcWorkflowEffects()`. The location request is consumed by scan handling, so permission request, location-service checks, and location acquisition happen after the scan event.

Current effects:

- NFC reader/listener starts on page entry.
- Location is requested only when a tag is scanned.
- Missing permission triggers the existing location-purpose dialog during the scan flow.
- If location returns blank coordinates, scan continues with empty longitude/latitude.

### Dashboard Company Name

`MainDashboardScreen` calls `mainDashboardViewModel.loadCompanyName()` in `LaunchedEffect(Unit)`.

`MainDashboardViewModel.loadCompanyName()` returns early if `_companyName` is not empty. This prevents duplicate loads during one ViewModel lifetime, but it also prevents a fresh API-backed value when the user enters the dashboard again and the ViewModel still has a cached value.

`SystemConfigManager.getCompanyName()` reads through the system-config cache. If local config exists, it returns the cached value and triggers a background refresh. For the dashboard requirement, the implementation should ensure the UI receives the latest fetched value after entering the page.

## Design

### NFC Entry Location Preparation

Add an explicit page-entry location preparation action to the existing NFC location handler boundary.

The handler should expose a function such as `prepareLocationOnEntry()` that is called from a `LaunchedEffect` when `NfcWorkflowScreen` enters for an `orderKey/signInMode` pair.

Behavior:

1. If location permission is missing, show the existing location-purpose dialog immediately.
2. When the user confirms, request location permissions through the existing launcher.
3. If permission is granted, start the same location acquisition used by NFC scan handling to warm `LocationFacade`/`LocationStateManager` cache.
4. If location service is disabled, open location settings immediately through the existing helper.
5. If permission and service are already available, immediately request the current location.
6. The page-entry warmup result should not submit NFC business actions by itself. It only prepares permission/service/cache.
7. Card scan behavior remains tolerant:
   - Use the existing `getCurrentLocationCoordinates()` path.
   - If warmup has populated cache, scan should return faster.
   - If warmup is still running or fails, scan may still short-wait and then continue with empty longitude/latitude.

This keeps business correctness in the scan flow while moving expensive permission/location setup to page entry.

### Permission Dialog Ownership

Reuse the current `PermissionPurposeDialog` and launchers in `rememberNfcWorkflowLocationHandlers()`.

The implementation should avoid adding a second independent permission flow. Entry warmup and scan resume should share the same location-only permission launcher so denied/granted behavior remains consistent.

If a scan happens while permission is still pending, the existing pending scan mechanism should still work: scan stores pending data and resumes after permission is granted.

### Dashboard Company Name Refresh

Change the dashboard company-name loading semantics from "load only if blank" to "load once per dashboard entry".

Implementation direction:

- `MainDashboardScreen` should call the dashboard ViewModel when the dashboard is entered, using a stable entry effect rather than every recomposition.
- `MainDashboardViewModel` should expose a method that always requests the latest company name for that entry and updates `_companyName`.
- Existing UI should continue to display the previous value while the request is in flight.
- If the request fails or returns blank, keep the previous non-empty value rather than flashing empty text.
- `SystemConfigManager` may need a force-refresh method for company name so the dashboard does not only receive stale local cache while a background refresh silently updates storage.

The goal is: every home dashboard entry asks the API-backed system config for the latest value, updates cache, then refreshes UI once the latest value is available.

## Out Of Scope

- Do not redesign the NFC workflow UI.
- Do not block NFC scanning until location warmup completes.
- Do not change the NFC API contract for empty longitude/latitude.
- Do not refetch company name on every recomposition, list refresh, or order refresh.
- Do not broadly rewrite `SystemConfigManager`; only add the minimum API needed for forced company-name refresh if current lazy loading cannot satisfy the requirement.

## Testing

### Unit Tests

- Update `MainDashboardViewModelTest`:
  - entering/load method requests company name each time it is called;
  - latest non-empty response updates state;
  - blank or failed refresh keeps previous value if one exists.
- Add or update source-level policy tests for NFC if direct Compose tests are too heavy:
  - `NfcWorkflowScreen` calls a page-entry location preparation effect;
  - scan flow still calls `getCurrentLocationCoordinates()`.

### Manual Android Verification

- Fresh install or cleared permissions:
  - enter NFC page;
  - location-purpose dialog appears before scanning;
  - granting permission starts location preparation;
  - scanning after permission proceeds without the original first-scan permission delay.
- Location service disabled:
  - enter NFC page;
  - app opens location settings prompt/path before scan.
- Permission already granted:
  - enter NFC page;
  - no permission dialog appears;
  - first scan should use warmed/cacheable location path when available.
- Dashboard:
  - enter home dashboard;
  - company name requests latest config and updates top-left text;
  - navigate away and enter dashboard again;
  - another entry-level request is made;
  - recomposition/order refresh alone does not trigger repeated company-name requests.
