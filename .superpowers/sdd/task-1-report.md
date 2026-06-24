# Task 1 Report: Gradle Script Deprecation Cleanup

## Scope

Updated only the files owned by the brief:

- `constants.gradle.kts`
- `app/build.gradle.kts`
- `baselineprofile/build.gradle.kts`
- `app/dependencies.gradle.kts`

No Android business code was changed.

## Changes made

### 1. Root extra setters

Replaced delegated `extra(...)` setters in `constants.gradle.kts` with explicit `extra.set(...)` calls, preserving the exact existing values:

- `appCompileSdkVersion = 37`
- `appTargetSdkVersion = 36`
- `appMinSdkVersion = 24`
- `appJdkVersion = 21`
- `appVersionCode = 41`
- `appVersionName = "1.0.6"`

### 2. App module extra readers

Replaced `by rootProject.extra` delegated reads in `app/build.gradle.kts` with indexed reads and casts:

- `rootProject.extra["appCompileSdkVersion"] as Int`
- `rootProject.extra["appTargetSdkVersion"] as Int`
- `rootProject.extra["appMinSdkVersion"] as Int`
- `rootProject.extra["appJdkVersion"] as Int`
- `rootProject.extra["appVersionCode"] as Int`
- `rootProject.extra["appVersionName"] as String`

### 3. Baseline profile module extra readers

Replaced `by rootProject.extra` delegated reads in `baselineprofile/build.gradle.kts` with indexed reads and casts for:

- `appCompileSdkVersion`
- `appTargetSdkVersion`
- `appMinSdkVersion`
- `appJdkVersion`

### 4. App project dependency notation

Added a helper below the existing `lib` function in `app/dependencies.gradle.kts` and routed the owned project dependencies through it.

## Deviation from brief

The exact brief snippet for `DependencyHandler.projectDependency` did compile, but it did not remove the `Using a Project object as a dependency notation` warning during verification in this repository.

I tried the smallest Gradle Kotlin DSL equivalents available in this script context:

1. `project(mapOf("path" to path))`
2. `project(path)`
3. `project.dependencies.project(path)`
4. `project.dependencyFactory.createProjectDependency(path)`

The checked-in version uses:

```kotlin
fun DependencyHandler.projectDependency(path: String): Any =
    project.dependencyFactory.createProjectDependency(path)
```

This is the most direct non-deprecated API available from Gradle 9.6 in this script context, but the warning still appears during configuration.

## Verification

Ran the required command:

```bash
./gradlew :app:help --warning-mode all
```

Result:

- Build succeeded.
- Warnings for `Constants_gradle`, `app/build.gradle.kts`, and `baselineprofile/build.gradle.kts` no longer appeared.
- Residual warnings still appeared for:
  - `android.enableJetifier=true` deprecation
  - `Using a Project object as a dependency notation has been deprecated`

## Assessment

The owned Gradle script extra-property deprecations were cleaned up as requested, and all required files were updated with preserved SDK/app version values.

The remaining project-dependency warning could not be eliminated within the owned files using the briefed snippet or the closest direct Gradle 9.6 API equivalents. The generated problems report attributes the remaining warnings to Android plugin configuration (`com.android.internal.application` / `com.android.internal.library`) rather than the cleaned extra-property scripts.
