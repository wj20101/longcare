package com.ytone.longcare.features.sales

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.model.ToDoResultModel
import com.ytone.longcare.model.User
import com.ytone.longcare.model.UserLatentCheckState
import com.ytone.longcare.model.UserLatentDetailModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesLargeFontLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun registrationFieldsGrowInsteadOfClippingText() {
        setLargeFontContent {
            SalesRegistrationScreen(
                draft = SalesCustomerDraft(),
                photoUris = emptyList(),
                location = null,
                onDraftChange = {},
                onTakePhoto = {},
                onRemovePhoto = {},
                onRequestLocation = {},
                onBack = {},
                onContinue = {},
                onValidationError = {},
            )
        }

        val fields =
            composeRule
                .onAllNodes(hasSetTextAction())
                .fetchSemanticsNodes()
        val singleLineMinimumHeight = with(composeRule.density) { 56.dp.toPx() }
        val addressMinimumHeight = with(composeRule.density) { 82.dp.toPx() }

        assertEquals(6, fields.size)
        assertTrue(
            fields.all {
                it.boundsInRoot.height + PIXEL_ROUNDING_TOLERANCE >= singleLineMinimumHeight
            }
        )
        assertTrue(
            fields.any {
                it.boundsInRoot.height + PIXEL_ROUNDING_TOLERANCE >= addressMinimumHeight
            }
        )
    }

    @Test
    fun photoAddOpensCameraDirectlyWithoutGalleryChoice() {
        var takePhotoRequests = 0
        setLargeFontContent {
            SalesRegistrationScreen(
                draft = SalesCustomerDraft(),
                photoUris = emptyList(),
                location = null,
                onDraftChange = {},
                onTakePhoto = { takePhotoRequests += 1 },
                onRemovePhoto = {},
                onRequestLocation = {},
                onBack = {},
                onContinue = {},
                onValidationError = {},
            )
        }

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription("添加照片"))
        composeRule
            .onNode(hasContentDescription("添加照片"))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, takePhotoRequests)
        }
        composeRule.onNodeWithText("从相册选择").assertDoesNotExist()
    }

    @Test
    fun photoThumbnailOpensSharedFullScreenPreview() {
        setLargeFontContent {
            SalesRegistrationScreen(
                draft = SalesCustomerDraft(),
                photoUris = listOf(Uri.parse("file:///tmp/sales-photo.jpg")),
                location = null,
                onDraftChange = {},
                onTakePhoto = {},
                onRemovePhoto = {},
                onRequestLocation = {},
                onBack = {},
                onContinue = {},
                onValidationError = {},
            )
        }

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription("照片"))
        composeRule.onNodeWithContentDescription("照片").performClick()

        composeRule.onNodeWithContentDescription("预览图片").assertIsDisplayed()
    }

    @Test
    fun dashboardActionsStackAndLongUserNameWraps() {
        setLargeFontContent {
            SalesDashboardScreen(
                user =
                    User(
                        userName = "Android销售负责人",
                        userIdentity = 1,
                    ),
                companyName = "庆平智慧养老测试",
                customers = emptyList(),
                toDoCount = 1,
                isToDoCountLoading = false,
                onRegisterCustomer = {},
                onReminders = {},
                onCustomerClick = {},
            )
        }

        val registrationBounds =
            composeRule.onNodeWithText("对象登记").fetchSemanticsNode().boundsInRoot
        val reminderBounds =
            composeRule.onNodeWithText("待办提醒").fetchSemanticsNode().boundsInRoot
        val userNameBounds =
            composeRule
                .onNodeWithTag("home_top_user_name")
                .fetchSemanticsNode()
                .boundsInRoot
        val singleLineHeight = with(composeRule.density) { 28.dp.toPx() }

        assertTrue(registrationBounds.bottom < reminderBounds.top)
        assertTrue(userNameBounds.height > singleLineHeight)
    }

    @Test
    fun customerStatusTabsBecomeHorizontallyScrollable() {
        setLargeFontContent {
            SalesCustomerListScreen(
                customers = emptyList(),
                isLoading = false,
                isLoadingMore = false,
                canLoadMore = false,
                loadMoreErrorMessage = null,
                initialKeyword = "",
                initialCheckState = UserLatentCheckState.ALL,
                onBack = {},
                onSearch = { _, _ -> },
                onLoadMore = {},
                onCustomerClick = {},
            )
        }

        composeRule
            .onNodeWithTag("customer_status_tabs")
            .assert(hasScrollAction())
    }

    @Test
    fun customerInfoLabelsStackAboveValues() {
        val identityCardNumber = "330106199001011234"
        setLargeFontContent {
            SalesCustomerDetailScreen(
                customer =
                    UserLatentDetailModel(
                        id = 1,
                        userName = "大字体测试客户",
                        identityCardNumber = identityCardNumber,
                        guardianName = "联系人",
                        guardianPhone = "13800138000",
                        guardianRelation = "子女",
                        liveAddress = "浙江省杭州市测试地址",
                    ),
                isLoading = false,
                errorMessage = null,
                onBack = {},
                onRetry = {},
                onEvaluate = {},
                onOpenReport = {},
            )
        }

        val labelBounds =
            composeRule.onNodeWithText("身份证号：").fetchSemanticsNode().boundsInRoot
        val valueBounds =
            composeRule.onNodeWithText(identityCardNumber).fetchSemanticsNode().boundsInRoot

        assertTrue(valueBounds.top >= labelBounds.bottom)
    }

    @Test
    fun resultActionsStackVertically() {
        setLargeFontContent {
            SalesSubmitSuccessScreen(
                onBack = {},
                onEvaluation = {},
            )
        }

        val backBounds =
            composeRule.onNodeWithText("确认并返回").fetchSemanticsNode().boundsInRoot
        val evaluationBounds =
            composeRule.onNodeWithText("进行评估").fetchSemanticsNode().boundsInRoot

        assertTrue(backBounds.bottom < evaluationBounds.top)
    }

    @Test
    fun evaluationChoicesStackVertically() {
        setLargeFontContent {
            SalesEvaluationChoiceScreen(
                onBack = {},
                onAutomaticEvaluation = {},
                onFormEvaluation = {},
            )
        }

        val automaticBounds =
            composeRule.onNodeWithText("设备自动评估").fetchSemanticsNode().boundsInRoot
        val formBounds =
            composeRule.onNodeWithText("表单评估").fetchSemanticsNode().boundsInRoot

        assertTrue(automaticBounds.bottom < formBounds.top)
    }

    @Test
    fun reminderDetailCanScrollToReturnAction() {
        setLargeFontContent {
            SalesReminderDetailScreen(
                reminder =
                    ToDoResultModel(
                        title = "大字体待办事项",
                        content = "请联系客户确认上门评估时间。".repeat(30),
                        createTime = "2026-08-03 10:30:00",
                    ),
                onBack = {},
            )
        }

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("返回"))
        composeRule.onNodeWithText("返回").assertIsDisplayed()
    }

    @Test
    fun evaluationGuideCanScrollToPrimaryAction() {
        setLargeFontContent {
            SalesEvaluationGuideScreen(
                connectedDeviceName = "QLZ 大字体测试设备",
                progressText = "",
                onBack = {},
                onOpenSdk = {},
            )
        }

        composeRule
            .onNodeWithText("继续评估")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun informationConfirmationCanScrollToSubmitAction() {
        setLargeFontContent {
            SalesInformationConfirmationScreen(
                draft =
                    SalesCustomerDraft(
                        userName = "大字体测试客户",
                        identityCardNumber = "330106199001011234",
                        guardianName = "大字体测试联系人",
                        guardianPhone = "13800138000",
                        guardianRelation = "子女",
                        liveAddress = "浙江省杭州市西湖区大字体测试地址",
                    ),
                photoUris = emptyList(),
                onBack = {},
                onSubmit = {},
            )
        }

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("确定提交"))
        composeRule.onNodeWithText("确定提交").assertIsDisplayed()
    }

    @Test
    fun reminderListKeepsPrimaryTextsVisible() {
        val title = "需要尽快联系客户并确认上门评估安排"
        val createTime = "2026-08-03 10:30:00"
        setLargeFontContent {
            SalesReminderListScreen(
                reminders =
                    listOf(
                        ToDoResultModel(
                            title = title,
                            content = "请联系客户确认上门评估时间",
                            createTime = createTime,
                        )
                    ),
                isLoading = false,
                errorMessage = null,
                onBack = {},
                onRetry = {},
                onReminderClick = {},
            )
        }

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(createTime).assertIsDisplayed()
    }

    @Test
    fun deviceStatusCanScrollToStartAction() {
        val deviceName = "QLZ 大字体测试设备 2026"
        setLargeFontContent {
            SalesDeviceStatusScreen(
                connectedDeviceName = deviceName,
                tokenReady = true,
                progressText = "",
                onBack = {},
                onStartEvaluation = {},
            )
        }

        composeRule.onNodeWithText(deviceName).assertIsDisplayed()
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("开始评估"))
        composeRule.onNodeWithText("开始评估").assertIsDisplayed()
    }

    @Test
    fun evaluationCompleteCanScrollToReportAction() {
        setLargeFontContent {
            SalesEvaluationCompleteScreen(
                hasReport = true,
                onBack = {},
                onDone = {},
                onOpenReport = {},
            )
        }

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("查看评估报告"))
        composeRule.onNodeWithText("查看评估报告").assertIsDisplayed()
    }

    private fun setLargeFontContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides
                    Density(
                        density = currentDensity.density,
                        fontScale = LARGE_FONT_SCALE,
                    )
            ) {
                SalesPageBackground(content = content)
            }
        }
    }

    private companion object {
        const val LARGE_FONT_SCALE = 1.4f
        const val PIXEL_ROUNDING_TOLERANCE = 1f
    }
}
