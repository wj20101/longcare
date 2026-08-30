package com.ytone.longcare.features.identification.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.feature.identification.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentificationScreenContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun initialState_showsBothCardsAndDispatchesBackAndServiceAction() {
        val events = mutableListOf<IdentificationScreenEvent>()

        composeRule.setContent {
            IdentificationScreenContent(
                state = renderState(),
                onEvent = events::add,
            )
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.identification_service_person_avatar),
        ).assertExists()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.identification_elder_avatar),
        ).assertExists()
        composeRule.onNodeWithTag(SERVICE_PERSON_ACTION_TAG).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(ELDER_ACTION_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(IDENTIFICATION_NEXT_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(IDENTIFICATION_BACK_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    IdentificationScreenEvent.VerifyServicePerson,
                    IdentificationScreenEvent.NavigateBack,
                ),
                events,
            )
        }
    }

    @Test
    fun elderVerified_enablesNextAndDispatchesNavigation() {
        val events = mutableListOf<IdentificationScreenEvent>()

        composeRule.setContent {
            IdentificationScreenContent(
                state = renderState(
                    serviceStatus = IdentificationCardStatus.VERIFIED,
                    elderStatus = IdentificationCardStatus.VERIFIED,
                    elderActionEnabled = true,
                    nextEnabled = true,
                ),
                onEvent = events::add,
            )
        }

        composeRule.onNodeWithText(
            context.getString(
                R.string.identification_verified,
                context.getString(R.string.identification_service_person),
            ),
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(
                R.string.identification_verified,
                context.getString(R.string.identification_elder),
            ),
        ).assertExists()
        composeRule.onNodeWithTag(IDENTIFICATION_NEXT_TAG).assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(IdentificationScreenEvent.ContinueToServiceSelection),
                events,
            )
        }
    }

    @Test
    fun progressAndErrors_renderAndRetryWithTheCorrectPerson() {
        val events = mutableListOf<IdentificationScreenEvent>()
        val state = mutableStateOf(
            renderState(
                serviceStatus = IdentificationCardStatus.FACE_VERIFYING,
                elderStatus = IdentificationCardStatus.PHOTO_PROCESSING,
                elderActionEnabled = true,
            ),
        )

        composeRule.setContent {
            IdentificationScreenContent(state = state.value, onEvent = events::add)
        }

        composeRule.onNodeWithText(
            context.getString(
                R.string.identification_recognizing,
                context.getString(R.string.identification_service_person),
            ),
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.identification_processing),
        ).assertTextEquals(context.getString(R.string.identification_processing))

        composeRule.runOnIdle {
            state.value = renderState(
                serviceStatus = IdentificationCardStatus.FACE_SETUP_ERROR,
                elderStatus = IdentificationCardStatus.FACE_ERROR,
                elderActionEnabled = true,
            )
        }

        composeRule.onNodeWithText(
            context.getString(R.string.identification_setup_failed),
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.identification_verification_failed),
        ).assertExists()
        composeRule.onNodeWithTag(SERVICE_PERSON_RETRY_TAG).performClick()
        composeRule.onNodeWithTag(ELDER_RETRY_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    IdentificationScreenEvent.RetryFaceSetup,
                    IdentificationScreenEvent.RetryFaceVerification(
                        IdentificationPersonType.ELDER,
                    ),
                ),
                events,
            )
        }
    }

    private fun renderState(
        serviceStatus: IdentificationCardStatus = IdentificationCardStatus.ACTION,
        elderStatus: IdentificationCardStatus = IdentificationCardStatus.ACTION,
        elderActionEnabled: Boolean = false,
        nextEnabled: Boolean = false,
    ): IdentificationScreenRenderState = IdentificationScreenRenderState(
        servicePerson = IdentificationCardRenderState(
            personType = IdentificationPersonType.SERVICE_PERSON,
            status = serviceStatus,
            actionEnabled = true,
        ),
        elder = IdentificationCardRenderState(
            personType = IdentificationPersonType.ELDER,
            status = elderStatus,
            actionEnabled = elderActionEnabled,
        ),
        nextEnabled = nextEnabled,
    )
}
