package com.ytone.longcare.features.login.ui

import com.ytone.longcare.feature.login.api.LoginAgreementLinks
import com.ytone.longcare.features.login.vm.StartConfigUiState

internal fun resolveUserAgreementUrl(
    startConfigState: StartConfigUiState,
    fallbackLinks: LoginAgreementLinks,
): String =
    (startConfigState as? StartConfigUiState.Success)
        ?.data
        ?.userXieYiUrl
        ?.takeIf { it.isNotEmpty() }
        ?: fallbackLinks.userAgreementUrl

internal fun resolvePrivacyPolicyUrl(fallbackLinks: LoginAgreementLinks): String =
    fallbackLinks.privacyPolicyUrl
