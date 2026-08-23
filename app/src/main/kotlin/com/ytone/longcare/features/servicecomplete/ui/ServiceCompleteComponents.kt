package com.ytone.longcare.features.servicecomplete.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.ui.screen.TagCategory

@Composable
fun ThankYouCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.service_complete_illustration),
                contentDescription = stringResource(
                    R.string.service_complete_illustration_description,
                ),
                modifier = Modifier
                    .height(150.dp)
                    .fillMaxWidth(),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.service_complete_device_disconnected),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.service_complete_thanks),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ServiceChecklistSection(summary: ServiceSummary) {
    val tagHeightEstimate = 32.dp
    val tagOverlap = 12.dp

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = tagHeightEstimate - tagOverlap),
            shape = RoundedCornerShape(
                topStart = 0.dp,
                topEnd = 12.dp,
                bottomStart = 12.dp,
                bottomEnd = 12.dp
            ),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(
                    top = tagOverlap + 16.dp,
                    bottom = 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChecklistItem(stringResource(R.string.service_complete_label_name), summary.clientName)
                ChecklistItem(
                    stringResource(R.string.service_complete_label_age),
                    summary.clientAge.toString(),
                )
                ChecklistItem(
                    stringResource(R.string.service_complete_label_id_number),
                    summary.clientIdNumber,
                )
                ChecklistItem(
                    stringResource(R.string.service_complete_label_address),
                    summary.clientAddress,
                )
                ChecklistItem(
                    stringResource(R.string.service_complete_label_content),
                    summary.serviceContent,
                )
                ChecklistItem(
                    stringResource(R.string.service_complete_label_duration),
                    summary.duration,
                )
            }
        }

        ServiceHoursTag(
            modifier = Modifier,
            tagText = stringResource(R.string.service_complete_checklist),
            tagCategory = TagCategory.DEFAULT
        )
    }
}
