package com.ytone.longcare.features.identification.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.feature.identification.R
import com.ytone.longcare.ui.components.BottomSafeActionContainer

internal const val IDENTIFICATION_BACK_TAG = "identification_back"
internal const val IDENTIFICATION_NEXT_TAG = "identification_next"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdentificationTopBar(onNavigateBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                stringResource(R.string.identification_screen_title),
                fontWeight = FontWeight.Bold,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = singleClick(onClick = onNavigateBack),
                modifier = Modifier.testTag(IDENTIFICATION_BACK_TAG),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.identification_back),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
        ),
    )
}

@Composable
internal fun IdentificationBottomBar(
    enabled: Boolean,
    onNavigateToSelectService: () -> Unit,
) {
    BottomSafeActionContainer(
        horizontalPadding = 20.dp,
        topPadding = 16.dp,
        extraBottomPadding = 24.dp,
    ) {
        Button(
            onClick = singleClick(onClick = onNavigateToSelectService),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(IDENTIFICATION_NEXT_TAG),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4A90E2),
                disabledContainerColor = Color(0xFF4A90E2).copy(alpha = 0.5f),
            ),
            enabled = enabled,
        ) {
            Text(
                stringResource(R.string.identification_next_step),
                fontSize = 16.sp,
                color = Color.White,
            )
        }
    }
}
