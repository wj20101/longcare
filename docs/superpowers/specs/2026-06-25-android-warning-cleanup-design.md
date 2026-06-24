# Android Warning Cleanup Design

## Goal

Reduce Android debug and release build warnings as close to zero as practical without changing business behavior, especially NFC sign-in, face verification, release signing, and location reporting.

## Background

The latest local release build succeeds, but emits several warning categories:

- Room reports that `LongCareDatabase` has `exportSchema = true` while the schema export directory is not configured for the `core:data` module.
- R8 reports missing optional classes from bundled third-party SDK code, including AMap-related references.
- AGP reports that several native libraries cannot be stripped and are packaged as-is.
- Kotlin reports deprecations for APK installation, NFC tag discovery constants, Compose transform/clipboard APIs, and `bundleOf`.
- Gradle reports `android.enableJetifier=true` as deprecated, while the project comment says the Tencent face SDK still references legacy support-library classes.

## Desired Behavior

1. `assembleDebug`, `assembleRelease`, and `bundleRelease` should complete successfully.
2. Release output should not contain avoidable warnings owned by this project.
3. Third-party SDK warnings should be explicitly documented in build configuration when the SDK behavior is outside this project's control.
4. NFC sign-in and NFC test behavior must remain compatible with existing tag dispatch flows.
5. The release signing configuration must remain unchanged and must continue to use the configured `longcare` release key.
6. Jetifier should be disabled only if the Tencent face SDK build and runtime dependency graph still compile safely without it.

## Proposed Approach

### Room Schema Export

Move Room schema export ownership to the module that declares the database:

- Apply the Room Gradle plugin in `core:data`.
- Configure its schema directory to the existing schema location unless the implementation proves a module-local schema path is cleaner.
- Keep the checked schema for `LongCareDatabase` version 2 in sync with the actual database.

This addresses the current release warning and prevents future Room migrations from relying on manually derived schema artifacts.

### R8 and Native Strip Warnings

Treat third-party optional classes and prebuilt native libraries as explicit build configuration:

- Add narrow `-dontwarn` rules for known optional SDK references only after confirming the referenced classes are not directly used by app code.
- Add `doNotStrip` packaging rules only for the native libraries AGP already cannot strip.
- Do not add broad wildcard suppression such as `-dontwarn **` or `doNotStrip "**/*.so"`.

This keeps release output quiet while preserving useful warnings for future regressions.

### Kotlin and Android API Deprecations

Replace low-risk deprecated usages with current APIs:

- APK install intent: use a modern install flow while preserving `FileProvider` URI permission behavior.
- `bundleOf`: replace with direct `Bundle` construction where type safety warnings are emitted.
- Compose transform APIs: migrate to the newer transform callback shape while preserving pan and zoom behavior.
- Compose clipboard APIs: migrate the NFC test copy action to the current clipboard API and keep copy behavior covered by tests.

### NFC Deprecation Handling

Handle NFC tag action deprecations conservatively:

- Keep support for existing platform-dispatched NFC intents that the app already receives.
- Prefer modern foreground dispatch or reader-mode configuration only where it does not break current tests and sign-in flow.
- If Android still requires `ACTION_TAG_DISCOVERED` as a compatibility fallback, isolate the deprecated reference in one compatibility helper with a clear suppression and test coverage.

The target is to avoid scattered deprecation warnings without removing real-world NFC compatibility.

### Jetifier

Try disabling Jetifier in a controlled validation pass:

- Run debug and release compilation with `android.enableJetifier=false`.
- If compilation succeeds and Tencent face SDK dependencies do not require rewriting legacy support references, remove the property.
- If compilation fails or produces unresolved legacy support references, keep Jetifier enabled and update the comment to explain the current SDK constraint.

Jetifier removal is desirable, but not at the cost of breaking the face SDK.

## Verification

Run these checks before implementation is considered complete:

- `./gradlew :app:assembleDebug`
- `./gradlew :app:assembleRelease :app:bundleRelease`
- Focused unit tests for changed behavior, including NFC utilities, NFC test copy action, install intent utility, and any touched Compose preview/dialog logic.
- `./gradlew :app:signingReport` to confirm release signing still uses the `longcare` release config.
- `git diff --check`

If warning output remains, it must be documented as an accepted third-party or toolchain limitation with a narrow configuration rationale.

## Non-Goals

- Do not redesign NFC sign-in.
- Do not change release signing keys, passwords, aliases, or GitHub Actions secret names.
- Do not upgrade major third-party SDKs unless a warning cannot be resolved without an upgrade.
- Do not remove Jetifier blindly.
- Do not suppress all warnings globally.

## Risks

- NFC deprecation changes can affect physical tag dispatch behavior, so compatibility tests and code isolation matter.
- Jetifier removal may compile locally but still fail in CI if a different Tencent SDK source is selected.
- R8 suppressions can hide real missing classes if written too broadly.
- Room schema path changes can create noisy schema diffs if the export path is not chosen carefully.
