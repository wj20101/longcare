package com.ytone.longcare.feature.login.api

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ytone.longcare.features.login.ui.LoginRouteScreen
import com.ytone.longcare.features.login.vm.LoginViewModel

/** Login feature 的唯一公开 Compose 页面入口。 */
@Composable
fun LoginFeatureScreen(
    actions: LoginFeatureActions,
    agreementLinks: LoginAgreementLinks,
) {
    val viewModel: LoginViewModel = hiltViewModel()
    LoginRouteScreen(
        actions = actions,
        agreementLinks = agreementLinks,
        viewModel = viewModel,
    )
}
