package com.ytone.longcare.baselineprofile

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

class ProfileScenarioDriver private constructor(
    val targetAppId: String,
    private val device: UiDevice,
) {
    fun prepare(scenario: ProfileScenario) {
        clearPackageData()
        if (scenario == ProfileScenario.FIRST_RUN_PRIVACY) {
            forceStopTarget()
            return
        }

        val observation = launchSetupRequest(scenario.wireId)
        check(observation.outcome == SetupOutcome.COMPLETE) {
            "Profile scenario=${scenario.wireId} expectedState=${scenario.expectedSetupState} " +
                "setup failed: ${observation.message}"
        }
        check(observation.completeNodeCount == 1 && observation.failedNodeCount == 0) {
            "Profile scenario=${scenario.wireId} setup result must be unique: $observation"
        }
        forceStopTarget()
    }

    fun MacrobenchmarkScope.startAndAssert(scenario: ProfileScenario) {
        startActivityAndWait()
        awaitTags(scenario, scenario.startupTags)
    }

    fun runBaselineJourney(scenario: ProfileScenario) {
        val journey = requireNotNull(scenario.journey) {
            "Profile scenario=${scenario.wireId} has no Baseline-only journey"
        }
        clickExactNode(scenario, journey.entryTag)
        awaitTags(scenario, journey.destinationTags)
        clickExactNode(scenario, journey.backTag)
        awaitTags(scenario, journey.returnTags)
    }

    internal fun clearPackageData() {
        val result = device.executeShellCommand("pm clear $targetAppId").trim()
        check(result == "Success") {
            "Profile package clear failed for $targetAppId: $result"
        }
    }

    internal fun launchSetupRequest(wireId: String?): SetupObservation {
        val intent = Intent().apply {
            component = ComponentName(
                targetAppId,
                "$targetAppId.performance.ProfileScenarioSetupActivity",
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            wireId?.let { putExtra(PROFILE_SCENARIO_EXTRA, it) }
        }
        InstrumentationRegistry.getInstrumentation().context.startActivity(intent)

        val resultNode = device.wait(
            Until.findObject(By.res(SETUP_RESULT_PATTERN)),
            SETUP_TIMEOUT_MS,
        ) ?: return SetupObservation(
            outcome = SetupOutcome.TIMED_OUT,
            message = "setup result node timed out",
            completeNodeCount = 0,
            failedNodeCount = 0,
        )
        val completeNodes = device.findObjects(By.res(SETUP_COMPLETE_TAG)).size
        val failedNodes = device.findObjects(By.res(SETUP_FAILED_TAG)).size
        val resultTag = resultNode.resourceName.orEmpty()
        val outcome = when {
            resultTag.endsWith(SETUP_COMPLETE_TAG) -> SetupOutcome.COMPLETE
            resultTag.endsWith(SETUP_FAILED_TAG) -> SetupOutcome.FAILED
            else -> SetupOutcome.TIMED_OUT
        }
        return SetupObservation(
            outcome = outcome,
            message = resultNode.text.orEmpty(),
            completeNodeCount = completeNodes,
            failedNodeCount = failedNodes,
        )
    }

    internal fun forceStopTarget() {
        device.executeShellCommand("am force-stop $targetAppId")
    }

    private fun clickExactNode(scenario: ProfileScenario, tag: String) {
        val node = device.findObject(scenario.selector(tag))
            ?: throw AssertionError(
                "Profile scenario=${scenario.wireId} expectedState=${scenario.expectedSetupState} " +
                    "missingTags=[$tag] before click",
            )
        check(node.isEnabled) {
            "Profile scenario=${scenario.wireId} node=$tag is disabled"
        }
        node.click()
    }

    private fun awaitTags(scenario: ProfileScenario, tags: List<String>) {
        val deadline = SystemClock.elapsedRealtime() + PAGE_TIMEOUT_MS
        tags.forEach { tag ->
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining > 0L) {
                device.wait(Until.hasObject(scenario.selector(tag)), remaining)
            }
        }
        val missingTags = tags.filterNot { tag -> device.hasObject(scenario.selector(tag)) }
        if (missingTags.isNotEmpty()) {
            throw AssertionError(
                "Profile scenario=${scenario.wireId} expectedState=${scenario.expectedSetupState} " +
                    "missingTags=$missingTags currentPackage=${device.currentPackageName}",
            )
        }
    }

    companion object {
        private const val PROFILE_SCENARIO_EXTRA = "profile_scenario_id"
        private const val SETUP_COMPLETE_TAG = "profile_setup_complete"
        private const val SETUP_FAILED_TAG = "profile_setup_failed"
        private val SETUP_RESULT_PATTERN = Pattern.compile(
            "$SETUP_COMPLETE_TAG|$SETUP_FAILED_TAG",
        )
        private const val SETUP_TIMEOUT_MS = 15_000L
        private const val PAGE_TIMEOUT_MS = 10_000L

        fun create(): ProfileScenarioDriver {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val targetAppId = InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: error("targetAppId not passed as instrumentation runner arg")
            return ProfileScenarioDriver(
                targetAppId = targetAppId,
                device = UiDevice.getInstance(instrumentation),
            )
        }
    }
}

internal enum class SetupOutcome {
    COMPLETE,
    FAILED,
    TIMED_OUT,
}

internal data class SetupObservation(
    val outcome: SetupOutcome,
    val message: String,
    val completeNodeCount: Int,
    val failedNodeCount: Int,
)
