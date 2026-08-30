package com.ytone.longcare.features.identification.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ytone.longcare.theme.bgGradientBrush

@Composable
internal fun IdentificationScreenContent(
    state: IdentificationScreenRenderState,
    onEvent: (IdentificationScreenEvent) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(bgGradientBrush)) {
        Scaffold(
            topBar = {
                IdentificationTopBar(
                    onNavigateBack = { onEvent(IdentificationScreenEvent.NavigateBack) },
                )
            },
            bottomBar = {
                IdentificationBottomBar(
                    enabled = state.nextEnabled,
                    onNavigateToSelectService = {
                        onEvent(IdentificationScreenEvent.ContinueToServiceSelection)
                    },
                )
            },
            containerColor = Color.Transparent,
        ) { paddingValues ->
            IdentificationBodyContent(
                state = state,
                onEvent = onEvent,
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}
