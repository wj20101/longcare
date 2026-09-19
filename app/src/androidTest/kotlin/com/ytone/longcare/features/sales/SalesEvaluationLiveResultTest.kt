package com.ytone.longcare.features.sales

import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.MainActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in, read-only check of an already submitted test customer's live result.
 * Does not create customers, submit questionnaires, or replace the full UI journey.
 */
class SalesEvaluationLiveResultTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun manualFormFetchesLiveGradeAndDisplaysCompletion() {
        val args = InstrumentationRegistry.getArguments()
        val customerId = args.getString("liveEvaluationCustomerId")?.toIntOrNull()
        val expectedGrade = args.getString("liveEvaluationExpectedGrade")
        assumeTrue(customerId != null && customerId > 0 && !expectedGrade.isNullOrBlank())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("首页").fetchSemanticsNodes().isNotEmpty()
            }
            lateinit var viewModel: SalesViewModel
            scenario.onActivity { activity ->
                viewModel = ViewModelProvider(activity)[SalesViewModel::class.java]
                viewModel.loadCustomerDetail(requireNotNull(customerId))
            }
            compose.waitUntil(15_000) { viewModel.uiState.value.selectedCustomer?.id == customerId }
            scenario.onActivity { activity ->
                assertNull(viewModel.uiState.value.evaluationFormRequest)
                viewModel.onEvaluationH5Closed()
                viewModel.loadEvaluationResult()
                activity.setContent {
                    val state by viewModel.uiState.collectAsState()
                    SalesPageBackground {
                        SalesEvaluationCompleteScreen(
                            hasReport = !state.evaluationResult?.pgUrl.isNullOrBlank(),
                            onBack = {}, onDone = {}, onOpenReport = {},
                            grade = state.evaluationResult?.pgResult,
                            isLoading = state.isEvaluationResultLoading,
                            resultError = state.evaluationResultError,
                            onRefresh = viewModel::loadEvaluationResult,
                        )
                    }
                }
            }
            compose.waitUntil(15_000) { !viewModel.uiState.value.isEvaluationResultLoading }
            val state = viewModel.uiState.value
            assertFalse(state.evaluationResultError)
            assertEquals(expectedGrade, state.evaluationResult?.pgResult)
            assertFalse(state.evaluationResult?.pgUrl.isNullOrBlank())
            compose.onNodeWithText("评估成功，评估等级为：$expectedGrade").assertIsDisplayed()
        }
    }
}
