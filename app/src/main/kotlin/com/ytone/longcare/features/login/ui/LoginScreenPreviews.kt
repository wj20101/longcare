package com.ytone.longcare.features.login.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ytone.longcare.theme.LongCareTheme

@Preview
@Composable
fun AgreementTextPreview() {
    LongCareTheme {
        AgreementText(
            onUserAgreementClick = {},
            onPrivacyPolicyClick = {}
        )
    }
}
