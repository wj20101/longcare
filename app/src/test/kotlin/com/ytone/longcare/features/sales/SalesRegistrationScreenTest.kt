package com.ytone.longcare.features.sales

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ytone.longcare.model.AddUserLatentParamModel
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SalesRegistrationScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var submitted: AddUserLatentParamModel? = null

    @Test
    fun defaultNoAndBlankRemarksCanContinueAndSubmit() {
        showRegistration()
        scrollTo("是否残疾")
        compose.onNodeWithText("否").assertIsSelected()
        compose.onNodeWithText("是").assertIsNotSelected()
        clickContinue()
        compose.onNodeWithText("是否残疾：").assertExists()
        compose.onNodeWithText("否").assertExists()
        compose.onNodeWithText("备注：").assertDoesNotExist()
        scrollTo("确定提交")
        compose.onNodeWithText("确定提交").performClick()
        compose.runOnIdle {
            assertEquals(0, submitted?.isDisability)
            assertEquals("", submitted?.remarks)
        }
    }

    @Test
    fun clickingOptionTextIsExclusiveAndUsesRadioSemantics() {
        showRegistration()
        scrollTo("是否残疾")
        val radioRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        compose.onNodeWithText("是").assert(radioRole).performClick().assertIsSelected()
        compose.onNodeWithText("否").assert(radioRole).assertIsNotSelected()
        compose.onNodeWithText("否").performClick().assertIsSelected()
        compose.onNodeWithText("是").assertIsNotSelected()
    }

    @Test
    fun remarksAndDisabilitySurviveConfirmationBackAndSavedStateRestore() {
        val restoration = showRegistration()
        val remarks = "  测试备注\n第二行  "
        scrollTo("是否残疾")
        compose.onNodeWithText("是").performClick()
        scrollTo("备注（选填）")
        compose.onNodeWithText("备注（选填）").performTextInput(remarks)
        restoration.emulateSavedInstanceStateRestore()
        scrollTo("是否残疾")
        compose.onNodeWithText("是").assertIsSelected()
        scrollTo(remarks)
        compose.onNodeWithText(remarks).assertExists()

        clickContinue()
        compose.onNodeWithText("是").assertExists()
        compose.onNodeWithText("测试备注\n第二行").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("信息确认").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        scrollTo("是否残疾")
        compose.onNodeWithText("是").assertIsSelected()
        scrollTo(remarks)
        compose.onNodeWithText(remarks).assertExists()

        clickContinue()
        scrollTo("确定提交")
        compose.onNodeWithText("确定提交").performClick()
        compose.runOnIdle {
            assertEquals(1, submitted?.isDisability)
            assertEquals("测试备注\n第二行", submitted?.remarks)
        }
    }

    @Test
    fun whitespaceOnlyRemarksAreNotShownInConfirmation() {
        showRegistration(SalesCustomerDraft(userName = "测试客户", remarks = " \n "))
        clickContinue()
        compose.onNodeWithText("备注：").assertDoesNotExist()
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private fun clickContinue() {
        scrollTo("提交")
        compose.onNodeWithText("提交").performClick()
        compose.onNodeWithText("信息确认").assertExists()
    }

    private fun showRegistration(
        initial: SalesCustomerDraft = SalesCustomerDraft(userName = "测试客户"),
    ): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            var draft by rememberSaveable(stateSaver = salesCustomerDraftSaver) { mutableStateOf(initial) }
            var confirmation by rememberSaveable { mutableStateOf(false) }
            LongCareTheme {
                if (confirmation) {
                    SalesInformationConfirmationScreen(
                        draft = draft,
                        photoUris = emptyList(),
                        onBack = { confirmation = false },
                        onSubmit = { submitted = draft.toRequest(null, emptyList()) },
                    )
                } else {
                    SalesRegistrationScreen(
                        draft = draft,
                        photoUris = emptyList(),
                        location = null,
                        onDraftChange = { draft = it },
                        onTakePhoto = {},
                        onRemovePhoto = {},
                        onRequestLocation = {},
                        onBack = {},
                        onContinue = { confirmation = true },
                        onValidationError = { error(it) },
                    )
                }
            }
        }
        return restoration
    }
}
