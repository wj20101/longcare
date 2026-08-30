package com.ytone.longcare.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {
    @get:Rule
    val macrobenchmarkRule = MacrobenchmarkRule()

    @Test
    fun firstRunPrivacyNone() =
        benchmark(ProfileScenario.FIRST_RUN_PRIVACY, CompilationMode.None())

    @Test
    fun firstRunPrivacyProfile() =
        benchmark(ProfileScenario.FIRST_RUN_PRIVACY, requiredBaselineProfile())

    @Test
    fun loggedOutNone() =
        benchmark(ProfileScenario.LOGGED_OUT, CompilationMode.None())

    @Test
    fun loggedOutProfile() =
        benchmark(ProfileScenario.LOGGED_OUT, requiredBaselineProfile())

    @Test
    fun careHomeNone() =
        benchmark(ProfileScenario.CARE_HOME, CompilationMode.None())

    @Test
    fun careHomeProfile() =
        benchmark(ProfileScenario.CARE_HOME, requiredBaselineProfile())

    @Test
    fun salesHomeNone() =
        benchmark(ProfileScenario.SALES_HOME, CompilationMode.None())

    @Test
    fun salesHomeProfile() =
        benchmark(ProfileScenario.SALES_HOME, requiredBaselineProfile())

    private fun requiredBaselineProfile(): CompilationMode =
        CompilationMode.Partial(BaselineProfileMode.Require)

    private fun benchmark(scenario: ProfileScenario, compilationMode: CompilationMode) {
        check(scenario.isStartup) { "Only Startup scenarios can be benchmarked: ${scenario.wireId}" }
        val scenarioDriver = ProfileScenarioDriver.create()
        scenarioDriver.prepare(scenario)
        macrobenchmarkRule.measureRepeated(
            packageName = scenarioDriver.targetAppId,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 10,
            setupBlock = {
                pressHome()
            },
            measureBlock = {
                with(scenarioDriver) { startAndAssert(scenario) }
            },
        )
    }
}
