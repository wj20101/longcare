package com.ytone.longcare.features.location.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ytone.longcare.features.location.provider.LocationStrategy

/**
 * 定位策略选择器组件
 * 用于在UI中切换定位策略
 */
@Composable
fun LocationStrategySelector(
    currentStrategy: LocationStrategy,
    onStrategyChanged: (LocationStrategy) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "定位策略选择",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Column(
                modifier = Modifier.selectableGroup()
            ) {
                LocationStrategy.values().forEach { strategy ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (strategy == currentStrategy),
                                onClick = { onStrategyChanged(strategy) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (strategy == currentStrategy),
                            onClick = null // null recommended for accessibility with screenreaders
                        )
                        Text(
                            text = getStrategyDisplayName(strategy),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            
            Text(
                text = getStrategyDescription(currentStrategy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * 获取策略的显示名称
 */
private fun getStrategyDisplayName(strategy: LocationStrategy): String {
    return when (strategy) {
        LocationStrategy.SYSTEM -> "系统定位"
        LocationStrategy.AMAP -> "高德定位"
        LocationStrategy.AUTO -> "自动选择（推荐）"
    }
}

/**
 * 获取策略的描述信息
 */
private fun getStrategyDescription(strategy: LocationStrategy): String {
    return when (strategy) {
        LocationStrategy.SYSTEM -> "使用Android系统原生定位服务，包括GPS和网络定位。"
        LocationStrategy.AMAP -> "使用高德地图定位服务，通常具有更高的精度和更快的定位速度。"
        LocationStrategy.AUTO -> "优先使用高德定位，如果失败则自动回退到系统定位，确保定位成功率。"
    }
}