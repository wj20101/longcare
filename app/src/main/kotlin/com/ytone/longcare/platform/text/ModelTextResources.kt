package com.ytone.longcare.platform.text

import androidx.annotation.StringRes
import com.ytone.longcare.R
import com.ytone.longcare.model.OrderStateDisplay
import com.ytone.longcare.model.UserIdentityDisplay

@StringRes
fun OrderStateDisplay.labelRes(): Int = when (this) {
    OrderStateDisplay.NOT_CREATED -> R.string.order_state_not_created
    OrderStateDisplay.PENDING -> R.string.order_state_pending
    OrderStateDisplay.IN_PROGRESS -> R.string.order_state_in_progress
    OrderStateDisplay.COMPLETED -> R.string.order_state_completed
    OrderStateDisplay.CANCELLED -> R.string.order_state_cancelled
    OrderStateDisplay.UNKNOWN -> R.string.order_state_unknown
}

@StringRes
fun UserIdentityDisplay.labelRes(): Int = when (this) {
    UserIdentityDisplay.CAREGIVER -> R.string.user_identity_caregiver
    UserIdentityDisplay.SALES -> R.string.user_identity_sales
    UserIdentityDisplay.OTHER -> R.string.user_identity_other
}
