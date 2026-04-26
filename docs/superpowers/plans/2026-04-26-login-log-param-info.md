# Login Log Param Info Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update home login-log payload values so `phoneSystem` reports a readable device label and `phoneVersion` reports the exact app version name plus version code.

**Architecture:** Keep the change inside `DefaultHomeLoginLogInfoProvider`, which already owns login-log payload construction. Add small pure formatting helpers in the same file so blank handling and version formatting can be tested without mocking Android static `Build` values.

**Tech Stack:** Kotlin, Android `Build`, generated app `BuildConfig`, JUnit4, MockK, Gradle app unit tests.

---

## File Structure

- Modify: `app/src/main/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProvider.kt`
  - Import app `BuildConfig`.
  - Replace fixed `phoneSystem = "Android"` with `formatPhoneSystem(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty())`.
  - Replace Android OS version `phoneVersion = Build.VERSION.RELEASE.orEmpty()` with `formatPhoneVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)`.
  - Add pure helper functions for device label and app version label formatting.
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProviderTest.kt`
  - Update existing provider assertions away from `"Android"`.
  - Add focused tests for `formatPhoneSystem(...)`.
  - Add focused test for `formatPhoneVersion(...)`.

No API model, repository, or backend JSON key changes are needed.

---

### Task 1: Update Provider Tests First

**Files:**
- Modify: `app/src/test/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProviderTest.kt`

- [ ] **Step 1: Replace the existing test file with expectations for the new field semantics**

Use this complete file:

```kotlin
package com.ytone.longcare.features.home.reporting

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import com.ytone.longcare.BuildConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DefaultHomeLoginLogInfoProviderTest {

    @Test
    fun `build returns wifi payload with readable device and app version`() {
        val context = mockk<Context>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>()
        val telephonyManager = mockk<TelephonyManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { context.getSystemService(Context.TELEPHONY_SERVICE) } returns telephonyManager
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        every { telephonyManager.networkOperatorName } returns "Carrier"

        val provider = DefaultHomeLoginLogInfoProvider(context)

        val result = provider.build()

        assertNotEquals("Android", result.phoneSystem)
        assertEquals(formatPhoneVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), result.phoneVersion)
        assertEquals("WIFI", result.networkType)
        assertEquals("Carrier", result.networkOperator)
    }

    @Test
    fun `build falls back gracefully when services unavailable`() {
        val context = mockk<Context>(relaxed = true)

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns null
        every { context.getSystemService(Context.TELEPHONY_SERVICE) } returns null

        val provider = DefaultHomeLoginLogInfoProvider(context)

        val result = provider.build()

        assertNotEquals("Android", result.phoneSystem)
        assertEquals(formatPhoneVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), result.phoneVersion)
        assertEquals("NONE", result.networkType)
        assertEquals("", result.networkOperator)
    }

    @Test
    fun `formatPhoneSystem joins manufacturer and model`() {
        val result = formatPhoneSystem(
            manufacturer = "HUAWEI",
            model = "NOH-AN00",
        )

        assertEquals("HUAWEI NOH-AN00", result)
    }

    @Test
    fun `formatPhoneSystem trims blank manufacturer or model parts`() {
        assertEquals("NOH-AN00", formatPhoneSystem(manufacturer = " ", model = "NOH-AN00"))
        assertEquals("HUAWEI", formatPhoneSystem(manufacturer = "HUAWEI", model = " "))
        assertEquals("", formatPhoneSystem(manufacturer = " ", model = " "))
    }

    @Test
    fun `formatPhoneVersion joins version name and version code`() {
        val result = formatPhoneVersion(versionName = "1.0.6", versionCode = 29)

        assertEquals("1.0.6.29", result)
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails before implementation**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.ytone.longcare.features.home.reporting.DefaultHomeLoginLogInfoProviderTest
```

Expected: FAIL because `formatPhoneSystem(...)` and `formatPhoneVersion(...)` are not defined yet, and current provider still returns the old field values.

---

### Task 2: Implement Login-Log Field Formatting

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProvider.kt`

- [ ] **Step 1: Replace the provider implementation with the new formatting logic**

Use this complete file:

```kotlin
package com.ytone.longcare.features.home.reporting

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.model.LoginLogParamModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultHomeLoginLogInfoProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HomeLoginLogInfoProvider {

    override fun build(): LoginLogParamModel = LoginLogParamModel(
        phoneSystem = formatPhoneSystem(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
        ),
        phoneVersion = formatPhoneVersion(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
        ),
        networkType = resolveNetworkType(),
        networkOperator = resolveNetworkOperator(),
    )

    private fun resolveNetworkType(): String {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "NONE"
        val network = connectivityManager.activeNetwork ?: return "NONE"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "UNKNOWN"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "UNKNOWN"
        }
    }

    private fun resolveNetworkOperator(): String {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return ""
        return telephonyManager.networkOperatorName.orEmpty()
    }
}

internal fun formatPhoneSystem(
    manufacturer: String,
    model: String,
): String = listOf(
    manufacturer.trim(),
    model.trim(),
).filter { it.isNotEmpty() }
    .joinToString(separator = " ")

internal fun formatPhoneVersion(
    versionName: String,
    versionCode: Int,
): String = "$versionName.$versionCode"
```

- [ ] **Step 2: Run the focused test and verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.ytone.longcare.features.home.reporting.DefaultHomeLoginLogInfoProviderTest
```

Expected: PASS for all tests in `DefaultHomeLoginLogInfoProviderTest`.

---

### Task 3: Verify The Change

**Files:**
- Verify: `app/src/main/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProvider.kt`
- Verify: `app/src/test/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProviderTest.kt`

- [ ] **Step 1: Run Kotlin/Gradle focused checks**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.ytone.longcare.features.home.reporting.DefaultHomeLoginLogInfoProviderTest
```

Expected: PASS.

- [ ] **Step 2: Run diff whitespace validation**

Run:

```bash
git diff --check -- app/src/main/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProvider.kt app/src/test/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProviderTest.kt
```

Expected: no output and exit code `0`.

- [ ] **Step 3: Inspect the final diff**

Run:

```bash
git diff -- app/src/main/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProvider.kt app/src/test/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProviderTest.kt
```

Expected:

- `phoneSystem` uses `Build.MANUFACTURER` plus `Build.MODEL`.
- `phoneVersion` uses `BuildConfig.VERSION_NAME` plus `BuildConfig.VERSION_CODE`.
- Network field behavior is unchanged.
- Existing unrelated worktree change in `build-logic/convention/src/main/kotlin/AndroidAppSigningTxFaceConventionPlugin.kt` is not included.

- [ ] **Step 4: Commit only the implementation files**

Run:

```bash
git add app/src/main/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProvider.kt app/src/test/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProviderTest.kt
git commit -m "fix: update login log device info"
```

Expected: a commit containing only the provider and its test.
