# NFC Bugly Error Reporting Design

## Purpose

NFC sign-in and sign-out failures are now shown inline with the real error message. The next step is to make sure user-visible NFC failures are visible in Bugly even when the failure is not caused by an API exception or API business failure.

The design must avoid duplicate Bugly reports for the same failure. Existing API and exception paths already call `DiagnosticEventTracker.trackError()`, which posts a caught exception to Bugly.

## Scope

In scope:

- NFC workflow errors that end in `NfcSignInUiState.Error`.
- A Bugly fallback report for user-visible errors that have not already been reported.
- Safe diagnostic metadata only: order ids, plan ids, sign-in mode, scan source, stage/event names, message text, NFC id length/hash, and boolean presence flags.
- Focused tests for reporting flags and fallback behavior.

Out of scope:

- Reporting every permission prompt dismissal or every transient reader state.
- Changing Bugly initialization or global diagnostic infrastructure.
- Sending raw NFC ids, names, identity numbers, photos, image keys, tokens, or full URLs.
- Changing the inline error UI behavior from the previous commit.

## Current State

Most network and business failures already report through `trackNfcFailure()` or `trackNfcException()`:

- start-order check failures and exceptions
- end-order pre-check failures and exceptions
- end-order submit failures and exceptions
- bind-location failures and exceptions
- order-detail lookup failures and exceptions before location activation

Some user-visible errors can still reach `NfcSignInUiState.Error` through direct `showError()` calls without an explicit Bugly report. Examples include location service unavailable, location retry/resume failures, and other frontend workflow errors.

## Recommended Approach

Extend `NfcSignInUiState.Error` with reporting metadata:

- `buglyReported: Boolean = false`
- optional lightweight source/stage metadata if needed by tests or reporting helpers

When a failure path already calls `trackNfcFailure()` or `trackNfcException()`, create the Error state with `buglyReported = true`.

When a user-visible failure is set without previous reporting, route it through one centralized helper that:

1. Calls `DiagnosticEventTracker.trackError()`.
2. Uses category `nfc_workflow`.
3. Uses event `nfc_user_visible_error`.
4. Includes safe extras such as `orderId`, `planId`, `signInMode`, `message`, and workflow source/stage when available.
5. Sets `NfcSignInUiState.Error(..., buglyReported = true)` after the report is posted.

## Architecture

Keep the reporting concern in the NFC ViewModel/delegate layer, not in Compose UI.

The UI should remain a pure renderer of state:

- `NfcWorkflowScreen` extracts `Error.message`.
- `SignInContentCard` displays that message.
- UI composables do not call Bugly or diagnostic APIs.

The NFC workflow layer owns reporting because it has access to workflow context and knows whether an API failure was already reported.

## Data Flow

1. NFC scan, location, API, or reader flow fails.
2. The workflow chooses one of two paths:
   - Already-reported path: call existing `trackNfcFailure()` or `trackNfcException()`, then set `Error(buglyReported = true)`.
   - Unreported user-visible path: call the new centralized helper, which reports and sets `Error(buglyReported = true)`.
3. UI renders the error message inline.
4. Retry calls `resetState()` and returns the workflow to `Initial`.

## Error Handling

Bugly reporting must be best effort. If diagnostic reporting itself throws, `DiagnosticEventTracker` already catches and logs the reporting failure. The NFC workflow should still show the user-visible error.

Cancellation exceptions must continue to propagate in coroutine paths that already do so. The fallback reporting helper should not swallow cancellation from surrounding workflow code.

## Testing

Add focused unit tests around helper-level behavior:

- Existing API failure paths produce `NfcSignInUiState.Error` marked as reported.
- Existing API exception paths produce `NfcSignInUiState.Error` marked as reported.
- A direct user-visible error path calls the fallback reporting helper and ends with a reported Error state.
- Retry/reset still clears the Error state.

If direct Bugly calls are hard to assert because `DiagnosticEventTracker` is an object, test the deterministic state metadata and keep Bugly invocation behind a small internal helper that can be exercised without network or device dependencies.

## Acceptance Criteria

- Any final `NfcSignInUiState.Error` from the NFC workflow is either already reported or is reported by the fallback helper.
- Existing API/exception reports are not duplicated.
- No sensitive user data or raw NFC ids are sent to Bugly.
- The inline error display continues to show the real message.
- `./gradlew :app:compileDebugKotlin` passes.
- NFC-related unit tests pass.
