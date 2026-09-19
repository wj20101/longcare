package com.ytone.longcare.platform.sales

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Keep a pending request until the source page is resumed and navigation is dispatched. */
@Composable
internal fun SalesEvaluationFormEffect(
    request: SalesEvaluationFormRequest?,
    onLeaveDevice: () -> Unit,
    onOpenForm: (String) -> Unit,
    onConsumed: (String) -> Unit,
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val leaveDevice by rememberUpdatedState(onLeaveDevice)
    val openForm by rememberUpdatedState(onOpenForm)
    val consumed by rememberUpdatedState(onConsumed)
    LaunchedEffect(request, lifecycleState) {
        if (request == null || request.consumed || lifecycleState != Lifecycle.State.RESUMED) return@LaunchedEffect
        leaveDevice()
        val url = request.url?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        openForm(url)
        consumed(request.recordId)
    }
}
