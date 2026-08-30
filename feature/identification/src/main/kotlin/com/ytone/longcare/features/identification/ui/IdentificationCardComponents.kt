package com.ytone.longcare.features.identification.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ytone.longcare.feature.identification.R

@Composable
internal fun IdentificationAvatar(personType: IdentificationPersonType) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = Color.LightGray.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = personType.avatarRes),
            contentDescription = stringResource(personType.avatarDescriptionRes),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@get:StringRes
internal val IdentificationPersonType.labelRes: Int
    get() = when (this) {
        IdentificationPersonType.SERVICE_PERSON -> R.string.identification_service_person
        IdentificationPersonType.ELDER -> R.string.identification_elder
    }

@get:DrawableRes
private val IdentificationPersonType.avatarRes: Int
    get() = when (this) {
        IdentificationPersonType.SERVICE_PERSON -> R.drawable.ic_service_person
        IdentificationPersonType.ELDER -> R.drawable.ic_elder_person
    }

@get:StringRes
private val IdentificationPersonType.avatarDescriptionRes: Int
    get() = when (this) {
        IdentificationPersonType.SERVICE_PERSON -> R.string.identification_service_person_avatar
        IdentificationPersonType.ELDER -> R.string.identification_elder_avatar
    }
