package com.ytone.longcare.features.photoupload.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.features.photoupload.viewmodel.PhotoProcessingViewModel

@Composable
internal fun PhotoUploadMockDebugToolsCard(
    viewModel: PhotoProcessingViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE4F3)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "🧪 Mock 调试工具",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFFD81B60),
            )

            Button(
                onClick = { viewModel.mockAddAllPhotos() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("一键添加所有照片")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.mockAddBeforeCarePhoto() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("护理前", fontSize = 12.sp)
                }
                Button(
                    onClick = { viewModel.mockAddCenterCarePhoto() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("护理中", fontSize = 12.sp)
                }
                Button(
                    onClick = { viewModel.mockAddAfterCarePhoto() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("护理后", fontSize = 12.sp)
                }
            }
        }
    }
}
