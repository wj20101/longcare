# R65C Business Fallback Reader State Fix Design

## Related Docs

- index: [`../README.md`](../README.md)
- refined fallback design: [`2026-04-11-r65c-business-fallback-integration-refine-design.md`](2026-04-11-r65c-business-fallback-integration-refine-design.md)
- UX fix design: [`2026-04-11-r65c-business-fallback-ux-fix-design.md`](2026-04-11-r65c-business-fallback-ux-fix-design.md)
- earlier fallback design: [`2026-04-10-r65c-business-fallback-integration-design.md`](2026-04-10-r65c-business-fallback-integration-design.md)

## Context

The recent fallback work already moved the formal workflow in the right direction:

- non-NFC devices use `ScanMode.EXTERNAL_RFID`
- strict validation happens before `TagScanned(..., ScanSource.EXTERNAL_RFID)` is published
- the workflow no longer needs a visible text-entry model for fallback scanning

Recent device testing exposed two remaining detail problems:

1. If the reader is already connected before entering the page, the page can still show `读卡器未就绪`.
2. During recognition, the user receives too little feedback and may assume scanning is not working.

These are not architecture failures. They are reader-state and feedback failures.

## Goal

Fix the formal fallback experience so that:

- the page reflects the actual reader readiness when it first opens
- a reader that is already connected is shown as ready immediately
- the user gets clear, lightweight feedback while a card is being recognized
- the existing external-reader business boundary remains unchanged

## Non-Goals

This design does not:

- redesign the fallback scan pipeline again
- replace the `EXTERNAL_RFID` event path
- add a scan-mode toggle
- add heavy progress UIs or a new screen
- move business validation out of the external-reader boundary

## Approved Direction

Keep the current fallback integration, but tighten two things:

1. initial reader-state synchronization
2. visible `Reading` feedback

The fix should remain small and local. It should not become a new reader framework.

## Design

### 1. Reader Ready State Must Be True on Entry

The formal workflow should not depend only on future connection events to know whether the external reader is ready.

If the reader is already connected before the page opens, the page should reflect that immediately.

That means the fallback path needs an explicit “current readiness” sync when the workflow starts.

### 2. Reader Boundary Should Expose Current Readiness

The cleanest place to answer “is the reader ready right now?” is the external-reader boundary.

Instead of making the workflow infer readiness from stale UI state, the reader layer should expose a small synchronous readiness query.

Conceptually:

- workflow starts external reader
- workflow asks the reader boundary for current readiness
- workflow uses that result to seed `ReaderUiState`

This preserves the boundary:

- reader layer knows the device
- workflow layer knows presentation and business flow

### 3. `ReaderUiState.Reading` Must Be User-Visible

`ReaderUiState.Reading` should no longer be treated as an internal transient only.

It should have two user-visible effects:

- status copy changes to a recognition-in-progress message
- the status region shows a lightweight visual activity indicator

Recommended copy direction:

- `Ready`: `读卡器已就绪`
- `Reading`: `正在识别，请保持卡片靠近读卡器`
- `Disconnected`: `读卡器未就绪`

### 4. Feedback Should Stay Lightweight

The user needs confidence, not a full-screen loader.

Recommended UI behavior:

- keep the current `SignInContentCard`
- reuse the existing status row area
- show a small progress indicator or animated status element in `Reading`
- do not add a modal, dialog, or page transition

The page should still feel like the same workflow screen.

### 5. No Change to Business Publication Rules

This fix should not change:

- strict Tag ID validation
- duplicate suppression
- invalid-streak escalation
- `TagScanned(..., ScanSource.EXTERNAL_RFID)` publication rules

Those remain correct and should stay in the external-reader boundary.

### 6. State Semantics After the Fix

After this fix, the intended meanings become:

- `Disconnected`
  - reader is not ready now
- `Ready`
  - reader is available now and waiting for scan
- `Reading`
  - a scan is actively being assembled or recognized and the user should see feedback
- `DeviceError`
  - repeated invalid scans or reader failure
- `NotRequired`
  - NFC path is active instead

## Testing Strategy

### Unit Tests

Add tests for:

- initial external-reader readiness sync when the device is already connected
- `Reading` state transitions back correctly after recognition
- readiness sync does not affect `SYSTEM_NFC`

### UI / Copy Tests

Add or update tests so:

- `Ready` and `Reading` use different copy
- `Reading` has a visible activity signal

### Manual Verification

Manual verification must confirm:

- entering the page with the reader already connected shows ready state immediately
- tapping into the page does not require replugging or reconnecting to become ready
- scanning shows a short but obvious “正在识别” feedback
- success and failure business flows still behave as before

## Consequences

This design keeps the current fallback integration and tightens the places where real users notice friction.

That is the correct trade-off because:

- the current business flow is already functional
- the remaining problem is trust in the interaction
- better reader-state truth and better feedback are enough to remove that friction

## Next Step

If this design is approved, the next document should be an implementation plan that:

1. adds a current-reader-readiness query to the external-reader boundary
2. uses that query to seed `ReaderUiState` when the workflow starts
3. adds explicit `Reading` copy and a lightweight visual feedback state
4. verifies that already-connected readers appear ready immediately
