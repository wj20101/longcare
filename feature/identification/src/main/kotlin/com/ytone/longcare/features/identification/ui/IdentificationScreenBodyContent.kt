package com.ytone.longcare.features.identification.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.feature.identification.R

@Composable
internal fun IdentificationBodyContent(
    state: IdentificationScreenRenderState,
    onEvent: (IdentificationScreenEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.identification_screen_subtitle),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(20.dp))
        IdentificationCard(state = state.servicePerson, onEvent = onEvent)
        Spacer(modifier = Modifier.height(16.dp))
        IdentificationCard(state = state.elder, onEvent = onEvent)
        Spacer(modifier = Modifier.height(24.dp))
    }
}
