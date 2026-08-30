package com.ytone.longcare.features.identification.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal const val SERVICE_PERSON_ACTION_TAG = "identification_service_action"
internal const val ELDER_ACTION_TAG = "identification_elder_action"
internal const val SERVICE_PERSON_RETRY_TAG = "identification_service_retry"
internal const val ELDER_RETRY_TAG = "identification_elder_retry"

@Composable
internal fun IdentificationCard(
    state: IdentificationCardRenderState,
    onEvent: (IdentificationScreenEvent) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IdentificationAvatar(personType = state.personType)
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                IdentificationCardStatusArea(state = state, onEvent = onEvent)
            }
        }
    }
}
