package com.ytone.longcare.features.identification.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.ytone.longcare.feature.identification.R

@Composable
internal fun IdentificationCardStatusArea(
    state: IdentificationCardRenderState,
    onEvent: (IdentificationScreenEvent) -> Unit,
) {
    when (state.status) {
        IdentificationCardStatus.VERIFIED -> VerifiedStatusRow(personType = state.personType)
        IdentificationCardStatus.FACE_SETUP_UPLOADING_IMAGE ->
            LoadingStatusRow(text = stringResource(R.string.identification_uploading_image))

        IdentificationCardStatus.FACE_SETUP_UPDATING_SERVER ->
            LoadingStatusRow(text = stringResource(R.string.identification_updating_server))

        IdentificationCardStatus.FACE_SETUP_UPDATING_LOCAL ->
            LoadingStatusRow(text = stringResource(R.string.identification_updating_local))

        IdentificationCardStatus.FACE_SETUP_ERROR -> RetryStatusColumn(
            statusText = stringResource(R.string.identification_setup_failed),
            statusColor = Color(0xFFFF3B30),
            buttonText = stringResource(R.string.identification_common_retry),
            testTag = state.retryTag,
            onClick = { state.retryEvent()?.let(onEvent) },
        )

        IdentificationCardStatus.FACE_INITIALIZING ->
            LoadingStatusRow(text = stringResource(R.string.identification_initializing))

        IdentificationCardStatus.FACE_VERIFYING -> LoadingStatusRow(
            text = stringResource(
                R.string.identification_recognizing,
                stringResource(state.personType.labelRes),
            ),
        )

        IdentificationCardStatus.FACE_ERROR -> RetryStatusColumn(
            statusText = stringResource(R.string.identification_verification_failed),
            statusColor = Color(0xFFFF3B30),
            buttonText = stringResource(R.string.identification_common_retry),
            testTag = state.retryTag,
            onClick = { state.retryEvent()?.let(onEvent) },
        )

        IdentificationCardStatus.FACE_CANCELLED -> RetryStatusColumn(
            statusText = stringResource(R.string.identification_cancelled),
            statusColor = Color(0xFF666666),
            buttonText = stringResource(R.string.identification_retry),
            testTag = state.retryTag,
            onClick = { state.retryEvent()?.let(onEvent) },
        )

        IdentificationCardStatus.PHOTO_PROCESSING ->
            LoadingStatusRow(text = stringResource(R.string.identification_processing))

        IdentificationCardStatus.PHOTO_UPLOADING ->
            LoadingStatusRow(text = stringResource(R.string.identification_uploading))

        IdentificationCardStatus.ACTION -> PrimaryActionButton(
            text = if (state.personType == IdentificationPersonType.ELDER) {
                stringResource(R.string.identification_elder_photo_action)
            } else {
                stringResource(
                    R.string.identification_action,
                    stringResource(state.personType.labelRes),
                )
            },
            enabled = state.actionEnabled,
            textSize = 12.sp,
            testTag = state.actionTag,
            onClick = { onEvent(state.personType.primaryEvent()) },
        )
    }
}

private val IdentificationCardRenderState.actionTag: String
    get() = when (personType) {
        IdentificationPersonType.SERVICE_PERSON -> SERVICE_PERSON_ACTION_TAG
        IdentificationPersonType.ELDER -> ELDER_ACTION_TAG
    }

private val IdentificationCardRenderState.retryTag: String
    get() = when (personType) {
        IdentificationPersonType.SERVICE_PERSON -> SERVICE_PERSON_RETRY_TAG
        IdentificationPersonType.ELDER -> ELDER_RETRY_TAG
    }
