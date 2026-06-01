package com.ytone.longcare.features.profile.api

data class ProfileActions(
    val onNavigateToHaveServiceUserList: () -> Unit,
    val onNavigateToNoServiceUserList: () -> Unit,
    val onOpenUserAgreement: () -> Unit,
    val onOpenPrivacyPolicy: () -> Unit
)
