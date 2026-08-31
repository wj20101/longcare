package com.ytone.longcare.features.home.api

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.features.home.ui.HomeCareContent
import com.ytone.longcare.features.home.ui.HomeExperienceContent
import com.ytone.longcare.features.home.ui.HomeProfileContent
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.theme.bgGradientBrush

/** App-owned values that Home may display without depending on the app BuildConfig. */
data class HomeFeatureConfig(
    val versionName: String,
    val versionCode: Long,
)

/** The resolved root experience reported to the app-owned startup observer. */
enum class HomeExperience {
    Loading,
    Care,
    Sales,
}

/** App-owned Sales content. The second slot renders Home's stateless profile content. */
typealias HomeSalesRenderer = @Composable (
    user: CurrentUser,
    profileContent: @Composable () -> Unit,
) -> Unit

/** App-owned startup observer; Home only reports its resolved experience. */
typealias HomeStartupReporter = @Composable (experience: HomeExperience) -> Unit

/**
 * The only screen entry point exported by the Home feature.
 *
 * App navigation, platform integrations, Sales state, and graph-scoped order ownership remain
 * outside this module and are supplied through narrow values, actions, and renderer callbacks.
 */
@Composable
fun HomeFeatureScreen(
    actions: HomeActions,
    config: HomeFeatureConfig,
    orderStateSource: HomeOrderStateSource,
    salesRenderer: HomeSalesRenderer,
    startupReporter: HomeStartupReporter,
) {
    val viewModel: HomeSharedViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.reportHomeEntry()
    }

    startupReporter(uiState.experience)
    HomeExperienceContent(
        experience = uiState.experience,
        loadingContent = { HomeLoadingContent() },
        careContent = {
            HomeCareContent(
                actions = actions,
                config = config,
                user = requireNotNull(uiState.user),
                selectedDashboardTab = uiState.selectedDashboardTab,
                onSelectedDashboardTab = viewModel::selectDashboardTab,
                orderStateSource = orderStateSource,
            )
        },
        salesContent = {
            salesRenderer(requireNotNull(uiState.user)) {
                HomeProfileContent(
                    user = requireNotNull(uiState.user),
                    config = config,
                    actions = actions,
                )
            }
        },
    )
}

internal fun resolveHomeExperience(userIdentity: Int?): HomeExperience = when (userIdentity) {
    null -> HomeExperience.Loading
    2 -> HomeExperience.Sales
    else -> HomeExperience.Care
}

@Composable
private fun HomeLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize().background(bgGradientBrush),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
