package com.ytone.longcare.features.servicecomplete.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun ThankYouCardPreview() {
    ThankYouCard()
}

@Preview
@Composable
fun ServiceChecklistSectionPreview() {
    val summary = ServiceSummary(
        clientName = "John Doe",
        clientAge = 75,
        clientIdNumber = "123456789012345678",
        clientAddress = "123 Main St, Anytown, USA",
        serviceContent = "Bathing Assistance",
        duration = "2 hours 30 minutes"
    )
    ServiceChecklistSection(summary = summary)
}

@Preview
@Composable
fun ChecklistItemPreview() {
    ChecklistItem(label = "Name:", value = "John Doe")
}

@Preview
@Composable
fun ActionButtonPreview() {
    ActionButton(text = "Submit", onClick = {})
}
