package com.ytone.longcare.features.nursingexecution.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.model.ServiceOrderInfoModel

@Composable
internal fun ClientInfoCard(modifier: Modifier, orderInfo: ServiceOrderInfoModel) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 32.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoRow(
                label = stringResource(R.string.nursing_execution_label_name),
                value = orderInfo.userInfo?.name ?: ""
            )
            InfoRow(
                label = stringResource(R.string.nursing_execution_label_age),
                value = orderInfo.userInfo?.age?.toString() ?: ""
            )
            InfoRow(
                label = stringResource(R.string.nursing_execution_label_id_number),
                value = orderInfo.userInfo?.identityCardNumber ?: ""
            )
            InfoRow(
                label = stringResource(R.string.nursing_execution_label_address),
                value = orderInfo.userInfo?.address ?: ""
            )
            InfoRow(
                label = stringResource(R.string.nursing_execution_label_service_content),
                value = (orderInfo.projectList ?: emptyList()).joinToString { it.projectName }
            )
        }
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}
