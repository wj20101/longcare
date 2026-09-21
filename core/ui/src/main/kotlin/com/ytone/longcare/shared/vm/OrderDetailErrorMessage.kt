package com.ytone.longcare.shared.vm

import androidx.annotation.StringRes
import com.ytone.longcare.common.network.ApiRequestException
import com.ytone.longcare.core.ui.R

/** Only request categories are user-facing; exception messages are diagnostic data. */
@StringRes
internal fun Throwable.orderDetailErrorMessage(): Int =
    when ((this as? ApiRequestException)?.kind) {
        ApiRequestException.Kind.CONNECTION -> R.string.order_detail_connection_failed
        ApiRequestException.Kind.TIMEOUT -> R.string.order_detail_timeout
        ApiRequestException.Kind.HTTP ->
            if (httpCode in 500..599) {
                R.string.order_detail_server_unavailable
            } else {
                R.string.order_detail_request_failed
            }
        ApiRequestException.Kind.INVALID_RESPONSE -> R.string.order_detail_invalid_response
        ApiRequestException.Kind.UNKNOWN, null -> R.string.order_detail_load_failed
    }
