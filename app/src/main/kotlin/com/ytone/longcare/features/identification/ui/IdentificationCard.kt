package com.ytone.longcare.features.identification.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.R
import com.ytone.longcare.features.identification.vm.FaceSetupState
import com.ytone.longcare.features.identification.vm.FaceVerificationState
import com.ytone.longcare.features.identification.vm.IdentificationState
import com.ytone.longcare.features.identification.vm.IdentificationViewModel
import com.ytone.longcare.features.identification.vm.PhotoUploadState
import com.ytone.longcare.features.identification.vm.VerificationType

internal object IdentificationConstants {
    const val SERVICE_PERSON = "服务人员"
    const val ELDER = "老人"
}

@Composable
fun IdentificationCard(
    personType: String,
    isVerified: Boolean,
    onVerifyClick: () -> Unit,
    viewModel: IdentificationViewModel,
    faceVerificationState: FaceVerificationState,
    photoUploadState: PhotoUploadState = PhotoUploadState.Initial,
    faceSetupState: FaceSetupState = FaceSetupState.Initial
) {
    val identificationState by viewModel.identificationState.collectAsStateWithLifecycle()
    val currentVerificationType by viewModel.currentVerificationType.collectAsStateWithLifecycle()

    val isCurrentlyVerifying = when (personType) {
        IdentificationConstants.SERVICE_PERSON -> currentVerificationType == VerificationType.SERVICE_PERSON
        IdentificationConstants.ELDER -> currentVerificationType == VerificationType.ELDER
        else -> false
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = Color.LightGray.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (personType == IdentificationConstants.SERVICE_PERSON) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_service_person),
                        contentDescription = "服务人员头像",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_elder_person),
                        contentDescription = "老人头像",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isVerified) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "验证成功",
                            tint = Color(0xFF34C759)
                        )
                        Text(
                            text = "${personType}识别成功",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF34C759)
                        )
                    }
                } else {
                    when {
                        personType == IdentificationConstants.SERVICE_PERSON && faceSetupState is FaceSetupState.UploadingImage -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "上传图片中...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }

                        personType == IdentificationConstants.SERVICE_PERSON && faceSetupState is FaceSetupState.UpdatingServer -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "更新服务器...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }

                        personType == IdentificationConstants.SERVICE_PERSON && faceSetupState is FaceSetupState.UpdatingLocal -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "更新本地数据...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }

                        personType == IdentificationConstants.SERVICE_PERSON && faceSetupState is FaceSetupState.Error -> {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "设置失败",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF3B30)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        viewModel.resetFaceSetupState()
                                        onVerifyClick()
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5A623)
                                    ),
                                    modifier = Modifier.heightIn(min = 36.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "重试",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Initializing -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "初始化中...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }

                        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Verifying -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "${personType}识别中...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }

                        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Error -> {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "验证失败",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF3B30)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        viewModel.resetFaceVerificationState()
                                        onVerifyClick()
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5A623)
                                    ),
                                    modifier = Modifier.heightIn(min = 36.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "重试",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        isCurrentlyVerifying && faceVerificationState is FaceVerificationState.Cancelled -> {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "已取消",
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        viewModel.resetFaceVerificationState()
                                        onVerifyClick()
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5A623)
                                    ),
                                    modifier = Modifier.heightIn(min = 36.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "重新识别",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        else -> {
                            val isButtonEnabled = when {
                                personType == IdentificationConstants.SERVICE_PERSON -> true
                                personType == IdentificationConstants.ELDER && identificationState == IdentificationState.SERVICE_VERIFIED -> true
                                else -> false
                            }

                            val isProcessing = personType == IdentificationConstants.ELDER && (
                                photoUploadState is PhotoUploadState.Processing ||
                                    photoUploadState is PhotoUploadState.Uploading
                                )

                            if (isProcessing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = if (photoUploadState is PhotoUploadState.Uploading) {
                                            "上传中..."
                                        } else {
                                            "处理中..."
                                        },
                                        fontSize = 14.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = onVerifyClick,
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5A623)
                                    ),
                                    enabled = isButtonEnabled,
                                    modifier = Modifier.heightIn(min = 36.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = if (personType == IdentificationConstants.ELDER) "拍照验证" else "进行${personType}识别",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
