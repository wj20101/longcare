# NFC Location Progress Feedback Design

Date: 2026-04-27

## Context

Users report that during start-order NFC sign-in, the card reader vibrates after scanning but the page appears unchanged. Scanning again can look the same. After killing and reopening the app, the flow works.

The current flow can enter location permission or location acquisition after the card is read. That middle phase is not represented clearly in the UI, so users cannot tell that the app already recognized the card and is waiting on location or permission.

## Goals

- Show immediate feedback after a card is read.
- Explain when the flow is waiting for location permission.
- Show a distinct loading state while fetching location.
- Show a distinct loading state while submitting the check-in/check-out request.
- Keep the existing permission purpose dialog and backend behavior.

## Non-Goals

- Do not change NFC foreground dispatch or reader registration behavior.
- Do not change location provider strategy or timeout behavior.
- Do not change order check-in/check-out API contracts.
- Do not redesign the NFC page.

## Recommended Approach

Add explicit NFC business progress states and map them to short loading copy in the existing content card.

The flow should be:

1. Card read: show `已识别卡片，正在获取定位...`.
2. Missing location permission: show `已识别卡片，请授予定位权限后继续`, then show the existing location purpose dialog.
3. Permission granted: show `正在获取定位...`.
4. Location acquired and API request starts: show `正在提交打卡...`.
5. Success and failure continue through the existing success, error, and retry paths.

This makes the user's mental model match the actual flow: card recognition succeeded, and the remaining wait is location or submission.

## State Design

Extend `NfcSignInUiState.Loading` so it can carry a display reason, for example:

- `ReadingCard`
- `WaitingForLocationPermission`
- `FetchingLocation`
- `Submitting`

The UI should avoid hard-coded boolean loading guesses. Instead, the screen maps the loading reason to a string resource and spinner.

Recommended text:

- `ReadingCard`: `已识别卡片，正在获取定位...`
- `WaitingForLocationPermission`: `已识别卡片，请授予定位权限后继续`
- `FetchingLocation`: `正在获取定位...`
- `Submitting`: `正在提交打卡...`

If implementation risk is lower with string resources directly in the state, use a small enum plus resolver rather than raw strings in view model logic.

## Data Flow

1. `AppEvent.TagScanned` arrives.
2. The scan delegate sets loading state to card-recognized/fetching-location before requesting location.
3. If permission is missing:
   - set loading state to waiting-for-location-permission;
   - trigger the current purpose notice flow;
   - do not show a generic NFC failure state.
4. If location is available:
   - call the existing start/end order action;
   - order workflow sets loading state to submitting before calling the API.
5. Existing success, confirm-dialog, and error states remain unchanged.

## UI Behavior

The existing NFC content card should show:

- Spinner for loading states.
- Loading copy from the current loading reason.
- Existing success and failure visuals unchanged.
- Existing reader readiness copy when the flow is idle.

The bottom bar should keep its existing behavior: no new action is needed during loading.

## Error Handling

- Permission denied: clear the waiting state and show the existing location permission denial toast or error prompt.
- Location service disabled: keep the existing settings prompt and show a clear error state if the flow cannot continue.
- Location fetch failure: show the existing NFC error dialog message.
- API failure: keep the existing order workflow failure behavior.

## Testing

Add focused unit coverage around the scan helper/delegate:

- Reading a card sets or requests the card-recognized loading state before location work.
- Missing location permission maps to the waiting-for-permission loading state.
- Location success proceeds to start/end order as before.

Add or update UI policy coverage if practical:

- Loading states are not mapped to idle copy.
- Loading copy is sourced from explicit state, not inferred from reader readiness.

Manual smoke checks:

- Fresh install start-order flow: scan card -> `已识别卡片，正在获取定位...` -> permission notice -> `请授予定位权限后继续`.
- Grant permission: flow resumes with `正在获取定位...` and then `正在提交打卡...`.
- Slow location: loading text remains visible.
- API success: existing next-step navigation still works.
