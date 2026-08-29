package com.ytone.longcare.startup

import android.content.Context
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.common.utils.DeviceRuntimeState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UserStorageNamespaceCutoverTest {
    private lateinit var context: Context
    private lateinit var protectedNamespaceFile: File
    private lateinit var protectedSessionFile: File
    private lateinit var rootSentinel: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        markerPreferences().edit().clear().commit()
        protectedNamespaceFile = File(
            context.filesDir,
            "user_scopes/v1/protected/persistent/orders/keep.txt",
        ).write("new-user-data")
        protectedSessionFile = File(
            context.cacheDir,
            "user_scopes/v1/protected/session/upload/keep.txt",
        ).write("new-session-data")
        rootSentinel = File(context.filesDir, "unrelated-root-sentinel.txt").write("keep")
    }

    @After
    fun tearDown() {
        markerPreferences().edit().clear().commit()
        protectedNamespaceFile.parentFile?.parentFile?.parentFile?.deleteRecursively()
        protectedSessionFile.parentFile?.parentFile?.parentFile?.deleteRecursively()
        rootSentinel.delete()
        removeSeededLegacyState()
    }

    @Test
    fun `explicit cold cutover removes all seeded legacy state without clearing app roots`() = runTest {
        val seededFiles = seedLegacyState()
        var platformCleanupCount = 0
        val cutover = cutover(platformCleanup = { platformCleanupCount += 1 })

        cutover.ensureCompleted()

        assertTrue(cutover.isCompleted)
        assertTrue(platformCleanupCount == 1)
        assertTrue(seededFiles.none(File::exists))
        UserStorageNamespaceCutover.LEGACY_SHARED_PREFERENCES.forEach { name ->
            assertFalse(File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").exists())
        }
        assertTrue(protectedNamespaceFile.exists())
        assertTrue(protectedSessionFile.exists())
        assertTrue(rootSentinel.exists())
        assertTrue(context.filesDir.exists())
        assertTrue(context.cacheDir.exists())
    }

    @Test
    fun `every interrupted step leaves marker absent and full retry is idempotent`() = runTest {
        LegacyCutoverStep.entries.forEach { interruptedStep ->
            markerPreferences().edit().clear().commit()
            seedLegacyState()
            var injected = false
            val interrupted = cutover(
                failureInjector = LegacyCutoverFailureInjector { completedStep ->
                    if (!injected && completedStep == interruptedStep) {
                        injected = true
                        error("interrupted after $completedStep")
                    }
                },
            )

            assertTrue(runCatching { interrupted.ensureCompleted() }.isFailure)
            assertFalse(interrupted.isCompleted)
            assertTrue(protectedNamespaceFile.exists())
            assertTrue(protectedSessionFile.exists())

            cutover().ensureCompleted()
            assertTrue(markerPreferences().getBoolean(DeviceRuntimeState.CUTOVER_MARKER_KEY, false))
            assertTrue(protectedNamespaceFile.exists())
            assertTrue(protectedSessionFile.exists())
        }
    }

    @Test
    fun `committed marker skips destructive work on normal future launches`() = runTest {
        markerPreferences().edit()
            .putBoolean(DeviceRuntimeState.CUTOVER_MARKER_KEY, true)
            .commit()
        val legacyNamedButPostCutover = File(context.cacheDir, "cos_temp/new-runtime.txt").write("keep")
        var platformCleanupCount = 0

        cutover(platformCleanup = { platformCleanupCount += 1 }).ensureCompleted()

        assertTrue(legacyNamedButPostCutover.exists())
        assertTrue(platformCleanupCount == 0)
        legacyNamedButPostCutover.parentFile?.deleteRecursively()
    }

    private fun cutover(
        platformCleanup: suspend () -> Unit = {},
        failureInjector: LegacyCutoverFailureInjector = LegacyCutoverFailureInjector { },
    ) = UserStorageNamespaceCutover(
        context = context,
        ioDispatcher = Dispatchers.IO,
        platformCleanup = platformCleanup,
        failureInjector = failureInjector,
    )

    private fun seedLegacyState(): List<File> {
        UserStorageNamespaceCutover.LEGACY_SHARED_PREFERENCES.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().putString("legacy", "secret-$name").commit()
        }
        val seeded = mutableListOf<File>()
        seeded += File(context.filesDir, "datastore/app_prefs.preferences_pb").write("app_user=secret")
        seeded += File(context.filesDir, "datastore/user_123_prefs.preferences_pb").write("bare-user")
        seeded += context.getDatabasePath(UserStorageNamespaceCutover.LEGACY_DATABASE_NAME).write("room")
        seeded += File(
            context.getDatabasePath(UserStorageNamespaceCutover.LEGACY_DATABASE_NAME).path + "-wal",
        ).write("wal")
        UserStorageNamespaceCutover.LEGACY_FILES_DIRECTORIES.forEach { name ->
            seeded += File(context.filesDir, "$name/legacy.txt").write("legacy")
        }
        UserStorageNamespaceCutover.LEGACY_CACHE_DIRECTORIES.forEach { name ->
            seeded += File(context.cacheDir, "$name/legacy.txt").write("legacy")
        }
        seeded += File(context.cacheDir, "temp_capture_123.jpg").write("capture")
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { pictures ->
            seeded += File(pictures, "captured_photos/legacy.jpg").write("legacy")
            seeded += File(pictures, "captured_image_123.jpg").write("legacy")
        }
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { downloads ->
            seeded += File(downloads, "longcare_legacy.apk").write("legacy")
        }
        return seeded
    }

    private fun removeSeededLegacyState() {
        UserStorageNamespaceCutover.LEGACY_SHARED_PREFERENCES.forEach(context::deleteSharedPreferences)
        File(context.filesDir, "datastore/app_prefs.preferences_pb").delete()
        File(context.filesDir, "datastore/user_123_prefs.preferences_pb").delete()
        context.deleteDatabase(UserStorageNamespaceCutover.LEGACY_DATABASE_NAME)
        UserStorageNamespaceCutover.LEGACY_FILES_DIRECTORIES.forEach { name ->
            File(context.filesDir, name).deleteRecursively()
        }
        UserStorageNamespaceCutover.LEGACY_CACHE_DIRECTORIES.forEach { name ->
            File(context.cacheDir, name).deleteRecursively()
        }
        File(context.cacheDir, "temp_capture_123.jpg").delete()
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { pictures ->
            File(pictures, "captured_photos").deleteRecursively()
            File(pictures, "captured_image_123.jpg").delete()
        }
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { downloads ->
            File(downloads, "longcare_legacy.apk").delete()
        }
    }

    private fun markerPreferences() = context.getSharedPreferences(
        DeviceRuntimeState.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private fun File.write(value: String): File = apply {
        parentFile?.mkdirs()
        writeText(value)
    }
}
