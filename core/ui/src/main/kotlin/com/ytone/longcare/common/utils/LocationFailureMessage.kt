package com.ytone.longcare.common.utils

import androidx.annotation.StringRes
import com.ytone.longcare.core.ui.R
import com.ytone.longcare.domain.location.LocationFailure

@StringRes
fun LocationFailure.messageRes(): Int = when (this) {
    LocationFailure.PERMISSION -> R.string.location_error_permission
    LocationFailure.SERVICE_DISABLED -> R.string.location_error_disabled
    LocationFailure.BUSY -> R.string.location_error_busy
    LocationFailure.TIMEOUT -> R.string.location_error_timeout
    LocationFailure.QUALITY -> R.string.location_error_quality
    LocationFailure.NETWORK -> R.string.location_error_network
    LocationFailure.CONFIGURATION -> R.string.location_error_configuration
    LocationFailure.UNAVAILABLE -> R.string.location_error_unavailable
}
