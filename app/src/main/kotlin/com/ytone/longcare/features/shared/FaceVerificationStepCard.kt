package com.ytone.longcare.features.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.shared.vm.FaceVerificationViewModel

@Composable
internal fun FaceVerificationStepCard(
    uiState: FaceVerificationViewModel.FaceVerifyUiState,
    currentUserId: String?,
    onStartVerification: () -> Unit,
    onResetAll: () -> Unit,
    onClearError: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "第二步：开始人脸验证",
                style = MaterialTheme.typography.titleMedium
            )

            when (uiState) {
                is FaceVerificationViewModel.FaceVerifyUiState.Idle -> {
                    Text(
                        text = "准备开始人脸验证",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = onStartVerification,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !currentUserId.isNullOrBlank()
                    ) {
                        Text("开始验证")
                    }
                }

                is FaceVerificationViewModel.FaceVerifyUiState.Initializing -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Column {
                            Text(
                                text = "正在初始化...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "获取签名参数中",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is FaceVerificationViewModel.FaceVerifyUiState.Verifying -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Column {
                            Text(
                                text = "正在进行人脸验证...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "请按照提示完成人脸验证",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is FaceVerificationViewModel.FaceVerifyUiState.Success -> {
                    Text(
                        text = "✓ 人脸验证成功",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(
                        onClick = onResetAll,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("重新验证")
                    }
                }

                is FaceVerificationViewModel.FaceVerifyUiState.Error -> {
                    Text(
                        text = "✗ 验证失败",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClearError,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("取消")
                        }

                        Button(
                            onClick = onStartVerification,
                            modifier = Modifier.weight(1f),
                            enabled = !currentUserId.isNullOrBlank()
                        ) {
                            Text("重试")
                        }
                    }
                }

                is FaceVerificationViewModel.FaceVerifyUiState.Cancelled -> {
                    Text(
                        text = "✗ 验证已取消",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    Button(
                        onClick = onStartVerification,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !currentUserId.isNullOrBlank()
                    ) {
                        Text("重新开始验证")
                    }
                }
            }
        }
    }
}
