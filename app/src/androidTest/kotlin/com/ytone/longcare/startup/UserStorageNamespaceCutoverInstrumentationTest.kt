package com.ytone.longcare.startup

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.DeviceRuntimeState
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.features.countdown.receiver.DismissAlarmReceiver
import com.ytone.longcare.features.service.ServiceTimeNotificationManager
import com.ytone.longcare.features.service.receiver.ServiceTimeAlarmReceiver
import com.ytone.longcare.presentation.countdown.CountdownAlarmActivity
import com.ytone.longcare.worker.UpdateWorker
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserStorageNamespaceCutoverInstrumentationTest {
    private lateinit var context: Context
    private lateinit var markerPreferences: android.content.SharedPreferences
    private lateinit var protectedNamespaceFile: File
    private lateinit var protectedDataStoreFile: File
    private lateinit var rootSentinel: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        markerPreferences = context.getSharedPreferences(
            DeviceRuntimeState.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        markerPreferences.edit().clear().commit()
        protectedNamespaceFile = File(
            context.filesDir,
            "user_scopes/v1/instrumentation-protected/persistent/orders/keep.txt",
        ).write("keep")
        protectedDataStoreFile = File(
            context.filesDir,
            "datastore/user_v1_instrumentation-protected.preferences_pb",
        ).write("keep")
        rootSentinel = File(context.filesDir, "cutover-root-sentinel.txt").write("keep")
    }

    @After
    fun tearDown() {
        clearLegacyFixtures()
        protectedNamespaceFile.parentFile?.parentFile?.parentFile?.deleteRecursively()
        protectedDataStoreFile.delete()
        rootSentinel.delete()
        markerPreferences.edit()
            .clear()
            .putBoolean(DeviceRuntimeState.CUTOVER_MARKER_KEY, true)
            .commit()
    }

    @Test
    fun seededUpgradeStateIsFullyRemovedAndColdStartDoesNotSelectAUser() = runBlocking {
        val legacyFiles = seedFullLegacyState()
        val cutover = cutover()

        cutover.ensureCompleted()

        assertTrue(cutover.isCompleted)
        assertTrue(legacyFiles.none(File::exists))
        assertFalse(PrivacyConsentManager(context).isPrivacyConsented)
        assertFalse(markerPreferences.contains(DeviceRuntimeState.APP_INSTANCE_GUID_KEY))
        assertTrue(protectedNamespaceFile.exists())
        assertTrue(protectedDataStoreFile.exists())
        assertTrue(rootSentinel.exists())
        assertFalse(context.databaseList().contains(UserStorageNamespaceCutover.LEGACY_DATABASE_NAME))
        assertTrue(
            context.databaseList().none { name ->
                name.startsWith("longcare_user_v1_instrumentation-protected")
            },
        )
    }

    @Test
    fun everyFilesystemInterruptionPointRetriesWithoutEarlyMarkerOrNewNamespaceDeletion() = runBlocking {
        LegacyCutoverStep.entries.forEach { interruptedStep ->
            markerPreferences.edit().clear().commit()
            val legacy = File(context.cacheDir, "cos_temp/interrupted.txt").write("legacy")
            var interrupted = false
            val failing = cutover(
                failureInjector = LegacyCutoverFailureInjector { completedStep ->
                    if (!interrupted && completedStep == interruptedStep) {
                        interrupted = true
                        error("injected process death after $completedStep")
                    }
                },
            )

            assertTrue(runCatching { failing.ensureCompleted() }.isFailure)
            assertFalse(failing.isCompleted)
            assertTrue(protectedNamespaceFile.exists())
            assertTrue(protectedDataStoreFile.exists())

            cutover().ensureCompleted()
            assertTrue(markerPreferences.getBoolean(DeviceRuntimeState.CUTOVER_MARKER_KEY, false))
            assertFalse(legacy.exists())
            assertTrue(protectedNamespaceFile.exists())
            assertTrue(protectedDataStoreFile.exists())
        }
    }

    @Test
    fun platformCleanupCancelsLegacyWorkAlarmPendingIntentsAndNotifications() = runBlocking {
        val workManager = WorkManager.getInstance(context)
        val oldWork = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        workManager.enqueue(oldWork).result.get(10, TimeUnit.SECONDS)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val serviceIntent = Intent(context, ServiceTimeAlarmReceiver::class.java).apply {
            action = ServiceTimeNotificationManager.ACTION_SERVICE_TIME_END_ALARM
        }
        val serviceAlarm = requireNotNull(
            PendingIntentCompat.getBroadcast(
                context,
                5001,
                serviceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT,
                false,
            ),
        )
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + TimeUnit.HOURS.toMillis(1),
            serviceAlarm,
        )
        PendingIntentCompat.getBroadcast(
            context,
            3002,
            Intent(context, DismissAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT,
            false,
        )
        PendingIntentCompat.getActivity(
            context,
            3003,
            Intent(context, CountdownAlarmActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT,
            false,
        )

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "legacy-cutover-instrumentation"
        notificationManager.createNotificationChannel(
            NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_LOW),
        )
        notificationManager.notify(
            LEGACY_NOTIFICATION_ID,
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.app_logo_round)
                .setContentTitle("legacy")
                .build(),
        )

        LegacyCutoverPlatformCleaner(
            context,
            Provider<WorkManager> { workManager },
        ).cleanup()

        assertEquals(
            WorkInfo.State.CANCELLED,
            workManager.getWorkInfoById(oldWork.id).get(10, TimeUnit.SECONDS)?.state,
        )
        assertNull(
            PendingIntentCompat.getBroadcast(
                context,
                5001,
                serviceIntent,
                PendingIntent.FLAG_NO_CREATE,
                false,
            ),
        )
        assertNull(
            PendingIntentCompat.getBroadcast(
                context,
                3002,
                Intent(context, DismissAlarmReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE,
                false,
            ),
        )
        assertNull(
            PendingIntentCompat.getActivity(
                context,
                3003,
                Intent(context, CountdownAlarmActivity::class.java),
                PendingIntent.FLAG_NO_CREATE,
                false,
            ),
        )
        assertTrue(notificationManager.activeNotifications.none { it.id == LEGACY_NOTIFICATION_ID })
        notificationManager.deleteNotificationChannel(channelId)
    }

    private fun cutover(
        failureInjector: LegacyCutoverFailureInjector = LegacyCutoverFailureInjector { },
    ) = UserStorageNamespaceCutover(
        context = context,
        ioDispatcher = Dispatchers.IO,
        platformCleanup = {},
        failureInjector = failureInjector,
    )

    private fun seedFullLegacyState(): List<File> {
        UserStorageNamespaceCutover.LEGACY_SHARED_PREFERENCES.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().putString("legacy", "secret").commit()
        }
        context.getSharedPreferences("privacy_consent", Context.MODE_PRIVATE)
            .edit().putBoolean("is_consented", true).commit()
        context.getSharedPreferences("device_instance_id_store", Context.MODE_PRIVATE)
            .edit().putString("generated_app_instance_id", "legacy-id").commit()

        val files = mutableListOf<File>()
        files += File(context.filesDir, "datastore/app_prefs.preferences_pb").write("app_user=legacy")
        files += File(context.filesDir, "datastore/user_987_prefs.preferences_pb").write("legacy")
        files += context.getDatabasePath(UserStorageNamespaceCutover.LEGACY_DATABASE_NAME).write("legacy")
        UserStorageNamespaceCutover.LEGACY_FILES_DIRECTORIES.forEach { name ->
            files += File(context.filesDir, "$name/legacy.txt").write("legacy")
        }
        UserStorageNamespaceCutover.LEGACY_CACHE_DIRECTORIES.forEach { name ->
            files += File(context.cacheDir, "$name/legacy.txt").write("legacy")
        }
        files += File(context.cacheDir, "temp_capture_999.jpg").write("legacy")
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { root ->
            files += File(root, "longcare_legacy.apk").write("legacy")
        }
        return files
    }

    private fun clearLegacyFixtures() {
        UserStorageNamespaceCutover.LEGACY_SHARED_PREFERENCES.forEach(context::deleteSharedPreferences)
        File(context.filesDir, "datastore/app_prefs.preferences_pb").delete()
        File(context.filesDir, "datastore/user_987_prefs.preferences_pb").delete()
        context.deleteDatabase(UserStorageNamespaceCutover.LEGACY_DATABASE_NAME)
        UserStorageNamespaceCutover.LEGACY_FILES_DIRECTORIES.forEach { name ->
            File(context.filesDir, name).deleteRecursively()
        }
        UserStorageNamespaceCutover.LEGACY_CACHE_DIRECTORIES.forEach { name ->
            File(context.cacheDir, name).deleteRecursively()
        }
        File(context.cacheDir, "temp_capture_999.jpg").delete()
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { root ->
            File(root, "longcare_legacy.apk").delete()
        }
    }

    private fun File.write(value: String): File = apply {
        parentFile?.mkdirs()
        writeText(value)
    }

    private companion object {
        const val LEGACY_NOTIFICATION_ID = 61_234
    }
}
