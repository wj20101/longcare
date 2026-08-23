package com.ytone.longcare.features.identification.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R

@Composable
internal fun VerifiedStatusRow(personType: IdentificationPersonType) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = stringResource(R.string.identification_verified_description),
            tint = Color(0xFF34C759)
        )
        Text(
            text = stringResource(
                R.string.identification_verified,
                stringResource(personType.labelRes),
            ),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF34C759)
        )
    }
}

@Composable
internal fun LoadingStatusRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF666666)
        )
    }
}

@Composable
internal fun RetryStatusColumn(
    statusText: String,
    statusColor: Color,
    buttonText: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = statusText,
            fontSize = 12.sp,
            color = statusColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        PrimaryActionButton(
            text = buttonText,
            textSize = 14.sp,
            onClick = onClick
        )
    }
}

@Composable
internal fun PrimaryActionButton(
    text: String,
    enabled: Boolean = true,
    textSize: TextUnit,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5A623)),
        enabled = enabled,
        modifier = Modifier.heightIn(min = 36.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = textSize
        )
    }
}
