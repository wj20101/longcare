package com.ytone.longcare.features.location.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel

@Composable
fun LocationTrackingScreen(
    viewModel: LocationTrackingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    // 从 ViewModel 订阅追踪状态
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                // 测试用的 OrderInfoRequestModel
                viewModel.onStartClicked(
                    com.ytone.longcare.api.request.OrderInfoRequestModel(
                        orderId = 123456L,
                        planId = 0
                    )
                )
            } else {
                Toast.makeText(context, "需要定位权限才能开始任务", Toast.LENGTH_LONG).show()
            }
        }
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
                    Toast.makeText(context, "请先在系统设置中开启定位服务", Toast.LENGTH_LONG).show()
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    return@Button
                }

                val permissionsToRequest = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                }

                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            },
            enabled = !isTracking
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isTracking) "定位上报中..." else "开启定位上报任务")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.onStopClicked() },
            enabled = isTracking
        ) {
            Text("结束定位上报任务")
        }

        if (isTracking) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }
    }
}