package com.ytone.longcare.features.update.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.R
import com.ytone.longcare.model.AppVersionModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdatePromptTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun availableUpdate_showsPromptAndUsesTestOwnedDownloadBoundary() {
        val downloadRequests = mutableListOf<String>()
        val update =
            AppVersionModel(
                versionCode = 43,
                versionName = "test-43",
                upType = 1,
                remarks = "Test-owned update prompt",
                platform = "android",
                downUrl = "https://updates.mock.invalid/longcare-43.apk",
            )

        composeRule.setContent {
            MaterialTheme {
                AppUpdateDialogContent(
                    appVersionModel = update,
                    isDownloading = false,
                    downloadProgress = 0,
                    errorMessage = null,
                    onDismiss = {},
                    onStartDownload = { downloadRequests += update.downUrl },
                    onCancelDownload = {},
                    onClearError = {},
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.update_version, update.versionName),
        ).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(downloadRequests.isEmpty()) }

        composeRule.onNodeWithText(context.getString(R.string.update_action)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(update.downUrl), downloadRequests)
            assertTrue(downloadRequests.single().contains(".mock.invalid/"))
        }
    }
}
