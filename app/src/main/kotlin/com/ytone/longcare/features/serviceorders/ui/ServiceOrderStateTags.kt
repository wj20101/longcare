package com.ytone.longcare.features.serviceorders.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.model.TodayServiceOrderModel

@Composable
internal fun ServiceOrderStateTags(order: TodayServiceOrderModel) {
    when (order.state) {
        0 -> {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFE8F4FF)
            ) {
                Text(
                    text = stringResource(
                        id = R.string.service_order_work_hours,
                        order.totalServiceTime
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        2 -> {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFE8F5E8)
            ) {
                Text(
                    text = "已完成",
                    color = Color(0xFF4CAF50),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFE8F4FF)
            ) {
                Text(
                    text = stringResource(
                        id = R.string.service_order_work_hours,
                        order.completeTotalTime
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        else -> {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFE8F4FF)
            ) {
                Text(
                    text = stringResource(
                        id = R.string.service_order_work_hours,
                        order.totalServiceTime
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
