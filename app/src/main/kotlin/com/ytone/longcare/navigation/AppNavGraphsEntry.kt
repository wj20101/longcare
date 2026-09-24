package com.ytone.longcare.navigation

import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.features.home.ui.HomeScreen
import com.ytone.longcare.features.login.ui.LoginScreen
import com.ytone.longcare.privacy.AgreementUrls
import com.ytone.longcare.shared.vm.TodayOrderViewModel
import com.ytone.longcare.R
import com.ytone.longcare.platform.nfc.CardDiagnosticsContent

internal fun AppEntryProviderBuilder.registerEntryNavGraphs(navController: AppNavigator) {
    destination<LoginRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        LoginScreen(
            actions = LoginFeatureActions(
                onLoginSuccess = { navController.navigateToHomeFromLogin() },
                onOpenWebPage = { url, title -> navController.navigateToWebView(url, title) },
            ),
            onOpenCardDiagnostics = { navController.navigateWhenResumed(CardDiagnosticsRoute) },
        )
    }

    destination<CardDiagnosticsRoute> { backStackEntry ->
        val controller = navController.forEntry(backStackEntry.id,
            androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        CardDiagnosticsContent {
            controller.popBackStack()
        }
    }

    destination<HomeRoute> { backStackEntry ->
        val navController = navController.forEntry(backStackEntry.id, androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle)
        val parentEntry = LocalHomeViewModelStoreOwner.current
        val todayOrderViewModel: TodayOrderViewModel = hiltViewModel(parentEntry)
        HomeScreen(
            actions = homeActions(navController, backStackEntry),
            navigator = navController,
            todayOrderViewModel = todayOrderViewModel
        )
    }

    registerServiceOrdersListNavGraphs(navController)
}

@androidx.compose.runtime.Composable
internal fun homeActions(navController: AppNavigator, backStackEntry: AppEntryHandle): HomeActions {
    val userAgreementTitle = stringResource(R.string.profile_user_agreement)
    val privacyPolicyTitle = stringResource(R.string.profile_privacy_policy)
    return HomeActions(
                onNavigateToCarePlansList = { navController.navigateToCarePlansList() },
                onNavigateToServiceRecordsList = { navController.navigateToServiceRecordsList() },
                onNavigateToNursingExecution = { orderKey ->
                    navController.navigateToNursingExecution(orderKey)
                },
                onNavigateToService = { orderKey ->
                    navController.navigateToService(orderKey)
                },
                onNavigateToServiceCountdown = { orderKey, projectIdList ->
                    navController.navigateToServiceCountdown(orderKey, projectIdList)
                },
                onNavigateToHaveServiceUserList = { navController.navigateToHaveServiceUserList() },
                onNavigateToNoServiceUserList = { navController.navigateToNoServiceUserList() },
                onOpenEvaluationReport = { url, title ->
                    navController.navigateToEvaluationReport(url, title)
                },
                onOpenEvaluationPage = { url, title ->
                    navController.navigateToEvaluationForm(url, title)
                },
                onOpenUserAgreement = {
                    navController.navigateToWebView(
                        AgreementUrls.USER_AGREEMENT_URL,
                        userAgreementTitle,
                    )
                },
                onOpenPrivacyPolicy = {
                    navController.navigateToWebView(
                        AgreementUrls.PRIVACY_POLICY_URL,
                        privacyPolicyTitle,
                    )
                },
                onNavigateToCamera = { watermarkData ->
                    navController.navigateToCamera(watermarkData)
                },
                capturedImageUriFlow =
                backStackEntry.results.getStateFlow(
                    NavigationConstants.CAPTURED_IMAGE_URI_KEY,
                    null,
                ),
                clearCapturedImageUri = {
                    backStackEntry.results.remove<String>(
                        NavigationConstants.CAPTURED_IMAGE_URI_KEY
                    )
                },
            )
}
