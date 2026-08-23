package com.ytone.longcare.features.servicecountdown.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.R
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState

@Composable
fun CountdownTimerCard(
    countdownState: ServiceCountdownState,
    formattedTime: String = "12:00:00",
    onOpenPhotoUpload: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val (timeText, statusText) = when (countdownState) {
                    ServiceCountdownState.RUNNING -> formattedTime to
                        stringResource(R.string.service_countdown_status_running)
                    ServiceCountdownState.COMPLETED -> "00:00:00" to
                        stringResource(R.string.service_countdown_status_running)
                    ServiceCountdownState.OVERTIME -> formattedTime to
                        stringResource(R.string.service_countdown_status_overtime)
                    ServiceCountdownState.ENDED -> "00:00:00" to
                        stringResource(R.string.service_countdown_status_ended)
                }

                Text(
                    text = timeText,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = statusText,
                    fontSize = 20.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Button(
                onClick = singleClick { onOpenPhotoUpload() },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5A623))
            ) {
                Text(stringResource(R.string.service_countdown_album), color = Color.White)
            }
        }
    }
}
