package com.ytone.longcare.features.identification.ui

import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.features.identification.vm.IdentificationViewModel
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.navigation.navigateToFaceRecognitionGuide


// 身份认证状态枚举
enum class IdentificationState {
    INITIAL,           // 初始状态
    SERVICE_VERIFIED,  // 服务人员已验证
    ELDER_VERIFIED     // 老人已验证
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentificationScreen(
    navController: NavController,
    orderId: Long = 0L,
    viewModel: IdentificationViewModel = hiltViewModel()
) {

    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    val identificationState by viewModel.identificationState.collectAsStateWithLifecycle()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("身份认证", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            },
            containerColor = Color.Transparent,
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "请按照要求进行人脸识别",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(20.dp))

                // 服务人员识别卡片
                IdentificationCard(
                    personType = "服务人员",
                    isVerified = identificationState == IdentificationState.SERVICE_VERIFIED || 
                                identificationState == IdentificationState.ELDER_VERIFIED,
                    onVerifyClick = { 
                        viewModel.verifyServicePerson()
                    },
                    viewModel = viewModel
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 老人识别卡片
                IdentificationCard(
                    personType = "老人",
                    isVerified = identificationState == IdentificationState.ELDER_VERIFIED,
                    onVerifyClick = { 
                        viewModel.verifyElder()
                    },
                    viewModel = viewModel
                )

                Spacer(modifier = Modifier.weight(1f))

                // 下一步按钮
                Button(
                    onClick = { navController.navigateToFaceRecognitionGuide(orderId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4A90E2), // 蓝色
                        disabledContainerColor = Color(0xFF4A90E2).copy(alpha = 0.5f)
                    ),
                    enabled = identificationState == IdentificationState.ELDER_VERIFIED
                ) {
                    Text("下一步", fontSize = 16.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun IdentificationCard(
    personType: String,
    isVerified: Boolean,
    onVerifyClick: () -> Unit,
    viewModel: IdentificationViewModel = hiltViewModel()
) {
    val identificationState by viewModel.identificationState.collectAsStateWithLifecycle()
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 人物头像框
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
                // 头像图片
                if (personType == "服务人员") {
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
                    // 已验证状态
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "验证成功",
                            tint = Color(0xFF34C759) // 绿色
                        )
                        Text(
                            text = "${personType}识别成功",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF34C759) // 绿色
                        )
                    }
                } else {
                    // 未验证状态，显示验证按钮
                    val isButtonEnabled = when {
                        personType == "服务人员" -> true // 服务人员按钮始终可用
                        personType == "老人" && identificationState == IdentificationState.SERVICE_VERIFIED -> true // 老人按钮仅在服务人员已验证时可用
                        else -> false // 其他情况按钮不可用
                    }
                    
                    Button(
                        onClick = onVerifyClick,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF5A623) // 橙色
                        ),
                        enabled = isButtonEnabled,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("进行${personType}识别", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}