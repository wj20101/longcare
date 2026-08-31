package com.ytone.longcare.features.profile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ytone.longcare.core.ui.message.UiMessageSnackbarEffect
import com.ytone.longcare.features.home.api.HomeFeatureConfig
import com.ytone.longcare.features.profile.api.ProfileActions
import com.ytone.longcare.features.profile.vm.ProfileViewModel
import androidx.compose.ui.res.stringResource
import com.ytone.longcare.feature.home.R
import com.ytone.longcare.model.CurrentUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    user: CurrentUser,
    config: HomeFeatureConfig,
    actions: ProfileActions,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val statsState by viewModel.statsState.collectAsStateWithLifecycle()
    val uiMessages by viewModel.uiMessages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    UiMessageSnackbarEffect(
        messages = uiMessages,
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::consumeUiMessage,
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshStats()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.profile_title),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp, top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LogoutButton(onClick = { viewModel.logout() })
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(
                                R.string.profile_version,
                                config.versionName,
                                config.versionCode,
                            ),
                            color = Color.Black.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                UserInfoSection(user = user)
                Spacer(modifier = Modifier.height(24.dp))
                StatsCard(actions = actions, stats = statsState)
                Spacer(modifier = Modifier.height(24.dp))
                OptionsCard(actions = actions)
                Spacer(modifier = Modifier.height(24.dp))
            }
    }
}
