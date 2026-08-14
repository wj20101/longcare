# Release Hidden Validation Entry Design

## Background

The login logo currently opens a hidden validation sheet only in Debug builds. The sheet, its real
navigation callbacks, the face-verification activity, and the NFC/R65C activity are implemented in
the Debug source set, while the Release source set provides no-op replacements. As a result, the
Release APK cannot provide the device and business verification functions required by users.

The approved product behavior is:

- Debug and Release both include the real validation functions.
- The entry remains hidden behind a long press on the login-page logo.
- Release does not add a launcher icon, normal navigation destination, or other visible shortcut.
- Release must not gain mock data, a mock network interceptor, or development-only environment
  behavior.

## Goals

- Make the hidden login validation entry and all five existing validation actions available in both
  Debug and Release.
- Reuse one implementation across build variants so behavior cannot drift.
- Keep the normal login flow and ordinary logo interaction unchanged.
- Use formal user-facing validation copy rather than development-oriented wording.
- Verify inclusion from the built Release artifact, not only from source compilation.

## Non-Goals

- Adding a desktop launcher entry to Release.
- Adding a visible entry to the home page or another authenticated screen.
- Changing any production API contract, request payload, authentication behavior, or app key.
- Moving `MockInterceptor` or other Debug-only network behavior into Release.
- Redesigning the face, camera, NFC, or R65C business implementation.

## Approved Architecture

### Shared source ownership

All code and resources required by the user-facing validation functions belong in the `main` source
set because Android build variants always combine `main` with their selected build-type source set.
This includes:

- the Material 3 login validation sheet
- the real navigation callbacks for camera, legacy face verification, and manual face capture
- the face-verification validation activity
- the NFC/R65C validation activity and its UI/input-state implementation
- the formal validation strings
- the unexported activity declarations required by both variants

The current Release no-op sheet and navigation callback implementations are removed. Validation
classes moved out of the Debug source set use the app shell's production-neutral
`presentation.validation` package instead of the frozen legacy `features` tree or a `debug` package.

Debug-only dependency injection and `MockInterceptor` stay in `src/debug` and remain excluded from
Release.

### Entry behavior

`LoginScreenContent` always supplies the logo long-press callback in both build variants. A long
press opens the shared bottom sheet; a normal tap has no new behavior. Dismissing the sheet returns
to the unchanged login screen.

The sheet contains the existing real actions:

1. face verification using an order ID and the documented real face-check API
2. phone NFC or R65C external reader verification
3. the shared production camera, watermark, compression, and preview flow
4. the existing backup face-verification flow
5. the existing manual face-capture flow

No action substitutes mock responses or test customer/order data.

### Manifest and exposure rules

The shared validation activities are declared in the main manifest with `android:exported="false"`,
portrait orientation, and the app theme. They can be started only from inside the app.

Release has no `MAIN`/`LAUNCHER` intent filter for a validation activity. The existing Debug-only
secondary face-verification launcher remains available for developer convenience; its exported
declaration and launcher intent filter stay exclusively in the Debug manifest overlay.

### User-facing wording

The bottom sheet title is `功能验证`. Descriptions explain the capability being verified without
using wording such as `开发联调`, `Mock`, or internal implementation terminology. Vendor or transport
details are shown only when they help the operator distinguish a real hardware path, such as `R65C`.

The standalone face and NFC pages follow the same wording rule. Internal test tags may retain stable
names so existing automation does not become brittle.

## Data and Error Flow

The shared entry only dispatches to existing real flows; it does not add a new repository or API
layer.

- Navigation-backed actions use the existing `NavController` helpers.
- Activity-backed actions use explicit internal intents.
- Face verification continues to require the existing authenticated session and valid order ID.
- Camera, NFC, and R65C permission/capability errors continue to use their existing UI handling.
- A missing device capability must produce the existing explanatory state rather than a crash.

## Compatibility and Security

- The logo long-press gesture remains compatible with the current Compose semantics and adaptive
  login layout.
- Activities remain portrait-only, matching the application UI.
- No component is externally exported in Release.
- No credentials, keys, mock responses, or undocumented API parameters are added.
- Moving the implementation to `main` changes packaging ownership only; it does not change protocol
  behavior.

## Verification

Implementation is complete only when all of the following pass:

- focused tests for the hidden login entry and NFC/R65C state logic
- Debug and Release Kotlin compilation
- Debug unit tests and both Debug/Release lint tasks
- Debug instrumentation on an emulator, including long-pressing the login logo and asserting all
  five actions are present
- an acceptance Release APK build using the repository's approved non-production signing/build
  flags
- inspection of the merged Release manifest confirming both internal validation activities exist,
  are `exported=false`, and have no launcher intent filter
- inspection of the Release APK/resources confirming the shared validation UI and strings are
  packaged
- inspection confirming Debug-only mock/network classes are not packaged in Release

## Documentation Impact

Update the architecture UI and capability maps so they describe the validation entry as a shared
Debug/Release capability with a hidden login-logo entry, while explicitly documenting that mock
network behavior remains Debug-only.
