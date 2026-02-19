package com.ytone.longcare.features.nfctest.ui

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable
internal fun BindNfcTestLifecycle(
    enabled: Boolean,
    context: Context,
    lifecycleOwner: LifecycleOwner,
    onEnable: (Activity) -> Unit,
    onDisable: (Activity) -> Unit
) {
    if (!enabled) return

    LaunchedEffect(context) {
        val activity = context as? Activity
        activity?.let(onEnable)
    }

    DisposableEffect(context) {
        onDispose {
            val activity = context as? Activity
            activity?.let(onDisable)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val activity = context as? Activity
            activity?.let(onEnable)
        }
    }
}
