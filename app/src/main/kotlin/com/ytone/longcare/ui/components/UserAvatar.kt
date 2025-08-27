package com.ytone.longcare.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.CrashLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 一个通用的、可复用的用户头像显示组件。
 *
 * @param modifier Modifier 应用于此组件。
 * @param avatarUrl 头像图片的网络 URL，可以为空。
 * @param size 头像的尺寸（直径）。
 * @param onClick 点击头像时触发的回调，可以为空。
 * @param onLongPress 长按头像时触发的回调，可以为空。
 */
@Composable
fun UserAvatar(
    modifier: Modifier = Modifier,
    avatarUrl: String?,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showCrashLogDialog by remember { mutableStateOf(false) }
    var crashLogs by remember { mutableStateOf<List<File>>(emptyList()) }
    // 使用 Coil 的 rememberAsyncImagePainter 来异步加载网络图片
    val painter = rememberAsyncImagePainter(
        model = avatarUrl,
        // 建议您在 drawable 中创建一个更合适的默认头像图标，例如 ic_default_avatar
        placeholder = ColorPainter(Color.LightGray),
        error = ColorPainter(Color.LightGray)
    )

    Image(
        painter = painter,
        contentDescription = stringResource(R.string.user_avatar_description),
        modifier = modifier
            .size(size) // 1. 应用尺寸
            .clip(CircleShape) // 2. 裁剪为圆形
            .background(Color.White) // 3. 添加白色背景，防止透明图片看不清
            .border(1.dp, Color.LightGray.copy(alpha = 0.5f), CircleShape) // 4. (可选)添加一个细微的边框，增加质感
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick?.invoke() },
                    onLongPress = {
                        // 处理长按事件
                        onLongPress?.invoke()
                        
                        // 检查是否有崩溃日志
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val allLogs = CrashLogManager.getAllCrashLogs(context)
                                if (allLogs.isNotEmpty()) {
                                    crashLogs = allLogs
                                    showCrashLogDialog = true
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                )
            },
        contentScale = ContentScale.Crop // 确保图片能填满圆形，不变形
    )
    
    // 显示崩溃日志选择对话框
    if (showCrashLogDialog) {
        CrashLogSelectionDialog(
            crashLogs = crashLogs,
            onDismiss = { showCrashLogDialog = false },
            onConfirm = { selectedLogs ->
                showCrashLogDialog = false
                if (selectedLogs.isNotEmpty()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val shareIntent = CrashLogManager.shareCrashLogs(context, selectedLogs)
                            if (shareIntent != null) {
                                context.startActivity(shareIntent)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            },
            onRefresh = {
                // 刷新日志列表
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        crashLogs = CrashLogManager.getAllCrashLogs(context)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        )
    }
}