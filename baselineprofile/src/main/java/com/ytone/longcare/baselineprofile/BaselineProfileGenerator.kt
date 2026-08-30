package com.ytone.longcare.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun firstRunPrivacyStartup() = collectScenario(
        scenario = ProfileScenario.FIRST_RUN_PRIVACY,
        includeInStartupProfile = true,
    )

    @Test
    fun loggedOutStartup() = collectScenario(
        scenario = ProfileScenario.LOGGED_OUT,
        includeInStartupProfile = true,
    )

    @Test
    fun careHomeStartup() = collectScenario(
        scenario = ProfileScenario.CARE_HOME,
        includeInStartupProfile = true,
    )

    @Test
    fun salesHomeStartup() = collectScenario(
        scenario = ProfileScenario.SALES_HOME,
        includeInStartupProfile = true,
    )

    @Test
    fun careServiceRecordsBaselineOnly() = collectScenario(
        scenario = ProfileScenario.CARE_SERVICE_RECORDS,
        includeInStartupProfile = false,
    )

    @Test
    fun salesCustomersBaselineOnly() = collectScenario(
        scenario = ProfileScenario.SALES_CUSTOMERS,
        includeInStartupProfile = false,
    )

    private fun collectScenario(
        scenario: ProfileScenario,
        includeInStartupProfile: Boolean,
    ) {
        check(scenario.isStartup == includeInStartupProfile) {
            "Profile classification mismatch for ${scenario.wireId}"
        }
        val scenarioDriver = ProfileScenarioDriver.create()
        scenarioDriver.prepare(scenario)
        baselineProfileRule.collect(
            packageName = scenarioDriver.targetAppId,
            includeInStartupProfile = includeInStartupProfile,
        ) {
            pressHome()
            with(scenarioDriver) { startAndAssert(scenario) }
            if (!scenario.isStartup) {
                scenarioDriver.runBaselineJourney(scenario)
            }
        }
    }
}
