package com.ytone.longcare.presentation.validation

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ytone.longcare.R
import com.ytone.longcare.feature.login.api.LoginValidationEntryActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoginValidationEntrySheet(
    visible: Boolean,
    validationEntryActions: LoginValidationEntryActions,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("login_test_entry_sheet"),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.login_validation_entry_title),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.login_validation_entry_description),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.login_validation_entry_face_verification))
                },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.login_validation_entry_face_verification_description,
                        ),
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("login_face_verification_test_entry")
                        .clickable {
                            onDismiss()
                            context.startActivity(
                                Intent(context, FaceVerificationValidationActivity::class.java),
                            )
                        },
            )
            HorizontalDivider()
            LoginValidationEntryItem(
                headline = stringResource(R.string.login_validation_entry_nfc),
                supporting = stringResource(R.string.login_validation_entry_nfc_description),
                testTag = "login_nfc_test_entry",
                onClick = {
                    onDismiss()
                    context.startActivity(Intent(context, NfcValidationActivity::class.java))
                },
            )
            HorizontalDivider()
            LoginValidationEntryItem(
                headline = stringResource(R.string.login_validation_entry_camera),
                supporting = stringResource(R.string.login_validation_entry_camera_description),
                testTag = "login_camera_test_entry",
                onClick = {
                    onDismiss()
                    validationEntryActions.onOpenCameraValidation()
                },
            )
            HorizontalDivider()
            LoginValidationEntryItem(
                headline = stringResource(R.string.login_validation_entry_backup_face),
                supporting =
                    stringResource(R.string.login_validation_entry_backup_face_description),
                testTag = "login_legacy_face_test_entry",
                onClick = {
                    onDismiss()
                    validationEntryActions.onOpenBackupFaceVerification()
                },
            )
            HorizontalDivider()
            LoginValidationEntryItem(
                headline = stringResource(R.string.login_validation_entry_manual_face),
                supporting =
                    stringResource(R.string.login_validation_entry_manual_face_description),
                testTag = "login_manual_face_test_entry",
                onClick = {
                    onDismiss()
                    validationEntryActions.onOpenManualFaceCapture()
                },
            )
        }
    }
}

@Composable
private fun LoginValidationEntryItem(
    headline: String,
    supporting: String,
    testTag: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(supporting) },
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .clickable(onClick = onClick),
    )
}
