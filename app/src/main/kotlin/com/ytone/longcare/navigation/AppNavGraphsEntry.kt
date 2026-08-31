package com.ytone.longcare.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.feature.login.api.LoginAgreementLinks
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.feature.login.api.LoginFeatureScreen
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.features.home.api.HomeExperience
import com.ytone.longcare.features.home.api.HomeFeatureConfig
import com.ytone.longcare.features.home.api.HomeFeatureScreen
import com.ytone.longcare.features.sales.SalesExperienceScreen
import com.ytone.longcare.privacy.AgreementUrls
import com.ytone.longcare.shared.vm.TodayOrderViewModel
import com.ytone.longcare.R
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.core.ui.R as CoreUiR

internal class EntryDestinationRenderers(
    val login: @Composable (NavController, onLoginSuccess: () -> Unit) -> Unit,
    val home: @Composable (NavController, NavBackStackEntry) -> Unit,
)

internal val productionEntryDestinationRenderers = EntryDestinationRenderers(
    login = { navController, onLoginSuccess ->
        LoginDestination(
            navController = navController,
            onLoginSuccess = onLoginSuccess,
        )
    },
    home = { navController, backStackEntry ->
        HomeDestination(
            navController = navController,
            backStackEntry = backStackEntry,
        )
    },
)

internal fun NavGraphBuilder.registerEntryNavGraphs(
    navController: NavController,
    onLoginSuccess: () -> Unit,
    entryDestinationRenderers: EntryDestinationRenderers = productionEntryDestinationRenderers,
) {
    composable<LoginRoute> {
        entryDestinationRenderers.login(navController, onLoginSuccess)
    }

    navigation<HomeGraphRoute>(startDestination = HomeRoute) {
        composable<HomeRoute> { backStackEntry ->
            entryDestinationRenderers.home(navController, backStackEntry)
        }

        registerServiceOrdersListNavGraphs(navController)
    }
}

@Composable
private fun LoginDestination(
    navController: NavController,
    onLoginSuccess: () -> Unit,
) {
    ReportStartupRootDrawn(
        expectedRoot = StartupRoot.Login,
        actualReadiness = resolveStartupRootReadiness(
            entryState = AppEntryState.LoggedOut,
            userIdentity = null,
        ),
    )
    LoginFeatureScreen(
        actions = LoginFeatureActions(
            onLoginSuccess = onLoginSuccess,
            onOpenWebPage = { url, title -> navController.navigateToWebView(url, title) },
            validationEntryActions = navController.createLoginValidationEntryActions(),
        ),
        agreementLinks = LoginAgreementLinks(
            userAgreementUrl = AgreementUrls.USER_AGREEMENT_URL,
            privacyPolicyUrl = AgreementUrls.PRIVACY_POLICY_URL,
        ),
    )
}

@Composable
private fun HomeDestination(
    navController: NavController,
    backStackEntry: NavBackStackEntry,
) {
    val parentEntry = remember(backStackEntry) {
        navController.requireHomeGraphBackStackEntry()
    }
    val todayOrderViewModel: TodayOrderViewModel = hiltViewModel(parentEntry)
    val orderStateSource = remember(todayOrderViewModel) {
        todayOrderViewModel.asHomeOrderStateSource()
    }
    val userAgreementTitle = stringResource(CoreUiR.string.profile_user_agreement)
    val privacyPolicyTitle = stringResource(CoreUiR.string.profile_privacy_policy)
    val homeActions = HomeActions(
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
        onOpenWebPage = { url, title ->
            navController.navigateToWebView(url, title)
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
            backStackEntry.savedStateHandle.getStateFlow(
                NavigationConstants.CAPTURED_IMAGE_URI_KEY,
                null,
            ),
        clearCapturedImageUri = {
            backStackEntry.savedStateHandle.remove<String>(
                NavigationConstants.CAPTURED_IMAGE_URI_KEY,
            )
        },
    )
    HomeFeatureScreen(
        actions = homeActions,
        config = HomeFeatureConfig(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
        ),
        orderStateSource = orderStateSource,
        salesRenderer = { user, profileContent ->
            SalesExperienceScreen(
                actions = homeActions,
                user = user,
                profileContent = profileContent,
            )
        },
        startupReporter = { experience ->
            ReportHomeStartupRootDrawn(experience)
        },
    )
}

@Composable
private fun ReportHomeStartupRootDrawn(experience: HomeExperience) {
    val expectedRoot = when (experience) {
        HomeExperience.Loading -> StartupRoot.ResolvingSession
        HomeExperience.Care -> StartupRoot.CareHome
        HomeExperience.Sales -> StartupRoot.SalesHome
    }
    val userIdentity = when (experience) {
        HomeExperience.Loading -> null
        HomeExperience.Care -> 1
        HomeExperience.Sales -> 2
    }
    ReportStartupRootDrawn(
        expectedRoot = expectedRoot,
        actualReadiness = resolveStartupRootReadiness(
            entryState = AppEntryState.LoggedIn,
            userIdentity = userIdentity,
        ),
    )
}
