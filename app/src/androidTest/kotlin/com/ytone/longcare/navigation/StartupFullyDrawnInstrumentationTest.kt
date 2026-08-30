package com.ytone.longcare.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.MainActivity
import com.ytone.longcare.app.MainApplication
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupFullyDrawnInstrumentationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fullyDrawn_isHeldUntilExpectedRootThenReportsOnceAcrossStateChanges() {
        val readiness = mutableStateOf(
            StartupRootReadiness(StartupRoot.ResolvingSession, isReady = false),
        )
        val reportCount = AtomicInteger(0)
        composeRule.activity.fullyDrawnReporter.addOnReportDrawnListener {
            reportCount.incrementAndGet()
        }

        composeRule.setContent {
            ReportStartupRootDrawn(
                expectedRoot = StartupRoot.Login,
                actualReadiness = readiness.value,
            )
        }
        composeRule.waitForIdle()
        assertFalse(composeRule.activity.fullyDrawnReporter.isFullyDrawnReported)

        composeRule.runOnIdle {
            readiness.value = StartupRootReadiness(StartupRoot.Login, isReady = false)
        }
        composeRule.waitForIdle()
        assertFalse(composeRule.activity.fullyDrawnReporter.isFullyDrawnReported)

        composeRule.runOnIdle {
            readiness.value = StartupRootReadiness(StartupRoot.CareHome, isReady = true)
        }
        composeRule.waitForIdle()
        assertFalse(composeRule.activity.fullyDrawnReporter.isFullyDrawnReported)

        composeRule.runOnIdle {
            readiness.value = StartupRootReadiness(StartupRoot.Login, isReady = true)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.activity.fullyDrawnReporter.isFullyDrawnReported
        }
        assertEquals(1, reportCount.get())

        composeRule.runOnIdle {
            readiness.value = StartupRootReadiness(StartupRoot.ResolvingSession, isReady = false)
        }
        composeRule.waitForIdle()
        assertTrue(composeRule.activity.fullyDrawnReporter.isFullyDrawnReported)
        assertEquals(1, reportCount.get())
    }

    @Test
    fun fullyDrawn_activityRecreationGetsOneFreshCompletionWithoutBlocking() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as MainApplication
        val consentManager = application.privacyConsentManager
        val originallyConsented = consentManager.isPrivacyConsented
        consentManager.resetConsent()

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val first = awaitFullyDrawn(scenario)

                scenario.recreate()
                val recreated = awaitFullyDrawn(scenario)

                assertNotSame(first.activity, recreated.activity)
                assertEquals(1, first.reportCount.get())
                assertEquals(1, recreated.reportCount.get())
            }
        } finally {
            if (originallyConsented) {
                consentManager.markConsented()
            } else {
                consentManager.resetConsent()
            }
        }
    }

    private fun awaitFullyDrawn(
        scenario: ActivityScenario<MainActivity>,
    ): ActivityReport {
        val activity = AtomicReference<MainActivity>()
        val reportCount = AtomicInteger(0)
        val reportLatch = CountDownLatch(1)
        scenario.onActivity { currentActivity ->
            activity.set(currentActivity)
            currentActivity.fullyDrawnReporter.addOnReportDrawnListener {
                reportCount.incrementAndGet()
                reportLatch.countDown()
            }
        }

        assertTrue(
            "Activity fully-drawn reporting timed out",
            reportLatch.await(15, TimeUnit.SECONDS),
        )
        scenario.onActivity { currentActivity ->
            assertSame(activity.get(), currentActivity)
            assertTrue(currentActivity.fullyDrawnReporter.isFullyDrawnReported)
        }
        assertEquals(1, reportCount.get())
        return ActivityReport(
            activity = requireNotNull(activity.get()),
            reportCount = reportCount,
        )
    }

    private data class ActivityReport(
        val activity: MainActivity,
        val reportCount: AtomicInteger,
    )
}
