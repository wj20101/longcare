package com.ytone.longcare.features.identification.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.features.identification.vm.IdentificationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IdentificationTopBar(onNavigateBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text("身份认证", fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = singleClick(onClick = onNavigateBack)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White
        )
    )
}

@Composable
internal fun IdentificationBottomBar(
    identificationState: IdentificationState,
    onNavigateToSelectService: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp, top = 16.dp)
        ) {
            Button(
                onClick = singleClick(onClick = onNavigateToSelectService),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4A90E2),
                    disabledContainerColor = Color(0xFF4A90E2).copy(alpha = 0.5f)
                ),
                enabled = identificationState == IdentificationState.ELDER_VERIFIED
            ) {
                Text("下一步", fontSize = 16.sp, color = Color.White)
            }
        }
    }
}
