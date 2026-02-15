package com.ytone.longcare.presentation.countdown

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.ytone.longcare.common.utils.logI
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.receiver.DismissAlarmReceiver
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.theme.LongCareTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CountdownAlarmActivity : AppCompatActivity() {
    
    @Inject
    lateinit var countdownNotificationManager: CountdownNotificationManager
    
    private var autoCloseHandler: Handler? = null
    private var autoCloseRunnable: Runnable? = null
    
    companion object {
        private const val TAG = "CountdownAlarmActivity"
        private const val EXTRA_REQUEST = "extra_request"
        private const val EXTRA_SERVICE_NAME = "service_name"
        private const val EXTRA_AUTO_CLOSE_ENABLED = "auto_close_enabled"
        private const val AUTO_CLOSE_DELAY_MS = 30000L // 30秒
        
        fun createIntent(context: Context, request: com.ytone.longcare.api.request.OrderInfoRequestModel, serviceName: String, autoCloseEnabled: Boolean = true): Intent {
            return Intent(context, CountdownAlarmActivity::class.java).apply {
                putExtra(EXTRA_REQUEST, request)
                putExtra(EXTRA_SERVICE_NAME, serviceName)
                putExtra(EXTRA_AUTO_CLOSE_ENABLED, autoCloseEnabled)
                // 确保Activity可以从后台启动
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
    }
    
    private val stopAlarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopAlarmAndFinish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        logI("========================================")
        logI("✅ CountdownAlarmActivity onCreate 被调用")
        logI("========================================")
        
        // 设置锁屏显示和点亮屏幕
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            // Android 8.1+ (API 27+) 使用 Activity 的方法
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            // 旧版本使用 WindowManager flags
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        logI("使用 setShowWhenLocked/setTurnScreenOn 设置锁屏显示")
        
        // 设置Window flags保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 使用 WindowCompat 设置全屏显示
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        logI("Window flags 已设置")
        
        val request =
            IntentCompat.getParcelableExtra(
                intent,
                EXTRA_REQUEST,
                com.ytone.longcare.api.request.OrderInfoRequestModel::class.java
            ) ?: com.ytone.longcare.api.request.OrderInfoRequestModel(orderId = -1L, planId = 0)
        
        val orderId = request.orderId.toString()
        val serviceName = intent.getStringExtra(EXTRA_SERVICE_NAME) ?: "护理服务"
        val autoCloseEnabled = intent.getBooleanExtra(EXTRA_AUTO_CLOSE_ENABLED, true)
        
        // 注册停止响铃广播接收器
        val filter = IntentFilter(DismissAlarmReceiver.ACTION_STOP_ALARM)
        ContextCompat.registerReceiver(this, stopAlarmReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        
        // 如果启用自动关闭，设置30秒后自动关闭
        if (autoCloseEnabled) {
            setupAutoClose()
        }
        
        setContent {
            LongCareTheme {
                CountdownAlarmScreen(
                    orderId = orderId,
                    serviceName = serviceName,
                    onDismiss = {
                        stopAlarmAndFinish()
                    }
                )
            }
        }

        // 禁用返回键，强制用户点击关闭按钮
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 拦截返回键，不做任何操作
            }
        })
    }
    
    private fun setupAutoClose() {
        autoCloseHandler = Handler(Looper.getMainLooper())
        autoCloseRunnable = Runnable {
            stopAlarmAndFinish()
        }
        autoCloseHandler?.postDelayed(autoCloseRunnable!!, AUTO_CLOSE_DELAY_MS)
    }
    
    private fun cancelAutoClose() {
        autoCloseRunnable?.let { runnable ->
            autoCloseHandler?.removeCallbacks(runnable)
        }
        autoCloseHandler = null
        autoCloseRunnable = null
    }
    
    private fun stopAlarmAndFinish() {
        // 取消自动关闭
        cancelAutoClose()
        
        // 停止响铃服务
        AlarmRingtoneService.stopRingtone(this)
        
        // 取消通知
        countdownNotificationManager.cancelCountdownCompletionNotification()
        
        // 关闭Activity
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(stopAlarmReceiver)
        } catch (e: Exception) {
            // 忽略取消注册失败的异常
        }
        stopAlarmAndFinish()
    }
}

@Composable
fun CountdownAlarmScreen(
    orderId: String,
    serviceName: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 标题
                Text(
                    text = "⏰ 服务时间到！",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                
                // 服务信息
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = serviceName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "订单号: $orderId",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // 提示信息
                Text(
                    text = "您的护理服务时间已到，请及时处理相关事务。",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                
                // 关闭按钮
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "我知道了",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
