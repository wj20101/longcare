package com.ytone.longcare.features.nursingexecution.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.ServiceProjectM
import com.ytone.longcare.model.UserInfoM

@Preview
@Composable
private fun LoadingScreenPreview() {
    LoadingScreen()
}

@Preview
@Composable
private fun ErrorScreenPreview() {
    ErrorScreen(
        message = "Error message",
        onRetry = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun NursingExecutionContentPreview() {
    val orderInfo = ServiceOrderInfoModel(
        orderId = 1L,
        state = 0,
        userInfo = UserInfoM(
            userId = 1,
            name = "John Doe",
            identityCardNumber = "123456789012345678",
            age = 80,
            gender = "Male",
            address = "123 Main St, Anytown, USA",
            lastServiceTime = "2023-10-26T10:00:00Z",
            monthServiceTime = 10,
            monthNoServiceTime = 5
        ),
        projectList = listOf(
            ServiceProjectM(
                projectId = 1,
                projectName = "Project A",
                serviceTime = 60,
                lastServiceTime = "2023-10-26T10:00:00Z"
            ),
            ServiceProjectM(
                projectId = 2,
                projectName = "Project B",
                serviceTime = 30,
                lastServiceTime = "2023-10-25T14:00:00Z"
            )
        )
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("NursingExecutionContent Preview")
    }
}

@Preview
@Composable
private fun ClientInfoCardPreview() {
    val orderInfo = ServiceOrderInfoModel(
        userInfo = UserInfoM(
            name = "Jane Doe",
            age = 75,
            identityCardNumber = "876543210987654321",
            address = "456 Oak St, Anytown, USA"
        ),
        projectList = listOf(
            ServiceProjectM(projectName = "Service X"),
            ServiceProjectM(projectName = "Service Y")
        )
    )
    ClientInfoCard(modifier = Modifier.padding(8.dp), orderInfo = orderInfo)
}

@Preview
@Composable
private fun InfoRowPreview() {
    InfoRow(label = "Label:", value = "This is the value for the label.")
}

@Preview
@Composable
private fun ConfirmButtonPreview() {
    ConfirmButton(
        text = "Confirm Action",
        onClick = {}
    )
}
