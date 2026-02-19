package com.ytone.longcare.features.login.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.theme.PrimaryBlue

@Composable
internal fun LoginNfcTestButtons(
    actions: LoginFeatureActions,
    modifier: Modifier = Modifier
) {
    val buttonColors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue.copy(alpha = 0.1f))
    val buttonShape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LoginTestButton("碰一碰测试", buttonShape, buttonColors, actions.onOpenNfcTest)
        LoginTestButton("相机测试", buttonShape, buttonColors, actions.onOpenCameraTest)
        LoginTestButton("人脸验证测试", buttonShape, buttonColors, actions.onOpenFaceVerificationTest)
        LoginTestButton("人脸采集测试", buttonShape, buttonColors, actions.onOpenManualFaceCapture)
        LoginTestButton("手动人脸捕获", buttonShape, buttonColors, actions.onOpenManualFaceCapture)
    }
}

@Composable
private fun LoginTestButton(
    text: String,
    shape: RoundedCornerShape,
    colors: ButtonColors,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = shape,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = PrimaryBlue,
            fontSize = 14.sp
        )
    }
}
