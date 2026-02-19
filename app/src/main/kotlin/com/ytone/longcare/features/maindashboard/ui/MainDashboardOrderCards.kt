package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.theme.IndicatorGradientEnd
import com.ytone.longcare.theme.IndicatorGradientStart

@Composable
fun InOrderServiceItem(
    order: ServiceOrderModel,
    onClick: () -> Unit = { }
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = order.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFFF3E0)
                    ) {
                        Text(
                            text = stringResource(id = R.string.service_order_work_hours, order.planTotalTime),
                            color = Color(0xFFFF9800),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "地址: ${order.liveAddress}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "进入详情",
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun CustomTabItem(
    text: String,
    isSelected: Boolean
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF007AFF) else Color(0xFF999999),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (isSelected) {
            val textLayoutResult = textMeasurer.measure(
                text = text,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
            val textWidthDp = with(density) { textLayoutResult.size.width.toDp() }

            Box(
                modifier = Modifier
                    .width(textWidthDp)
                    .height(2.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                IndicatorGradientStart,
                                IndicatorGradientEnd
                            )
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(2.dp)
            )
        }
    }
}
