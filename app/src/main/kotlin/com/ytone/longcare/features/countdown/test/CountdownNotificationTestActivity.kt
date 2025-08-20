package com.ytone.longcare.features.countdown.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.countdown.debug.CountdownNotificationDebugHelper
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CountdownNotificationTestActivity : ComponentActivity() {
    
    @Inject
    lateinit var countdownNotificationManager: CountdownNotificationManager
    
    @Inject
    lateinit var debugHelper: CountdownNotificationDebugHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CountdownNotificationTestScreen(
                countdownNotificationManager = countdownNotificationManager,
                debugHelper = debugHelper
            )
        }
    }
}

@Composable
fun CountdownNotificationTestScreen(
    countdownNotificationManager: CountdownNotificationManager,
    debugHelper: CountdownNotificationDebugHelper
) {
    val context = LocalContext.current
    var testResult by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "倒计时通知测试",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Button(
            onClick = {
                debugHelper.diagnoseNotificationIssues(context)
            }
        ) {
            Text("运行诊断")
        }
        
        Button(
            onClick = {
                // 设置1分钟后的测试闹钟
                val triggerTime = System.currentTimeMillis() + 60000 // 1分钟后
                countdownNotificationManager.scheduleCountdownAlarm(
                    orderId = 12345L,
                    serviceName = "测试服务",
                    triggerTimeMillis = triggerTime
                )
                testResult = "已设置1分钟后的测试闹钟"
            }
        ) {
            Text("设置1分钟测试闹钟")
        }
        
        Button(
            onClick = {
                // 设置10秒后的测试闹钟（用于快速测试）
                countdownNotificationManager.scheduleCountdownAlarm(
                    orderId = 12345L,
                    serviceName = "快速测试服务",
                    triggerTimeMillis = System.currentTimeMillis() + 10000 // 10秒后
                )
                testResult = "已设置10秒后的快速测试闹钟"
            }
        ) {
            Text("设置10秒测试闹钟")
        }
        
        Button(
            onClick = {
                countdownNotificationManager.cancelCountdownAlarm()
                testResult = "已取消所有闹钟"
            }
        ) {
            Text("取消闹钟")
        }
        
        Button(
            onClick = {
                countdownNotificationManager.showCountdownCompletionNotification(
                    orderId = 12345L,
                    serviceName = "手动测试通知"
                )
                testResult = "已手动显示通知"
            }
        ) {
            Text("手动显示通知")
        }
        
        if (testResult.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = testResult,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}