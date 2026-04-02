# Login Agreement Confirmation Design

## Context

The login screen currently supports:

- phone number input
- verification code input
- login submission
- linked agreement text for the user agreement and privacy policy

It does not yet support explicit consent state or a login-time confirmation flow when the user has not agreed to the required policies.

## Goal

Add a clear consent interaction to the login page so that:

- the user can explicitly indicate agreement
- login is blocked until agreement is confirmed
- agreement links still open normally
- the flow remains lightweight and local to the screen

## Approved Decisions

### 1. Consent Control

Add a checkbox to the left of the agreement text.

- Only tapping the checkbox itself toggles the checked state.
- Tapping the agreement text does not toggle the checkbox.
- Tapping the linked text for the user agreement or privacy policy still opens the corresponding web page.

### 2. Login Interception

When the user taps the login button:

- if the agreement checkbox is already selected, continue with the existing login flow
- if the agreement checkbox is not selected, show a confirmation dialog

### 3. Confirmation Dialog Behavior

If the user has not agreed and taps login, show a dialog that clearly states that login requires agreement to the user agreement and privacy policy.

Dialog actions:

- `取消`: dismiss the dialog and do not log in
- `确认并同意`: set the checkbox to selected, dismiss the dialog, and immediately continue the login flow

### 4. State Ownership

Keep the new consent state local to `LoginScreenContent`.

Local state to add:

- whether the agreement is checked
- whether the confirmation dialog is visible

This is a screen interaction concern, not a domain or repository concern, so it should not be moved into `LoginViewModel` at this stage.

### 5. Submit Flow Structure

Centralize login submission behind one local function, for example `submitLogin()`.

That function should:

- check the consent state
- either show the dialog or proceed with login

This avoids duplicating login logic between:

- the main login button click
- the dialog confirmation action

## UI Structure Changes

### Agreement Area

Refactor the current agreement area into a composed section with:

- a checkbox on the left
- the existing linked agreement text on the right

Behavior rules:

- checkbox click toggles consent
- plain text area is non-toggle
- annotated agreement links remain clickable

### Login Button

Keep the current enablement logic based on phone number and verification code validity.

Do not require the checkbox to be selected to enable the button. The agreement check happens when the button is tapped, because the approved interaction requires a reminder dialog rather than passive disablement.

## Error Handling and Edge Cases

- If agreement URLs are unavailable, keep the existing fallback toast behavior.
- If the user confirms in the dialog, consent is granted before login is invoked.
- If the user cancels in the dialog, consent remains unchecked.
- Repeated login taps after consent should follow the normal login path with no dialog.

## Scope Boundaries

This design does not include:

- persistence of agreement consent across app launches
- analytics or event tracking
- changes to backend contracts
- changes to login validation rules beyond the consent confirmation flow

## Files Expected To Change

- `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreenComponents.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreenContentSections.kt`

If string resources are added or adjusted, related resource files may also change.

## Validation Plan

Verify the following:

1. Tapping only the checkbox toggles consent.
2. Tapping the agreement links opens the correct pages and does not toggle consent.
3. With valid phone number and code but unchecked consent, tapping login shows the dialog.
4. Tapping `取消` dismisses the dialog and does not log in.
5. Tapping `确认并同意` checks the checkbox and continues login.
6. With consent already checked, tapping login follows the existing login flow directly.

## Rationale

This design matches the requested interaction while keeping the implementation local, predictable, and easy to verify. It improves legal confirmation clarity without expanding ViewModel responsibility or changing existing login architecture.
