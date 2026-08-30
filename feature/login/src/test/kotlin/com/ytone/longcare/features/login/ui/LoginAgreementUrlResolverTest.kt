package com.ytone.longcare.features.login.ui

import com.ytone.longcare.feature.login.api.LoginAgreementLinks
import com.ytone.longcare.features.login.vm.StartConfigUiState
import com.ytone.longcare.model.StartConfigResultModel
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginAgreementUrlResolverTest {

    private val fallbackLinks = LoginAgreementLinks(
        userAgreementUrl = "https://fallback.example.test/user",
        privacyPolicyUrl = "https://fallback.example.test/privacy",
    )

    @Test
    fun `successful config uses dynamic user agreement without filtering host`() {
        val dynamicUrl = "http://192.0.2.1:8080/user?source=server"

        val resolved = resolveUserAgreementUrl(
            StartConfigUiState.Success(StartConfigResultModel(userXieYiUrl = dynamicUrl)),
            fallbackLinks,
        )

        assertEquals(dynamicUrl, resolved)
    }

    @Test
    fun `empty dynamic user agreement uses app fallback`() {
        val resolved = resolveUserAgreementUrl(
            StartConfigUiState.Success(StartConfigResultModel(userXieYiUrl = "")),
            fallbackLinks,
        )

        assertEquals(fallbackLinks.userAgreementUrl, resolved)
    }

    @Test
    fun `loading config uses app fallback`() {
        assertEquals(
            fallbackLinks.userAgreementUrl,
            resolveUserAgreementUrl(StartConfigUiState.Loading, fallbackLinks),
        )
    }

    @Test
    fun `failed config uses app fallback`() {
        assertEquals(
            fallbackLinks.userAgreementUrl,
            resolveUserAgreementUrl(StartConfigUiState.Error("network"), fallbackLinks),
        )
    }

    @Test
    fun `privacy policy always uses app supplied fallback`() {
        assertEquals(
            fallbackLinks.privacyPolicyUrl,
            resolvePrivacyPolicyUrl(fallbackLinks),
        )
    }
}
