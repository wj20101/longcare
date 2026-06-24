# Android Warning Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce Android debug and release build warnings as close to zero as practical while preserving NFC, face verification, release signing, and update behavior.

**Architecture:** Treat warnings by ownership: Gradle script warnings stay in build scripts, Room schema warnings stay in the database module, app API deprecations stay near their call sites, NFC legacy dispatch is isolated behind one helper, and third-party release warnings are documented in narrow release configuration. Prefer Jetpack and AndroidX replacement APIs first; use direct Android APIs only where the current ownership boundary already requires them.

**Tech Stack:** Kotlin 2.4.0, Android Gradle Plugin 9.2.1, Gradle Kotlin DSL, Jetpack Room 2.8.4, Jetpack Compose BOM 2026.06.00, AndroidX Core/Activity, R8, Robolectric, MockK.

## Global Constraints

- `assembleDebug`, `assembleRelease`, and `bundleRelease` should complete successfully.
- Release output should not contain avoidable warnings owned by this project.
- Third-party SDK warnings should be explicitly documented in build configuration when the SDK behavior is outside this project's control.
- NFC sign-in and NFC test behavior must remain compatible with existing tag dispatch flows.
- The release signing configuration must remain unchanged and must continue to use the configured `longcare` release key.
- Jetifier should be disabled only if the Tencent face SDK build and runtime dependency graph still compile safely without it.
- Prefer Jetpack and AndroidX APIs over platform compatibility workarounds whenever Jetpack provides a stable solution.
- Do not redesign NFC sign-in.
- Do not change release signing keys, passwords, aliases, or GitHub Actions secret names.
- Do not upgrade major third-party SDKs unless a warning cannot be resolved without upgrade.
- Do not remove Jetifier blindly.
- Do not suppress all warnings globally.

---

## File Structure

- Modify `constants.gradle.kts`: replace deprecated `extra` delegated setters with `extra.set`.
- Modify `app/build.gradle.kts`: replace deprecated `rootProject.extra` delegated readers, remove misplaced Room plugin/configuration, add release JNI packaging documentation.
- Modify `baselineprofile/build.gradle.kts`: replace deprecated `rootProject.extra` delegated readers.
- Modify `app/dependencies.gradle.kts`: avoid passing `Project` objects through generic dependency notation.
- Modify `core/data/build.gradle.kts`: apply Jetpack Room Gradle plugin and export schemas to the existing checked-in schema directory.
- Modify `gradle.properties`: remove `android.enableJetifier=true` after the already verified debug compile with `-Pandroid.enableJetifier=false`.
- Modify `app/src/main/kotlin/com/ytone/longcare/common/utils/FaceVerificationManager.kt`: replace deprecated `bundleOf` with direct `Bundle` construction.
- Modify `app/src/main/kotlin/com/ytone/longcare/common/utils/ApkInstallUtils.kt`: replace deprecated `ACTION_INSTALL_PACKAGE` with `ACTION_VIEW` while keeping `FileProvider` URI grants.
- Add `app/src/test/kotlin/com/ytone/longcare/common/utils/ApkInstallUtilsIntentTest.kt`: cover install intent action, data, MIME type, clip grant, and flags.
- Modify `app/src/main/kotlin/com/ytone/longcare/features/facecapture/FaceCaptureImagePreviewDialog.kt`: use the current Jetpack Compose transform callback.
- Modify `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/PhotoUploadPreviewDialog.kt`: use the current Jetpack Compose transform callback.
- Modify `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`: replace deprecated `LocalClipboardManager` with `LocalClipboard`.
- Modify `app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenCopyActionTest.kt`: test the suspend clipboard writer contract without deprecated Compose APIs.
- Add `app/src/main/kotlin/com/ytone/longcare/common/utils/NfcIntentActions.kt`: isolate legacy NFC tag action usage and foreground dispatch filters.
- Modify `app/src/main/kotlin/com/ytone/longcare/common/utils/NfcIntentDataUtils.kt`: route all NFC action checks through `NfcIntentActions`.
- Modify `app/src/main/kotlin/com/ytone/longcare/common/utils/NfcUtils.kt`: route legacy foreground dispatch filter creation through `NfcIntentActions`.
- Add `app/src/test/kotlin/com/ytone/longcare/common/utils/NfcIntentActionsTest.kt`: cover NDEF, TECH, legacy TAG, and unrelated actions.
- Modify `app/proguard-rules.pro`: add narrow `-dontwarn` entries for optional third-party classes reported by R8.

---

### Task 1: Gradle Script Deprecation Cleanup

**Files:**
- Modify: `constants.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `baselineprofile/build.gradle.kts`
- Modify: `app/dependencies.gradle.kts`

**Interfaces:**
- Consumes: existing root extra keys `appCompileSdkVersion`, `appTargetSdkVersion`, `appMinSdkVersion`, `appJdkVersion`, `appVersionCode`, `appVersionName`.
- Produces: the same root extra keys with the same values and types, without Gradle 10 deprecation warnings.

- [ ] **Step 1: Replace root extra setters**

Replace the full content of `constants.gradle.kts` with:

```kotlin
extra.set("appCompileSdkVersion", 37)
extra.set("appTargetSdkVersion", 36)
extra.set("appMinSdkVersion", 24)
extra.set("appJdkVersion", 21)
extra.set("appVersionCode", 41)
extra.set("appVersionName", "1.0.6")
```

- [ ] **Step 2: Replace app module extra readers**

In `app/build.gradle.kts`, replace:

```kotlin
val appCompileSdkVersion: Int by rootProject.extra
val appTargetSdkVersion: Int by rootProject.extra
val appMinSdkVersion: Int by rootProject.extra
val appJdkVersion: Int by rootProject.extra
val appVersionCode: Int by rootProject.extra
val appVersionName: String by rootProject.extra
```

with:

```kotlin
val appCompileSdkVersion = rootProject.extra["appCompileSdkVersion"] as Int
val appTargetSdkVersion = rootProject.extra["appTargetSdkVersion"] as Int
val appMinSdkVersion = rootProject.extra["appMinSdkVersion"] as Int
val appJdkVersion = rootProject.extra["appJdkVersion"] as Int
val appVersionCode = rootProject.extra["appVersionCode"] as Int
val appVersionName = rootProject.extra["appVersionName"] as String
```

- [ ] **Step 3: Replace baselineprofile extra readers**

In `baselineprofile/build.gradle.kts`, replace:

```kotlin
val appCompileSdkVersion: Int by rootProject.extra
val appTargetSdkVersion: Int by rootProject.extra
val appMinSdkVersion: Int by rootProject.extra
val appJdkVersion: Int by rootProject.extra
```

with:

```kotlin
val appCompileSdkVersion = rootProject.extra["appCompileSdkVersion"] as Int
val appTargetSdkVersion = rootProject.extra["appTargetSdkVersion"] as Int
val appMinSdkVersion = rootProject.extra["appMinSdkVersion"] as Int
val appJdkVersion = rootProject.extra["appJdkVersion"] as Int
```

- [ ] **Step 4: Replace app dependency project notation**

In `app/dependencies.gradle.kts`, add this helper below the existing `lib` function:

```kotlin
fun DependencyHandler.projectDependency(path: String): Any =
    project(mapOf("path" to path))
```

Then replace the project dependencies inside the `dependencies` block with dependency-handler project dependencies:

```kotlin
dependencies {
    add("baselineProfile", projectDependency(":baselineprofile"))

    addAll(
        "implementation",
        listOf(
            projectDependency(":core:common"),
            projectDependency(":core:data"),
            projectDependency(":core:domain"),
            projectDependency(":core:model"),
            projectDependency(":core:ui"),
            projectDependency(":feature:login"),
            projectDependency(":feature:home"),
            projectDependency(":feature:identification"),
            projectDependency(":feature:location"),
            projectDependency(":feature:photoupload"),
            projectDependency(":feature:servicecountdown")
        )
    )

    add("implementation", platform(lib("compose-bom")))
```

Leave the rest of the `dependencies` block unchanged.

- [ ] **Step 5: Verify Gradle configuration warnings are gone**

Run:

```bash
./gradlew :app:help --warning-mode all
```

Expected: build succeeds and no warning appears for `Constants_gradle`, `app/build.gradle.kts`, `baselineprofile/build.gradle.kts`, or `Using a Project object as a dependency notation`.

- [ ] **Step 6: Commit**

```bash
git add constants.gradle.kts app/build.gradle.kts baselineprofile/build.gradle.kts app/dependencies.gradle.kts
git commit -m "build: remove gradle script deprecations"
```

---

### Task 2: Room Schema Export Ownership

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `core/data/build.gradle.kts`
- Verify: `app/schemas/com.ytone.longcare.data.database.LongCareDatabase/1.json`
- Verify: `app/schemas/com.ytone.longcare.data.database.LongCareDatabase/2.json`

**Interfaces:**
- Consumes: `LongCareDatabase` in `core:data` with `exportSchema = true`.
- Produces: Room schema export configured by the module that owns `LongCareDatabase`, using the existing checked-in `app/schemas` directory.

- [ ] **Step 1: Move Room plugin to data module**

In `core/data/build.gradle.kts`, change the plugins block to:

```kotlin
plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}
```

- [ ] **Step 2: Add Room schema directory in data module**

In `core/data/build.gradle.kts`, add this block after the `android` block:

```kotlin
extensions.configure<androidx.room.gradle.RoomExtension>("room") {
    schemaDirectory("${rootProject.projectDir}/app/schemas")
}
```

- [ ] **Step 3: Remove misplaced app Room plugin**

In `app/build.gradle.kts`, remove this plugin line:

```kotlin
alias(libs.plugins.room)
```

Remove this app-level extension block:

```kotlin
extensions.configure<androidx.room.gradle.RoomExtension>("room") {
    schemaDirectory("$projectDir/schemas")
}
```

- [ ] **Step 4: Verify Room schema export**

Run:

```bash
./gradlew :core:data:kspDebugKotlin :core:data:kspReleaseKotlin --warning-mode all
```

Expected: build succeeds and the Room warning `Schema export directory was not provided` does not appear.

- [ ] **Step 5: Verify schema files remain stable**

Run:

```bash
git diff -- app/schemas
```

Expected: no diff. If Room rewrites formatting only, inspect the diff and keep it only when the schema content still represents `LongCareDatabase` version 1 and version 2.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts core/data/build.gradle.kts app/schemas
git commit -m "build: configure room schema export in data module"
```

---

### Task 3: Jetpack and AndroidX API Deprecation Cleanup

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/common/utils/FaceVerificationManager.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/common/utils/ApkInstallUtils.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/common/utils/ApkInstallUtilsIntentTest.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/facecapture/FaceCaptureImagePreviewDialog.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/PhotoUploadPreviewDialog.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt`
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenCopyActionTest.kt`

**Interfaces:**
- Consumes: Tencent `WbCloudFaceVerifySdk.InputData`, app update install flow, Compose transformable state, NFC test copy action.
- Produces: `internal suspend fun copyNormalizedUidAndRefocus(uid: String?, writeClipboardText: suspend (String) -> Unit, onCopied: () -> Unit, onRequestRefocus: () -> Unit): Boolean`.
- Produces: `internal fun buildInstallIntent(uri: Uri, fileName: String): Intent` using `Intent.ACTION_VIEW`.

- [ ] **Step 1: Replace deprecated `bundleOf`**

In `FaceVerificationManager.kt`, remove:

```kotlin
import androidx.core.os.bundleOf
```

Add:

```kotlin
import android.os.Bundle
```

Replace the `val data = bundleOf(...)` block with:

```kotlin
val data = Bundle().apply {
    putSerializable(WbCloudFaceContant.INPUT_DATA, inputData)
    putString(WbCloudFaceContant.LANGUAGE, WbCloudFaceContant.LANGUAGE_ZH_CN)
    putString(WbCloudFaceContant.COLOR_MODE, WbCloudFaceContant.WHITE)
    putBoolean(WbCloudFaceContant.VIDEO_UPLOAD, false)
    putBoolean(WbCloudFaceContant.PLAY_VOICE, false)
    putBoolean(WbCloudFaceContant.IS_LANDSCAPE, false)
    putString(WbCloudFaceContant.COMPARE_TYPE, WbCloudFaceContant.ID_CARD)
    putBoolean(WbCloudFaceContant.IS_ENABLE_LOG, runtimeConfigProvider.isDebug)
}
```

- [ ] **Step 2: Replace APK install action**

In `ApkInstallUtils.kt`, change the install intent builder from private to internal and replace the deprecated action:

```kotlin
internal fun buildInstallIntent(uri: Uri, fileName: String): Intent = Intent(Intent.ACTION_VIEW).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    clipData = ClipData.newRawUri(fileName, uri)
    setDataAndType(uri, APK_MIME_TYPE)
    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
}
```

- [ ] **Step 3: Add install intent test**

Create `app/src/test/kotlin/com/ytone/longcare/common/utils/ApkInstallUtilsIntentTest.kt`:

```kotlin
package com.ytone.longcare.common.utils

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkInstallUtilsIntentTest {

    @Test
    fun `buildInstallIntent uses view action and grants apk uri access`() {
        val uri = Uri.parse("content://com.ytone.longcare.fileprovider/update.apk")

        val intent = ApkInstallUtils.buildInstallIntent(uri, "update.apk")

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(uri, intent.data)
        assertEquals("application/vnd.android.package-archive", intent.type)
        assertNotNull(intent.clipData)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(true, intent.getBooleanExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false))
    }
}
```

- [ ] **Step 4: Update face capture transform callback**

In `FaceCaptureImagePreviewDialog.kt`, replace:

```kotlin
val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
    scale = (scale * zoomChange).coerceIn(0.5f, 5f)
    offsetX += offsetChange.x
    offsetY += offsetChange.y
}
```

with:

```kotlin
val transformableState = rememberTransformableState { _, zoomChange, offsetChange, _ ->
    scale = (scale * zoomChange).coerceIn(0.5f, 5f)
    offsetX += offsetChange.x
    offsetY += offsetChange.y
}
```

- [ ] **Step 5: Update photo upload transform callback**

In `PhotoUploadPreviewDialog.kt`, replace:

```kotlin
val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
    val newScale = (scale * zoomChange).coerceIn(1f, 5f)
```

with:

```kotlin
val transformableState = rememberTransformableState { _, zoomChange, offsetChange, _ ->
    val newScale = (scale * zoomChange).coerceIn(1f, 5f)
```

Leave the rest of that lambda unchanged.

- [ ] **Step 6: Replace NFC test clipboard API**

In `NfcTestScreen.kt`, remove these imports:

```kotlin
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
```

Add these imports:

```kotlin
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
```

Replace:

```kotlin
val clipboardManager = LocalClipboardManager.current
```

with:

```kotlin
val clipboard = LocalClipboard.current
val coroutineScope = rememberCoroutineScope()
```

Replace the copy callback with:

```kotlin
onR65CCopyResult = {
    coroutineScope.launch {
        copyNormalizedUidAndRefocus(
            uid = r65cPanelState.lastNormalizedUid,
            writeClipboardText = { text ->
                clipboard.setClipEntry(
                    ClipEntry(ClipData.newPlainText("NFC UID", text))
                )
            },
            onCopied = { context.showShortToast("已复制卡号") },
            onRequestRefocus = r65cViewModel::requestRefocus,
        )
    }
},
```

Replace `copyNormalizedUidAndRefocus` with:

```kotlin
internal suspend fun copyNormalizedUidAndRefocus(
    uid: String?,
    writeClipboardText: suspend (String) -> Unit,
    onCopied: () -> Unit,
    onRequestRefocus: () -> Unit,
): Boolean {
    val normalizedUid = uid?.takeIf(String::isNotBlank) ?: return false

    writeClipboardText(normalizedUid)
    onCopied()
    onRequestRefocus()
    return true
}
```

- [ ] **Step 7: Update NFC copy action tests**

Replace the full content of `app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenCopyActionTest.kt` with:

```kotlin
package com.ytone.longcare.features.nfctest.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class NfcTestScreenCopyActionTest {

    @Test
    fun copy_action_copies_uid_to_clipboard_and_requests_refocus() = runTest {
        var copiedText: String? = null
        var copiedCount = 0
        var refocusCount = 0

        val result = copyNormalizedUidAndRefocus(
            uid = "AB12",
            writeClipboardText = { text -> copiedText = text },
            onCopied = { copiedCount += 1 },
            onRequestRefocus = { refocusCount += 1 },
        )

        assertTrue(result)
        assertEquals("AB12", copiedText)
        assertEquals(1, copiedCount)
        assertEquals(1, refocusCount)
    }

    @Test
    fun copy_action_ignores_blank_uid() = runTest {
        var copiedText: String? = null
        var copiedCount = 0
        var refocusCount = 0

        val result = copyNormalizedUidAndRefocus(
            uid = "   ",
            writeClipboardText = { text -> copiedText = text },
            onCopied = { copiedCount += 1 },
            onRequestRefocus = { refocusCount += 1 },
        )

        assertFalse(result)
        assertEquals(null, copiedText)
        assertEquals(0, copiedCount)
        assertEquals(0, refocusCount)
    }
}
```

- [ ] **Step 8: Run focused tests and compile**

Run:

```bash
./gradlew \
  :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.common.utils.ApkInstallUtilsIntentTest' \
  --tests 'com.ytone.longcare.features.nfctest.ui.NfcTestScreenCopyActionTest' \
  :app:compileDebugKotlin \
  --warning-mode all
```

Expected: focused tests pass, Kotlin compile succeeds, and warnings for `bundleOf`, `ACTION_INSTALL_PACKAGE`, `rememberTransformableState`, `LocalClipboardManager`, and deprecated `ClipboardManager` do not appear.

- [ ] **Step 9: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/common/utils/FaceVerificationManager.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/ApkInstallUtils.kt \
  app/src/test/kotlin/com/ytone/longcare/common/utils/ApkInstallUtilsIntentTest.kt \
  app/src/main/kotlin/com/ytone/longcare/features/facecapture/FaceCaptureImagePreviewDialog.kt \
  app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/PhotoUploadPreviewDialog.kt \
  app/src/main/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreen.kt \
  app/src/test/kotlin/com/ytone/longcare/features/nfctest/ui/NfcTestScreenCopyActionTest.kt
git commit -m "refactor: adopt current jetpack api usage"
```

---

### Task 4: NFC Legacy Action Isolation

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/common/utils/NfcIntentActions.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/common/utils/NfcIntentDataUtils.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/common/utils/NfcUtils.kt`
- Create: `app/src/test/kotlin/com/ytone/longcare/common/utils/NfcIntentActionsTest.kt`
- Verify: `app/src/test/kotlin/com/ytone/longcare/common/utils/NfcManagerTagEventTest.kt`

**Interfaces:**
- Produces: `NfcIntentActions.isSupportedTagAction(action: String?): Boolean`.
- Produces: `NfcIntentActions.createLegacyTagDiscoveredFilter(): IntentFilter`.
- Consumes: all callers that previously referenced `NfcAdapter.ACTION_TAG_DISCOVERED` directly.

- [ ] **Step 1: Add NFC action helper**

Create `app/src/main/kotlin/com/ytone/longcare/common/utils/NfcIntentActions.kt`:

```kotlin
package com.ytone.longcare.common.utils

import android.content.IntentFilter
import android.nfc.NfcAdapter

object NfcIntentActions {

    fun isSupportedTagAction(action: String?): Boolean =
        action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == legacyTagDiscoveredAction

    fun createLegacyTagDiscoveredFilter(): IntentFilter =
        IntentFilter(legacyTagDiscoveredAction)

    @Suppress("DEPRECATION")
    private val legacyTagDiscoveredAction: String = NfcAdapter.ACTION_TAG_DISCOVERED
}
```

- [ ] **Step 2: Add NFC action tests**

Create `app/src/test/kotlin/com/ytone/longcare/common/utils/NfcIntentActionsTest.kt`:

```kotlin
package com.ytone.longcare.common.utils

import android.nfc.NfcAdapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcIntentActionsTest {

    @Test
    fun `supported actions include ndef tech and legacy tag actions`() {
        assertTrue(NfcIntentActions.isSupportedTagAction(NfcAdapter.ACTION_NDEF_DISCOVERED))
        assertTrue(NfcIntentActions.isSupportedTagAction(NfcAdapter.ACTION_TECH_DISCOVERED))
        assertTrue(NfcIntentActions.isSupportedTagAction("android.nfc.action.TAG_DISCOVERED"))
    }

    @Test
    fun `unsupported actions are rejected`() {
        assertFalse(NfcIntentActions.isSupportedTagAction(null))
        assertFalse(NfcIntentActions.isSupportedTagAction("android.intent.action.VIEW"))
    }
}
```

- [ ] **Step 3: Update NFC intent parsing**

In `NfcIntentDataUtils.kt`, keep `import android.nfc.NfcAdapter` because `NfcAdapter.EXTRA_TAG` and `NfcAdapter.EXTRA_NDEF_MESSAGES` are still current constants.

Replace the action checks in `getTagFromIntent` and `getNdefMessagesFromIntent` with:

```kotlin
if (NfcIntentActions.isSupportedTagAction(intent.action)) {
    return IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
}
```

and:

```kotlin
if (NfcIntentActions.isSupportedTagAction(intent.action)) {
    val rawMessages = IntentCompat.getParcelableArrayExtra(
        intent,
        NfcAdapter.EXTRA_NDEF_MESSAGES,
        NdefMessage::class.java
    )
    return rawMessages?.filterIsInstance<NdefMessage>()?.toTypedArray()
}
```

- [ ] **Step 4: Update foreground dispatch filters**

In `NfcUtils.kt`, replace:

```kotlin
val tagIntentFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
val techIntentFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
```

with:

```kotlin
val tagIntentFilter = NfcIntentActions.createLegacyTagDiscoveredFilter()
val techIntentFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
```

- [ ] **Step 5: Run NFC tests and compile**

Run:

```bash
./gradlew \
  :app:testDebugUnitTest \
  --tests 'com.ytone.longcare.common.utils.NfcIntentActionsTest' \
  --tests 'com.ytone.longcare.common.utils.NfcManagerTagEventTest' \
  :app:compileDebugKotlin \
  --warning-mode all
```

Expected: tests pass, Kotlin compile succeeds, and warning output contains no direct warning for `NfcAdapter.ACTION_TAG_DISCOVERED` outside `NfcIntentActions.kt`.

- [ ] **Step 6: Search for remaining deprecated NFC action references**

Run:

```bash
rg -n "ACTION_TAG_DISCOVERED" app/src/main app/src/test
```

Expected: only these two matches remain:

```text
app/src/main/kotlin/com/ytone/longcare/common/utils/NfcIntentActions.kt
app/src/test/kotlin/com/ytone/longcare/common/utils/NfcIntentActionsTest.kt
```

- [ ] **Step 7: Commit**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/common/utils/NfcIntentActions.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/NfcIntentDataUtils.kt \
  app/src/main/kotlin/com/ytone/longcare/common/utils/NfcUtils.kt \
  app/src/test/kotlin/com/ytone/longcare/common/utils/NfcIntentActionsTest.kt
git commit -m "refactor: isolate legacy nfc tag action"
```

---

### Task 5: Third-Party Release Warning Configuration

**Files:**
- Modify: `app/proguard-rules.pro`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: release R8 output that reports optional missing classes `com.amap.ams.gnss.GnssSoftLocator` and `net.jafama.FastMath`.
- Consumes: AGP native strip output for prebuilt third-party `.so` files that cannot be stripped by this build.
- Produces: narrow release configuration with comments for each owned suppression category.

- [ ] **Step 1: Add narrow R8 optional-class suppressions**

In `app/proguard-rules.pro`, below the existing AMap keep rules, add:

```proguard
# Optional classes referenced by bundled third-party SDK code. App code does not
# call these classes directly; keep the suppression narrow so new missing classes
# still fail loudly in release builds.
-dontwarn com.amap.ams.gnss.GnssSoftLocator
-dontwarn net.jafama.FastMath
```

- [ ] **Step 2: Add explicit native debug symbol packaging**

In `app/build.gradle.kts`, inside the `android { ... }` block after `buildFeatures`, add:

```kotlin
packaging {
    jniLibs {
        keepDebugSymbols +=
            setOf(
                "**/libBugly_Native.so",
                "**/libBugly_Native_idasc.so",
                "**/libYTCommonLiveness.so",
                "**/libandroidx.graphics.path.so",
                "**/libapssdk.so",
                "**/libdatastore_shared_counter.so",
                "**/libface_detector_v2_jni.so",
                "**/libimage_processing_util_jni.so",
                "**/libkyctoolkit.so",
                "**/libsurface_util_jni.so",
                "**/libturingmfa.so",
                "**/libweconvert.so",
                "**/libweyuv.so",
            )
    }
}
```

- [ ] **Step 3: Verify release warning output**

Run:

```bash
./gradlew :app:assembleRelease :app:bundleRelease --warning-mode all
```

Expected: build succeeds, R8 output does not report missing classes for `GnssSoftLocator` or `FastMath`, and AGP does not print `Unable to strip the following libraries`.

- [ ] **Step 4: Commit**

```bash
git add app/proguard-rules.pro app/build.gradle.kts
git commit -m "build: document third party release warnings"
```

---

### Task 6: Jetifier Removal and Final Verification

**Files:**
- Modify: `gradle.properties`
- Verify: release APK/AAB outputs under `app/build/outputs`

**Interfaces:**
- Consumes: successful debug compile already observed with `./gradlew :app:assembleDebug -Pandroid.enableJetifier=false --warning-mode all`.
- Produces: project default with Jetifier disabled by removing the property instead of overriding it per command.

- [ ] **Step 1: Remove Jetifier property and stale comment**

In `gradle.properties`, remove these three lines:

```properties
# Tencent Huiyan face SDK AAR still references legacy support-library classes.
# Keep Jetifier enabled so those references are rewritten to AndroidX at build time.
android.enableJetifier=true
```

- [ ] **Step 2: Verify full debug build without Jetifier**

Run:

```bash
./gradlew :app:assembleDebug --warning-mode all
```

Expected: build succeeds and no `android.enableJetifier=true is deprecated` warning appears.

- [ ] **Step 3: Verify full release package flow**

Run:

```bash
./gradlew :app:assembleRelease :app:bundleRelease --warning-mode all
```

Expected: build succeeds and release output does not contain warnings owned by project code or narrow third-party configuration.

- [ ] **Step 4: Verify release signing still uses longcare**

Run:

```bash
./gradlew :app:signingReport
```

Expected: release variant remains signed with alias/configuration for `longcare`. Do not edit signing plugins, keystore paths, aliases, passwords, or GitHub secret names.

- [ ] **Step 5: Run warning searches**

Run:

```bash
rg -n "LocalClipboardManager|ClipboardManager|bundleOf|ACTION_INSTALL_PACKAGE|ACTION_TAG_DISCOVERED|rememberTransformableState \\{ zoomChange|android.enableJetifier=true" \
  app/src/main app/src/test gradle.properties
```

Expected: no matches except the intentional `ACTION_TAG_DISCOVERED` reference inside `NfcIntentActions.kt` and the string literal inside `NfcIntentActionsTest.kt`.

- [ ] **Step 6: Run final repository checks**

Run:

```bash
git diff --check
git status --short
```

Expected: `git diff --check` prints no whitespace errors. `git status --short` shows only intended modified files before the final commit.

- [ ] **Step 7: Commit**

```bash
git add gradle.properties
git commit -m "build: remove jetifier default"
```

---

## Self-Review Checklist

- Spec coverage: Gradle warning cleanup, Room schema export, R8 missing classes, native strip output, Kotlin deprecations, NFC compatibility, Jetifier validation, and release signing verification are each covered by a task.
- Placeholder scan: this plan contains concrete file paths, code snippets, commands, and expected results for every implementation step.
- Type consistency: `buildInstallIntent`, `copyNormalizedUidAndRefocus`, `NfcIntentActions.isSupportedTagAction`, and `NfcIntentActions.createLegacyTagDiscoveredFilter` are defined before later steps rely on them.
