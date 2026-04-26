# Login Log Param Info Optimization Design

## Context

The home login-log payload is currently built by
`DefaultHomeLoginLogInfoProvider`.

Current mapping:

- `phoneSystem`: fixed string `Android`
- `phoneVersion`: Android OS release from `Build.VERSION.RELEASE`
- `networkType`: resolved from active connectivity
- `networkOperator`: resolved from `TelephonyManager.networkOperatorName`

The backend still expects the existing `LoginLogParamModel` JSON field names.
This change only optimizes the values sent in those fields.

## Goal

Improve the information quality of `LoginLogParamModel`:

- `phoneSystem` should contain a readable phone model value.
- `phoneVersion` should contain the app version name plus app version code.

## Field Mapping

### `phoneSystem`

Use a readable device label built from Android build metadata:

```text
<manufacturer> <model>
```

Examples:

- `HUAWEI NOH-AN00`
- `Xiaomi 2211133C`
- `samsung SM-S9180`

The value should be trimmed after concatenation so empty manufacturer or model
parts do not leave leading or trailing spaces.

If both manufacturer and model are unavailable, return an empty string. This
matches the current model fallback style and avoids sending a misleading
constant value.

### `phoneVersion`

Use the app version shown by the client:

```text
<BuildConfig.VERSION_NAME>.<BuildConfig.VERSION_CODE>
```

Example:

```text
1.0.6.29
```

This matches the existing profile screen display format and ensures the login
log identifies the exact installed package version, including release rebuilds
that share the same version name.

### Unchanged Fields

Keep the existing network fields unchanged:

- `networkType`
- `networkOperator`

## Architecture

Keep the change inside `DefaultHomeLoginLogInfoProvider`.

Reasons:

- The provider already owns login-log device and network payload construction.
- There is only one current caller.
- The API model and repository boundary do not need to change.

No new abstraction is needed unless future features reuse the same device label
or app version label outside login logging.

## Data Flow

1. `HomeSharedViewModel.reportHomeEntry()` asks `HomeLoginLogInfoProvider` for a
   `LoginLogParamModel`.
2. `DefaultHomeLoginLogInfoProvider.build()` creates the model.
3. `phoneSystem` is built from `Build.MANUFACTURER` and `Build.MODEL`.
4. `phoneVersion` is built from `BuildConfig.VERSION_NAME` and
   `BuildConfig.VERSION_CODE`.
5. The existing repository call sends the payload without changing API field
   names.

## Error Handling

This provider should remain lightweight and non-throwing:

- Device label construction should tolerate blank values.
- Version construction should use generated `BuildConfig` constants.
- Existing network fallback behavior remains unchanged.

## Testing And Verification

Implementation should verify:

- `phoneSystem` is no longer the fixed `Android` value.
- `phoneSystem` uses manufacturer plus model and trims blank parts.
- `phoneVersion` uses `VERSION_NAME.VERSION_CODE`.
- Existing home login-log flow tests still pass.

If direct JVM tests for Android static `Build` values are awkward in this
module, prefer a small pure helper function for formatting and test that helper
directly while keeping the public provider behavior unchanged.

## Out Of Scope

- Renaming `LoginLogParamModel` fields.
- Changing backend JSON keys.
- Changing login-log request timing or retry behavior.
- Changing network type or network operator values.
