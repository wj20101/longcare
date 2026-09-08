package com.ytone.longcare.domain.order

import com.ytone.longcare.model.ServiceOrderStateModel
import kotlinx.coroutines.flow.StateFlow

/** Process-local business session. Observing a screen never owns the monitoring lifetime. */
interface ServiceOrderLifecycle {
    val orderState: StateFlow<ServiceOrderStateModel?>

    /** Start/reuse state synchronization; this alone does not request location permission/tracking. */
    fun monitorOrder(orderId: Long)

    /** The formal start-order API succeeded; NFC identification alone must never call this. */
    fun onOrderStarted(orderId: Long)

    /** Called only after the end-order API succeeds, before UI/resource cleanup. */
    fun onOrderEnded(orderId: Long)
}
