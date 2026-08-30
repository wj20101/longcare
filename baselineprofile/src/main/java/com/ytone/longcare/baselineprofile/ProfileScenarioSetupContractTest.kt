package com.ytone.longcare.baselineprofile

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class ProfileScenarioSetupContractTest {
    @Test
    fun missingAndUnknownScenarioIdsFailClosedWithOneObservableNode() {
        val driver = ProfileScenarioDriver.create()

        driver.clearPackageData()
        val missing = driver.launchSetupRequest(null)
        assertEquals(SetupOutcome.FAILED, missing.outcome)
        assertEquals(0, missing.completeNodeCount)
        assertEquals(1, missing.failedNodeCount)
        assertTrue(missing.message.contains("missing-or-unknown-scenario"))

        driver.forceStopTarget()
        driver.clearPackageData()
        val unknown = driver.launchSetupRequest("not_a_profile_scenario")
        assertEquals(SetupOutcome.FAILED, unknown.outcome)
        assertEquals(0, unknown.completeNodeCount)
        assertEquals(1, unknown.failedNodeCount)
        assertTrue(unknown.message.contains("missing-or-unknown-scenario"))
        driver.forceStopTarget()
    }

    @Test
    fun legalRepositoryBackedStatesCompleteWithoutProductionData() {
        val driver = ProfileScenarioDriver.create()
        listOf(
            ProfileScenario.LOGGED_OUT,
            ProfileScenario.CARE_HOME,
            ProfileScenario.SALES_HOME,
            ProfileScenario.CARE_SERVICE_RECORDS,
            ProfileScenario.SALES_CUSTOMERS,
        ).forEach(driver::prepare)
    }

    @Test
    fun setupPermissionIsSignatureProtectedAndNonMatchingCallerIsDenied() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetAppId = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId not passed as instrumentation runner arg")
        val permission = instrumentation.context.packageManager.getPermissionInfo(
            PROFILE_PERMISSION,
            0,
        )
        assertEquals(
            PermissionInfo.PROTECTION_SIGNATURE,
            permission.protection and PermissionInfo.PROTECTION_MASK_BASE,
        )
        assertEquals(
            PackageManager.SIGNATURE_NO_MATCH,
            instrumentation.context.packageManager.checkSignatures(
                targetAppId,
                SHELL_PACKAGE,
            ),
        )
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            instrumentation.context.packageManager.checkPermission(
                PROFILE_PERMISSION,
                SHELL_PACKAGE,
            ),
        )
        val setupActivity = instrumentation.context.packageManager.getActivityInfo(
            ComponentName(
                targetAppId,
                "$targetAppId.performance.ProfileScenarioSetupActivity",
            ),
            0,
        )
        assertEquals(PROFILE_PERMISSION, setupActivity.permission)
    }

    companion object {
        private const val PROFILE_PERMISSION =
            "com.ytone.longcare.permission.PROFILE_SCENARIO_SETUP"
        private const val SHELL_PACKAGE = "com.android.shell"
    }
}
