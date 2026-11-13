package com.ytone.longcare.features.nfc

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Parcel
import androidx.activity.compose.setContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import androidx.lifecycle.Lifecycle
import com.ytone.longcare.MainActivity
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.utils.NfcManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.onSubscription
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Activity / singleTop delivery with synthetic tags and a private bus; no business API. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 35)
class NfcIntentLifecycleDeviceTest {
    @Test fun nfcLaunchWithoutAnOrderIsConsumedAndNeverReplayedAfterRecreation() = verifyLifecycle(coldLaunch = true)
    @Test fun foregroundTagIsConsumedAcrossRecreationAndSameCardCanBeScannedAgain() = verifyLifecycle(coldLaunch = false)
    @Test fun freshBackgroundIntentWaitsForTheSameOrderPageToReturn() = verifyLifecycle(coldLaunch = false, background = true)

    private fun verifyLifecycle(coldLaunch: Boolean, background: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as Application
        val bus = AppEventBus()
        val tags = CopyOnWriteArrayList<AppEvent.TagScanned>()
        val current = AtomicReference<MainActivity>()
        val recreated = CountDownLatch(2)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val subscribed = CompletableDeferred<Unit>()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.onSubscription { subscribed.complete(Unit) }.collect {
                if (it is AppEvent.TagScanned) tags.add(it)
            }
        }
        runBlocking { withTimeout(5_000) { subscribed.await() } }
        val observer = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPostCreated(activity: Activity, state: Bundle?) {
                if (activity is MainActivity) {
                    // Dispose the ordinary MainApp before attachment. Events cannot reach an order.
                    activity.setContent { }
                    activity.nfcManager = NfcManager(bus).also {
                        it.enableNfcForActivity(activity, "page-${recreated.count}")
                    }
                    current.set(activity)
                    recreated.countDown()
                }
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (activity is MainActivity) current.compareAndSet(activity, null)
            }
        }
        fun newTagIntent() = Intent(context, MainActivity::class.java).setAction(NfcAdapter.ACTION_TECH_DISCOVERED)
            .putExtra(NfcAdapter.EXTRA_TAG, mockTag()).putExtra(NfcAdapter.EXTRA_ID, byteArrayOf(1, 10))
        fun scanAgain() = instrumentation.runOnMainSync {
            requireNotNull(current.get()).startActivity(newTagIntent().addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        fun awaitTags(count: Int) = runBlocking {
            withTimeout(5_000) { while (tags.size < count) delay(25) }
        }
        app.registerActivityLifecycleCallbacks(observer)
        try {
            val launch = if (coldLaunch) newTagIntent() else Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
            instrumentation.startActivitySync(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            if (!coldLaunch) {
                if (background) {
                    instrumentation.runOnMainSync { requireNotNull(current.get()).moveTaskToBack(true) }
                    runBlocking { withTimeout(5_000) {
                        while (requireNotNull(current.get()).lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) delay(25)
                    } }
                    context.startActivity(newTagIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                } else scanAgain()
                awaitTags(1)
            }
            instrumentation.waitForIdleSync()
            assertEquals(if (coldLaunch) 0 else 1, tags.size)
            instrumentation.runOnMainSync {
                val activity = requireNotNull(current.get())
                assertEquals(Intent.ACTION_MAIN, activity.intent.action)
                assertFalse(activity.intent.hasExtra(NfcAdapter.EXTRA_TAG))
                assertFalse(activity.intent.hasExtra(NfcAdapter.EXTRA_ID))
                activity.recreate()
            }
            assertTrue("Framework must recreate the Activity", recreated.await(10, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            Thread.sleep(150)
            assertEquals("Recreation must never replay an old card", if (coldLaunch) 0 else 1, tags.size)
            scanAgain()
            awaitTags(if (coldLaunch) 1 else 2)
            assertTrue(tags.all { it.tagId == "010A" })
            if (!coldLaunch) assertNotEquals(tags[0].scanSessionId, tags[1].scanSessionId)
        } finally {
            instrumentation.runOnMainSync { current.get()?.finish() }
            instrumentation.waitForIdleSync()
            app.unregisterActivityLifecycleCallbacks(observer)
            scope.cancel()
        }
    }

    private fun mockTag(): Tag {
        // Android SDK Tag.java public Parcelable format; no hidden API or NFC service needed.
        val parcel = Parcel.obtain()
        return try {
            parcel.writeInt(2)
            parcel.writeByteArray(byteArrayOf(1, 10))
            parcel.writeInt(0)
            parcel.writeIntArray(intArrayOf())
            parcel.writeTypedArray(emptyArray<Bundle>(), 0)
            parcel.writeInt(0)
            parcel.writeLong(0)
            parcel.writeInt(1)
            parcel.setDataPosition(0)
            Tag.CREATOR.createFromParcel(parcel)
        } finally { parcel.recycle() }
    }
}
