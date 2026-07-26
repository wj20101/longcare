package com.ytone.longcare.integration.qlz

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.WeakHashMap

/**
 * Host-side edge-to-edge compatibility for the vendor SDK activities.
 *
 * The QLZ SDK 1.3.0.2 activities target a classic decor layout and do not
 * consume system-bar insets. Android 15+ enforces edge-to-edge for modern
 * target SDK levels, and Android 16+ no longer supports the theme opt-out.
 * Keep this workaround scoped to vendor activities until the SDK handles
 * WindowInsets itself.
 */
object QlzSdkWindowInsetsCompat {
    private val originalPaddingByView = WeakHashMap<View, Padding>()

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    applyToVendorActivity(activity)
                }

                override fun onActivityResumed(activity: Activity) {
                    applyToVendorActivity(activity)
                }

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    private fun applyToVendorActivity(activity: Activity) {
        val activityName = activity.javaClass.name
        if (activityName !in VENDOR_ACTIVITY_NAMES) return

        val content = activity.findViewById<View>(android.R.id.content) ?: return
        WindowCompat.enableEdgeToEdge(activity.window)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }
        if (shouldProtectTopInset(activityName)) {
            // The SDK title bar is white. Extend that surface behind the
            // transparent status bar so dark system icons remain legible.
            content.setBackgroundColor(Color.WHITE)
            WindowCompat.getInsetsController(activity.window, content).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
        val originalPadding =
            originalPaddingByView.getOrPut(content) {
                Padding(
                    left = content.paddingLeft,
                    top = content.paddingTop,
                    right = content.paddingRight,
                    bottom = content.paddingBottom,
                )
            }

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, windowInsets ->
            val systemBarInsets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutoutInsets =
                windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val safeInsets =
                maxInsets(
                    systemBarInsets,
                    cutoutInsets,
                )
            val protectTop = shouldProtectTopInset(activityName)
            view.setPadding(
                originalPadding.left + safeInsets.left,
                originalPadding.top + if (protectTop) safeInsets.top else 0,
                originalPadding.right + safeInsets.right,
                originalPadding.bottom + safeInsets.bottom,
            )
            // The native parent has handled these edges. Keep any unhandled top
            // inset for immersive detection activities, while zeroing handled
            // values before they reach the SDK WebView to avoid double padding.
            WindowInsetsCompat.Builder(windowInsets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars(),
                    remainingInsets(systemBarInsets, protectTop),
                )
                .setInsets(
                    WindowInsetsCompat.Type.displayCutout(),
                    remainingInsets(cutoutInsets, protectTop),
                )
                .build()
        }
        ViewCompat.requestApplyInsets(content)
    }

    private fun remainingInsets(
        insets: Insets,
        protectTop: Boolean,
    ): Insets =
        Insets.of(
            0,
            if (protectTop) 0 else insets.top,
            0,
            0,
        )

    private fun maxInsets(
        first: Insets,
        second: Insets,
    ): Insets =
        Insets.of(
            maxOf(first.left, second.left),
            maxOf(first.top, second.top),
            maxOf(first.right, second.right),
            maxOf(first.bottom, second.bottom),
        )

    private data class Padding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private val VENDOR_ACTIVITY_NAMES =
        setOf(
            "com.comm.androidview.BaseAct",
            "com.comm.androidview.BaseAct2",
            "com.evenmed.sdk.call.CheckActBase",
            "com.evenmed.sdk.call.MainLoadingActivity",
            "com.evenmed.sdk.ResultViewActivity",
        )

    internal fun shouldProtectTopInset(activityName: String): Boolean =
        activityName in TOP_INSET_ACTIVITY_NAMES

    private val TOP_INSET_ACTIVITY_NAMES =
        setOf(
            "com.evenmed.sdk.ResultViewActivity",
        )
}
