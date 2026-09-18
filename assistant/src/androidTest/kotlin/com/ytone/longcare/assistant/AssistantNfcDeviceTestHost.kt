package com.ytone.longcare.assistant

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Before

/** Observes real Activity instances without relying on ActivityScenario's synthetic lifecycle invoker. */
abstract class AssistantNfcDeviceTestHost {
    private val currentActivity = AtomicReference<AssistantActivity?>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application get() = instrumentation.targetContext.applicationContext as Application
    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) {
            if (activity is AssistantActivity) currentActivity.set(activity)
        }
        override fun onActivityDestroyed(activity: Activity) {
            if (activity is AssistantActivity) currentActivity.compareAndSet(activity, null)
        }
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    }

    @Before fun launchHost() {
        application.registerActivityLifecycleCallbacks(callbacks)
        instrumentation.targetContext.startActivity(
            Intent(instrumentation.targetContext, AssistantActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        waitForHost("AssistantActivity creation") { currentActivity.get() != null }
    }

    @After fun closeHost() {
        try {
            currentActivity.get()?.let { activity -> activity.runOnUiThread { activity.finishAndRemoveTask() } }
            waitForHost("AssistantActivity destruction") { currentActivity.get() == null }
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }

    protected fun recreateHost() {
        val previous = checkNotNull(currentActivity.get())
        previous.runOnUiThread { previous.recreate() }
        waitForHost("replacement AssistantActivity") {
            currentActivity.get()?.let { it !== previous } == true
        }
    }

    protected fun deliverNewIntent(intent: Intent) {
        val activity = checkNotNull(currentActivity.get())
        instrumentation.runOnMainSync { instrumentation.callActivityOnNewIntent(activity, intent) }
    }

    private fun waitForHost(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for $description")
    }
}
