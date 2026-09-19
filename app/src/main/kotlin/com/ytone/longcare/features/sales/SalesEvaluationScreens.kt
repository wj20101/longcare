package com.ytone.longcare.features.sales

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.integration.qlz.QlzDeviceOption
import com.ytone.longcare.integration.qlz.QlzEvaluationIssue
import com.ytone.longcare.integration.qlz.QlzEvaluationRecoveryAction
import com.ytone.longcare.integration.qlz.QlzEvaluationStage
import com.ytone.longcare.integration.qlz.QlzEvaluationUiState

@Composable
internal fun SalesEvaluationPrepareErrorDialog(
    message: String,
    onExit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(text = stringResource(R.string.sales_evaluation_prepare_error_title))
        },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = singleClick(onClick = onExit)) {
                Text(text = stringResource(R.string.sales_evaluation_prepare_error_action))
            }
        },
    )
}

@Composable
internal fun SalesDeviceStatusScreen(
    evaluationState: QlzEvaluationUiState,
    tokenReady: Boolean,
    onBack: () -> Unit,
    onStartScan: () -> Unit,
    onSelectDevice: (QlzDeviceOption) -> Unit,
    onRetry: () -> Unit,
    onRecheckEnvironment: () -> Unit,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        val horizontalPadding = if (maxWidth >= 600.dp) 48.dp else 18.dp
        val illustrationHeight = if (maxHeight < 620.dp) 92.dp else 120.dp
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SalesTopBar(
                title = stringResource(R.string.sales_evaluation_device_status_title),
                onBack = onBack,
            )
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp),
                contentPadding =
                    PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = 8.dp,
                        bottom = 24.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .salesWhiteCard()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.sales_qlz_device_design),
                            contentDescription =
                                stringResource(
                                    R.string.sales_evaluation_device_image_description
                                ),
                            modifier =
                                Modifier
                                    .width(200.dp)
                                    .height(illustrationHeight),
                            contentScale = ContentScale.Fit,
                        )
                        EvaluationStageText(evaluationState)
                        if (
                            evaluationState.stage == QlzEvaluationStage.AUTHORIZING ||
                            evaluationState.stage == QlzEvaluationStage.SCANNING
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = SalesBlue,
                                strokeWidth = 3.dp,
                            )
                        }
                    }
                }

                evaluationState.issue?.let { issue ->
                    item {
                        EvaluationIssueCard(issue = issue)
                    }
                }

                if (evaluationState.devices.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.sales_evaluation_choose_device),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    items(
                        items = evaluationState.devices,
                        key = QlzDeviceOption::id,
                    ) { device ->
                        EvaluationDeviceRow(
                            device = device,
                            enabled =
                                evaluationState.stage == QlzEvaluationStage.SCANNING ||
                                    evaluationState.stage == QlzEvaluationStage.SCAN_RESULTS,
                            onClick = { onSelectDevice(device) },
                        )
                    }
                }

                item {
                    val action = evaluationState.recoveryAction
                    val click =
                        when (action) {
                            QlzEvaluationRecoveryAction.RETRY_AUTHORIZATION,
                            QlzEvaluationRecoveryAction.RETRY_SCAN,
                            QlzEvaluationRecoveryAction.RETRY_CONNECTION,
                            QlzEvaluationRecoveryAction.RETRY_UPLOAD,
                            -> onRetry

                            QlzEvaluationRecoveryAction.RECHECK_ENVIRONMENT ->
                                onRecheckEnvironment

                            QlzEvaluationRecoveryAction.EXIT -> onBack
                            else -> onStartScan
                        }
                    SalesPrimaryButton(
                        text = evaluationScanActionText(evaluationState, tokenReady),
                        onClick = singleClick(onClick = click),
                        enabled =
                            tokenReady &&
                                evaluationState.stage !in
                                setOf(
                                    QlzEvaluationStage.AUTHORIZING,
                                    QlzEvaluationStage.SCANNING,
                                    QlzEvaluationStage.CONNECTING,
                                    QlzEvaluationStage.UPLOADING,
                                ),
                        modifier = Modifier.testTag("qlz_scan_action"),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SalesEvaluationGuideScreen(
    evaluationState: QlzEvaluationUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val background = Brush.verticalGradient(
                        0f to Color(0xFF468AFF),
                        0.4245f to Color(0xFF468AFF),
                        0.9776f to Color(0xFFF6F9FF),
                        1f to Color(0xFFF6F9FF),
                        endY = size.width * 1013f / 750f,
                    )
                    onDrawBehind { drawRect(background) }
                }
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SalesTopBar(
                title = stringResource(R.string.sales_evaluation_title),
                onBack = onBack,
                compact = true,
            )
            LazyColumn(
                modifier =
                    Modifier
                        .widthIn(max = 510.dp)
                        .fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 15.5.dp,
                        end = 15.5.dp,
                        top = 8.dp,
                        bottom = 24.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    EvaluationMeasurementCard(evaluationState)
                }

                evaluationState.issue?.let { issue ->
                    item {
                        EvaluationIssueCard(issue = issue)
                    }
                }

                if (evaluationState.recoveryAction != null) {
                    item {
                        val exits =
                            evaluationState.recoveryAction ==
                                QlzEvaluationRecoveryAction.EXIT
                        SalesPrimaryButton(
                            text =
                                stringResource(
                                    if (exits) {
                                        R.string.sales_evaluation_exit
                                    } else {
                                        R.string.sales_evaluation_retry
                                    }
                                ),
                            onClick =
                                singleClick(
                                    onClick = if (exits) onBack else onRetry
                                ),
                            modifier = Modifier.testTag("qlz_retry_action"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EvaluationDeviceRow(
    device: QlzDeviceOption,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .salesWhiteCard()
                .clickable(enabled = enabled, onClick = singleClick(onClick = onClick))
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .testTag("qlz_device_${device.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SalesBlue.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.sales_evaluation_ble_badge),
                color = SalesBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    device.displayName.ifBlank {
                        stringResource(R.string.sales_evaluation_unnamed_device)
                    },
                color = SalesTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = device.maskedIdentifier,
                color = SalesTextSecondary,
                fontSize = 12.sp,
            )
        }
        Text(
            text = stringResource(R.string.sales_evaluation_connect),
            color = if (enabled) SalesBlue else SalesTextSecondary,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun EvaluationMeasurementCard(state: QlzEvaluationUiState) {
    val progress = state.showMeasurementProgress || state.totalCount > 0 ||
        state.stage == QlzEvaluationStage.UPLOADING
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White)) {
        val scale = maxWidth.value / 339f
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = (341 * scale).dp)
                .padding(horizontal = 12.dp).padding(bottom = 28.dp)
                .testTag(if (progress) "qlz_progress_card" else "qlz_grip_card"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (progress) {
                Spacer(Modifier.height((67.5f * scale).dp))
                Image(
                    painterResource(R.drawable.sales_evaluation_hourglass),
                    contentDescription = null,
                    modifier = Modifier.size((71 * scale).dp, (120.5f * scale).dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height((30 * scale).dp))
                EvaluationProgressBar(
                    progress = state.progressFraction,
                    modifier = Modifier.width((248 * scale).dp).height((19 * scale).dp)
                        .testTag("qlz_measurement_progress"),
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = if (state.totalCount > 0) {
                        stringResource(R.string.sales_evaluation_percentage, state.progressPercent)
                    } else stringResource(R.string.sales_evaluation_waiting_progress),
                    color = Color(0xFF666666), fontSize = 16.sp, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.5.dp))
                Text(
                    text = if (state.stage == QlzEvaluationStage.MEASURING ||
                        state.stage == QlzEvaluationStage.CONNECTED
                    ) stringResource(R.string.sales_evaluation_measuring_design) else evaluationStageText(state),
                    color = Color(0xFF666666), fontSize = 16.sp, textAlign = TextAlign.Center,
                )
            } else {
                Spacer(Modifier.height((8.5f * scale).dp))
                Image(
                    painterResource(R.drawable.sales_evaluation_instruction),
                    contentDescription = stringResource(R.string.sales_evaluation_hold_device_description),
                    modifier = Modifier.width((276 * scale).dp).height((199.5f * scale).dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height((12 * scale).dp))
                val fingerLabels = listOf(
                    stringResource(R.string.sales_evaluation_finger_little),
                    stringResource(R.string.sales_evaluation_finger_ring),
                    stringResource(R.string.sales_evaluation_finger_middle),
                    stringResource(R.string.sales_evaluation_finger_index),
                    stringResource(R.string.sales_evaluation_finger_thumb),
                )
                Row(
                    modifier = Modifier.width((197 * scale).dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    state.fingerContacts.asList().forEachIndexed { index, connected ->
                        FingerContactIndicator(
                            label = fingerLabels[index],
                            connected = connected,
                            modifier = Modifier.size((19.5f * scale).dp, (24.5f * scale).dp),
                            testTag = "qlz_finger_$index",
                        )
                    }
                }
                Spacer(Modifier.height((18.5f * scale).dp))
                Text(
                    text = stringResource(if (state.preparationSeconds != null) {
                        R.string.sales_evaluation_grip_ready
                    } else R.string.sales_evaluation_finger_instruction),
                    color = if (state.preparationSeconds != null) Color(0xFF3FA62F) else Color(0xFF666666),
                    fontSize = 16.sp, textAlign = TextAlign.Center,
                )
                state.preparationSeconds?.let { seconds ->
                    Spacer(Modifier.height(3.5.dp))
                    Text(
                        stringResource(R.string.sales_evaluation_grip_countdown, seconds),
                        color = Color(0xFF666666), fontSize = 16.sp,
                        modifier = Modifier.testTag("qlz_grip_countdown"),
                    )
                }
                if (state.stage == QlzEvaluationStage.POWER_PAUSED || state.stage == QlzEvaluationStage.ERROR) {
                    Spacer(Modifier.height(8.dp))
                    EvaluationStageText(state)
                }
            }
        }
    }
}

/** Continuous track under a proportional fill; no Material gap or minimum cap width. */
@Composable
internal fun EvaluationProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val fraction = progress.coerceIn(0f, 1f)
    Canvas(modifier.clip(RoundedCornerShape(50)).progressSemantics(fraction)) {
        val radius = size.height / 2
        drawRoundRect(Color(0xFFE5EDFF), cornerRadius = CornerRadius(radius))
        val fillWidth = size.width * fraction
        if (fillWidth > 0) {
            drawRoundRect(
                color = Color(0xFF63E544),
                topLeft = Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - fillWidth else 0f, 0f),
                size = Size(fillWidth, size.height),
                cornerRadius = CornerRadius(minOf(radius, fillWidth / 2)),
            )
        }
    }
}

@Composable
private fun FingerContactIndicator(
    label: String,
    connected: Boolean,
    modifier: Modifier,
    testTag: String,
) {
    val statusText =
        stringResource(
            if (connected) {
                R.string.sales_evaluation_finger_connected
            } else {
                R.string.sales_evaluation_finger_not_connected
            }
        )
    Box(
        modifier =
            modifier
                .semantics { stateDescription = statusText; contentDescription = label }
                .testTag(testTag)
                .clip(RoundedCornerShape(50))
                .background(if (connected) Color(0xFF63E544) else Color(0xFFDBDBDB)),
    )
}

@Composable
private fun EvaluationIssueCard(issue: QlzEvaluationIssue) {
    Text(
        text = evaluationIssueText(issue),
        color =
            if (issue == QlzEvaluationIssue.CHARGING) {
                Color(0xFF8B5A00)
            } else {
                Color(0xFF9E2630)
            },
        fontSize = 14.sp,
        lineHeight = 21.sp,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (issue == QlzEvaluationIssue.CHARGING) {
                        Color(0xFFFFF2CF)
                    } else {
                        Color(0xFFFFE8EA)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 13.dp)
                .testTag("qlz_issue_${issue.name.lowercase()}"),
    )
}

@Composable
private fun EvaluationStageText(state: QlzEvaluationUiState) {
    Text(
        text = evaluationStageText(state),
        color = SalesTextPrimary,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun evaluationStageText(state: QlzEvaluationUiState): String =
    when (state.stage) {
        QlzEvaluationStage.IDLE,
        QlzEvaluationStage.CLOSED,
        -> stringResource(R.string.sales_evaluation_ready_to_scan)

        QlzEvaluationStage.AUTHORIZING ->
            stringResource(R.string.sales_evaluation_authorizing)

        QlzEvaluationStage.READY_TO_SCAN ->
            stringResource(R.string.sales_evaluation_ready_to_scan)

        QlzEvaluationStage.SCANNING -> stringResource(R.string.sales_evaluation_scanning)
        QlzEvaluationStage.SCAN_RESULTS ->
            stringResource(R.string.sales_evaluation_devices_found, state.devices.size)

        QlzEvaluationStage.SCAN_EMPTY ->
            stringResource(R.string.sales_evaluation_no_devices)

        QlzEvaluationStage.CONNECTING ->
            stringResource(R.string.sales_evaluation_connecting)

        QlzEvaluationStage.CONNECTED ->
            stringResource(R.string.sales_evaluation_guide_connected)

        QlzEvaluationStage.MEASURING ->
            stringResource(R.string.sales_evaluation_measuring)

        QlzEvaluationStage.POWER_PAUSED ->
            stringResource(R.string.sales_evaluation_paused)

        QlzEvaluationStage.UPLOADING ->
            stringResource(R.string.sales_evaluation_uploading)

        QlzEvaluationStage.COMPLETED ->
            stringResource(R.string.sales_progress_detection_complete)

        QlzEvaluationStage.BLOCKED,
        QlzEvaluationStage.ERROR,
        -> stringResource(R.string.sales_evaluation_attention_required)
    }

@Composable
private fun evaluationScanActionText(
    state: QlzEvaluationUiState,
    tokenReady: Boolean,
): String {
    if (!tokenReady) return stringResource(R.string.sales_evaluation_preparing)
    return when {
        state.stage == QlzEvaluationStage.AUTHORIZING ->
            stringResource(R.string.sales_evaluation_authorizing)

        state.stage == QlzEvaluationStage.SCANNING ->
            stringResource(R.string.sales_evaluation_scanning)

        state.stage == QlzEvaluationStage.CONNECTING ->
            stringResource(R.string.sales_evaluation_connecting)

        state.recoveryAction == QlzEvaluationRecoveryAction.EXIT ->
            stringResource(R.string.sales_evaluation_exit)

        state.recoveryAction != null -> stringResource(R.string.sales_evaluation_retry)
        state.stage == QlzEvaluationStage.SCAN_RESULTS ->
            stringResource(R.string.sales_evaluation_rescan)

        else -> stringResource(R.string.sales_evaluation_scan_devices)
    }
}

@Composable
private fun evaluationIssueText(issue: QlzEvaluationIssue): String =
    stringResource(
        when (issue) {
            QlzEvaluationIssue.PERMISSION_REQUIRED ->
                R.string.sales_error_evaluation_permission

            QlzEvaluationIssue.BLUETOOTH_UNSUPPORTED ->
                R.string.sales_error_evaluation_ble_unsupported

            QlzEvaluationIssue.BLUETOOTH_DISABLED ->
                R.string.sales_error_evaluation_bluetooth_disabled

            QlzEvaluationIssue.LOCATION_SERVICE_DISABLED ->
                R.string.sales_error_evaluation_location_service_disabled

            QlzEvaluationIssue.SDK_UNAVAILABLE ->
                R.string.sales_error_evaluation_service_start

            QlzEvaluationIssue.SESSION_BUSY ->
                R.string.sales_error_evaluation_session_busy

            QlzEvaluationIssue.TOKEN_EXPIRED ->
                R.string.sales_error_evaluation_expired

            QlzEvaluationIssue.USER_NOT_ELIGIBLE ->
                R.string.sales_error_evaluation_user_not_eligible

            QlzEvaluationIssue.NETWORK -> R.string.sales_error_evaluation_network
            QlzEvaluationIssue.CONNECTION_FAILED ->
                R.string.sales_error_evaluation_connect_failed

            QlzEvaluationIssue.CONNECTION_LOST ->
                R.string.sales_error_evaluation_connection_lost

            QlzEvaluationIssue.CHECK_TIMEOUT ->
                R.string.sales_error_evaluation_check_timeout

            QlzEvaluationIssue.NO_MEASUREMENT_DATA ->
                R.string.sales_error_evaluation_no_measurement_data

            QlzEvaluationIssue.DEVICE_UNAUTHORIZED ->
                R.string.sales_error_evaluation_device_unauthorized

            QlzEvaluationIssue.LOW_POWER ->
                R.string.sales_error_evaluation_low_power

            QlzEvaluationIssue.DEVICE_SERVICE ->
                R.string.sales_error_evaluation_device_service

            QlzEvaluationIssue.DEVICE_INFO ->
                R.string.sales_error_evaluation_device_info

            QlzEvaluationIssue.WEAK_SIGNAL ->
                R.string.sales_error_evaluation_weak_signal

            QlzEvaluationIssue.MEASUREMENT_ENDED ->
                R.string.sales_error_evaluation_measurement_ended

            QlzEvaluationIssue.CHARGING ->
                R.string.sales_error_evaluation_charging

            QlzEvaluationIssue.PAYMENT_REQUIRED ->
                R.string.sales_error_evaluation_payment_required

            QlzEvaluationIssue.UPLOAD_FAILED ->
                R.string.sales_error_evaluation_upload_failed

            QlzEvaluationIssue.UNKNOWN -> R.string.sales_error_evaluation_continue
        }
    )

@Composable
internal fun SalesEvaluationCompleteScreen(
    hasReport: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenReport: () -> Unit,
    grade: String? = null,
    isLoading: Boolean = false,
    resultError: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = stringResource(R.string.sales_evaluation_complete_title),
            onBack = onBack,
        )
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .align(Alignment.CenterHorizontally),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 10.dp,
                    bottom = 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item {
                SalesSuccessPanel(
                    title = if (grade.isNullOrBlank()) {
                        stringResource(R.string.sales_evaluation_success)
                    } else {
                        stringResource(R.string.sales_evaluation_success_grade, grade.trim())
                    },
                )
            }
            if (isLoading || resultError || grade.isNullOrBlank()) {
                item {
                    Text(
                        text = stringResource(
                            when {
                                isLoading -> R.string.sales_evaluation_result_loading
                                resultError -> R.string.sales_evaluation_result_error
                                else -> R.string.sales_evaluation_grade_pending
                            },
                        ),
                    )
                }
                if (!isLoading) {
                    item {
                        SalesOutlinedActionButton(
                            text = stringResource(R.string.sales_evaluation_result_refresh),
                            onClick = onRefresh,
                        )
                    }
                }
            }
            item {
                SalesOutlinedActionButton(
                    text = stringResource(R.string.sales_common_done),
                    onClick = onDone,
                )
            }
            if (hasReport) {
                item {
                    SalesPrimaryButton(
                        text = stringResource(R.string.sales_customer_view_report),
                        onClick = onOpenReport,
                    )
                }
            }
        }
    }
}
