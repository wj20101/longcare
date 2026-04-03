# Bottom Safe Area Adaptation Design

## Context

On the current physical device, the bottom primary action in the NFC workflow screen is partially obscured by the system navigation bar in three-button navigation mode. The issue is not isolated to a single screen. Several feature screens use similar bottom action layouts with fixed bottom padding, which makes them vulnerable under edge-to-edge rendering and non-gesture navigation modes.

Representative examples include:

- `features/nfc/ui/NfcWorkflowBottomBar.kt`
- `features/servicecountdown/ui/ServiceCountdownBottomActionBar.kt`
- `features/photoupload/ui/PhotoUploadBottomActionBar.kt`
- `features/selectservice/ui/SelectServiceScreenSupportViews.kt`
- `features/identification/ui/IdentificationScreenScaffoldSections.kt`
- `features/servicecomplete/ui/ServiceCompleteScreen.kt`

The codebase currently mixes several bottom action layout styles:

- `Scaffold(bottomBar = ...)`
- `BoxScope.align(Alignment.BottomCenter)`
- overlay-style camera and workflow layouts

That means a one-line global fix at the `Scaffold` layer is unlikely to cover all cases safely.

## Goal

Introduce a consistent bottom safe-area adaptation strategy so that bottom action buttons remain fully visible above the system navigation bar across affected pages, without changing existing business behavior or causing excessive empty bottom spacing on other navigation modes.

## Non-Goals

This design does not include:

- a global edge-to-edge refactor for the full app
- unrelated visual redesign of bottom action areas
- changes to button wording, enablement logic, or workflow state transitions
- migration of all screens to one shared scaffold architecture

## Approved Direction

Use a shared bottom safe-area container for bottom action regions, and adopt it across the set of pages that currently use fixed bottom padding for primary actions.

This is preferred over:

- page-by-page ad hoc fixes, which would drift over time
- a global `Scaffold` inset rewrite, which would miss overlay-based screens and increase risk

## Design

### 1. Shared Container

Add a reusable UI component in the shared app UI layer, under the existing `app/ui/components` area.

Suggested responsibility:

- apply bottom navigation-bar-safe spacing
- preserve horizontal spacing conventions
- support an optional visual background layer such as a bottom gradient
- stay presentation-only, with no business logic

A candidate shape is a composable similar to `BottomSafeActionContainer`, with parameters for:

- `modifier`
- horizontal padding
- top padding
- extra bottom padding
- optional background content or background style
- child content slot

The key rule is that navigation bar inset handling becomes centralized instead of hardcoded as `padding(bottom = 24.dp)` or `padding(vertical = 32.dp)` inside each feature.

### 2. Inset Strategy

Use navigation bar insets directly for bottom action regions.

The intended behavior is:

- preserve each screen's visual baseline spacing
- add system bottom inset on top of that spacing when needed
- avoid double-applying insets at both the page and button-container layers

For screens that already own their own gradient background around the bottom action area, the gradient wrapper should remain, but its bottom spacing should be calculated through the shared safe-area approach.

### 3. Adoption Scope

This change should cover the known bottom action patterns most likely to be obscured:

- `NfcWorkflowBottomBar`
- `ServiceCountdownBottomActionBar`
- `PhotoUploadBottomActionBar`
- `SelectServiceBottomActions`
- `IdentificationBottomBar`
- `ServiceCompleteScreen` bottom action area

In addition, any nearby screen using `align(Alignment.BottomCenter)` or a custom bottom overlay for its main action should be evaluated during implementation and included if it matches the same risk pattern.

### 4. Per-Screen Treatment

The implementation should preserve current visuals per screen:

- Gradient-backed bottom bars keep their gradients
- Plain surface-backed bottom bars remain plain
- Button size, shape, text, and action logic stay unchanged

The adaptation should only change how the bottom container is positioned relative to system navigation insets.

`ServiceCompleteScreen` belongs to the plain surface-backed group. It should be treated the same way as other scaffold-owned bottom bars: preserve the existing `Surface` wrapper semantics and migrate only the internal spacing responsibility to the shared safe-area approach.

### 5. Why Not Fix Only NFC

The current NFC screen is the most visible example, but the underlying problem is structural. The same fixed-padding pattern appears in multiple places. Fixing only one page would leave the codebase inconsistent and would almost certainly lead to repeated regressions on adjacent screens.

## File Targets

Expected new shared component area:

- `app/src/main/kotlin/com/ytone/longcare/ui/components/...`

Expected directly affected files:

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowBottomBar.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownBottomActionBar.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/PhotoUploadBottomActionBar.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/selectservice/ui/SelectServiceScreenSupportViews.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/identification/ui/IdentificationScreenScaffoldSections.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/servicecomplete/ui/ServiceCompleteScreen.kt`

Additional pages may be included during implementation if they use the same bottom action pattern and show the same overlap risk.

## Acceptance Criteria

1. On the current real device in three-button navigation mode, the bottom primary action in the NFC workflow screen is fully visible and not overlapped by the system navigation bar.
2. The same safe-area behavior is applied to the agreed set of bottom action screens.
3. Existing button behavior, state transitions, and click flows do not change.
4. Gesture navigation or devices with different inset profiles do not show obviously excessive bottom whitespace.
5. Screens with bottom gradients preserve the existing visual direction while gaining safe-area spacing.

## Verification Strategy

Implementation should verify at least:

- local Kotlin compilation for modified modules
- affected Android UI or instrumentation validation where available
- real-device screenshot checks for NFC and at least one other adapted page

Because the issue is device- and navigation-mode-dependent, real-device validation is part of the core acceptance path, not just a nice-to-have.

## Risks and Controls

### Risk: Double Insets

If a parent screen already applies bottom system insets and the child bottom action container also applies them, the page may end up with too much bottom whitespace.

Control:

- keep inset ownership explicit
- prefer applying navigation bar spacing at the dedicated bottom action container layer
- inspect each adapted screen for existing inset handling before attaching the shared container

### Risk: Overlay Screens Behave Differently

Camera and capture flows often use full-screen overlays rather than standard scaffold content.

Control:

- adopt the same rule set, but do not force every screen into the same layout abstraction
- where reuse is awkward, preserve the layout structure and apply the same safe-area calculation directly

### Risk: Over-Broad Refactor

This problem could tempt a broad “layout cleanup” pass.

Control:

- keep scope limited to bottom action visibility and safe-area adaptation
- avoid unrelated restructuring

## Rationale

This design addresses the actual root cause: fixed bottom spacing in a mixed edge-to-edge environment. It treats the issue as a shared layout concern rather than a one-off page bug, while still keeping the implementation narrow enough to ship safely.
