package com.ytone.longcare.features.nfc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R

@Composable
internal fun SignInContentCard(
    signInState: SignInState,
    statusOverrideRes: Int? = null,
    failureStatusText: String? = null,
    showReadingIndicator: Boolean = false,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (signInState) {
                SignInState.SUCCESS -> StatusDisplay(
                    icon = Icons.Default.CheckCircle,
                    text = stringResource(R.string.nfc_sign_in_status_success),
                    iconColor = Color(0xFF34C759)
                )

                SignInState.FAILURE -> StatusDisplay(
                    icon = Icons.Default.Error,
                    text = failureStatusText ?: stringResource(R.string.nfc_sign_in_status_failure),
                    iconColor = MaterialTheme.colorScheme.error,
                    textColor = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                )

                SignInState.IDLE -> {
                    if (statusOverrideRes != null) {
                        Row(
                            modifier = Modifier
                                .height(48.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showReadingIndicator) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Text(
                                text = stringResource(statusOverrideRes),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Image(
                painter = painterResource(id = R.drawable.nfc_interaction_diagram),
                contentDescription = stringResource(R.string.nfc_sign_in_diagram_description),
                modifier = Modifier
                    .padding(start = 48.dp)
                    .size(170.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
internal fun StatusDisplay(
    icon: ImageVector,
    text: String,
    iconColor: Color,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 2,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f, fill = false),
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ActionButton(text: String, onClick: () -> Unit) {
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF5A9BFA), Color(0xFF3A86FF))
    )
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(brush = buttonGradient, shape = CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
