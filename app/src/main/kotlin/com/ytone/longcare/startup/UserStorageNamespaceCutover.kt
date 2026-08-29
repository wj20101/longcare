package com.ytone.longcare.startup

import android.annotation.SuppressLint
import android.os.Environment
import com.ytone.longcare.common.utils.DeviceRuntimeState
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.domain.startup.UserStorageNamespaceCutoverGate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class LegacyCutoverStep {
    CANCEL_PLATFORM_STATE,
    DELETE_LEGACY_PREFERENCES,
    DELETE_GLOBAL_DATABASE,
    DELETE_LEGACY_DATASTORES,
    DELETE_SHARED_FILES,
    DELETE_SHARED_CACHES,
    DELETE_LEGACY_DOWNLOADS,
    BEFORE_MARKER_COMMIT,
}

internal fun interface LegacyCutoverFailureInjector {
    fun after(step: LegacyCutoverStep)
}

private val NoCutoverFailure = LegacyCutoverFailureInjector { }

@Singleton
class UserStorageNamespaceCutover internal constructor(
    private val context: android.content.Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val platformCleanup: suspend () -> Unit,
    private val failureInjector: LegacyCutoverFailureInjector,
) : UserStorageNamespaceCutoverGate {
    @Inject
    constructor(
        @ApplicationContext context: android.content.Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        platformCleaner: LegacyCutoverPlatformCleaner,
    ) : this(
        context = context,
        ioDispatcher = ioDispatcher,
        platformCleanup = platformCleaner::cleanup,
        failureInjector = NoCutoverFailure,
    )

    private val mutex = Mutex()
    private val markerPreferences by lazy {
        context.getSharedPreferences(DeviceRuntimeState.PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
    }

    override val isCompleted: Boolean
        get() = runCatching {
            markerPreferences.getBoolean(DeviceRuntimeState.CUTOVER_MARKER_KEY, false)
        }.getOrDefault(false)

    override suspend fun ensureCompleted() {
        if (isCompleted) return
        mutex.withLock {
            if (isCompleted) return
            withContext(ioDispatcher) {
                platformCleanup()
                checkpoint(LegacyCutoverStep.CANCEL_PLATFORM_STATE)

                deleteLegacyPreferences()
                checkpoint(LegacyCutoverStep.DELETE_LEGACY_PREFERENCES)

                deleteLegacyDatabase()
                checkpoint(LegacyCutoverStep.DELETE_GLOBAL_DATABASE)

                deleteLegacyDataStores()
                checkpoint(LegacyCutoverStep.DELETE_LEGACY_DATASTORES)

                deleteLegacySharedFiles()
                checkpoint(LegacyCutoverStep.DELETE_SHARED_FILES)

                deleteLegacyCaches()
                checkpoint(LegacyCutoverStep.DELETE_SHARED_CACHES)

                deleteLegacyDownloads()
                checkpoint(LegacyCutoverStep.DELETE_LEGACY_DOWNLOADS)
                checkpoint(LegacyCutoverStep.BEFORE_MARKER_COMMIT)

                check(commitCutoverMarker()) {
                    "Unable to commit user-storage namespace cutover marker"
                }
            }
        }
    }

    /** Raw commit is intentional: the cutover marker must verify its synchronous disk result. */
    @SuppressLint("UseKtx")
    private fun commitCutoverMarker(): Boolean =
        markerPreferences.edit()
            .putBoolean(DeviceRuntimeState.CUTOVER_MARKER_KEY, true)
            .commit()

    private fun checkpoint(step: LegacyCutoverStep) {
        failureInjector.after(step)
    }

    private fun deleteLegacyPreferences() {
        LEGACY_SHARED_PREFERENCES.forEach { name ->
            context.deleteSharedPreferences(name)
            val sharedPreferencesRoot = File(context.applicationInfo.dataDir, "shared_prefs")
            deleteFile(File(sharedPreferencesRoot, "$name.xml"))
            deleteFile(File(sharedPreferencesRoot, "$name.xml.bak"))
        }
    }

    private fun deleteLegacyDatabase() {
        context.deleteDatabase(LEGACY_DATABASE_NAME)
        val database = context.getDatabasePath(LEGACY_DATABASE_NAME)
        database.parentFile?.listFiles { file ->
            file.name == LEGACY_DATABASE_NAME || file.name.startsWith("$LEGACY_DATABASE_NAME-")
        }.orEmpty().forEach(::deleteFile)
    }

    private fun deleteLegacyDataStores() {
        val dataStoreRoot = File(context.filesDir, "datastore")
        dataStoreRoot.listFiles { file ->
            file.isFile && (
                file.name == LEGACY_APP_DATASTORE_FILE ||
                    file.name.startsWith("$LEGACY_APP_DATASTORE_FILE.") ||
                    LEGACY_BARE_USER_DATASTORE.matches(file.name)
                )
        }.orEmpty().forEach(::deleteFile)
    }

    private fun deleteLegacySharedFiles() {
        LEGACY_FILES_DIRECTORIES.forEach { name ->
            deleteDirectoryUnder(context.filesDir, File(context.filesDir, name))
        }
        deleteMatchingFiles(context.filesDir, LEGACY_ROOT_CAPTURE_FILE)

        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { picturesRoot ->
            deleteDirectoryUnder(picturesRoot, File(picturesRoot, "captured_photos"))
            deleteMatchingFiles(picturesRoot, LEGACY_PICTURE_FILE)
        }
    }

    private fun deleteLegacyCaches() {
        LEGACY_CACHE_DIRECTORIES.forEach { name ->
            deleteDirectoryUnder(context.cacheDir, File(context.cacheDir, name))
        }
        deleteMatchingFiles(context.cacheDir, LEGACY_TEMP_CAPTURE_FILE)
    }

    private fun deleteLegacyDownloads() {
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { downloadsRoot ->
            deleteMatchingFiles(downloadsRoot, LEGACY_APK_DOWNLOAD)
        }
    }

    private fun deleteMatchingFiles(root: File, pattern: Regex) {
        root.listFiles { file -> file.isFile && pattern.matches(file.name) }
            .orEmpty()
            .forEach(::deleteFile)
    }

    private fun deleteDirectoryUnder(root: File, target: File) {
        val canonicalRoot = root.canonicalFile
        val canonicalTarget = target.canonicalFile
        check(canonicalTarget != canonicalRoot && canonicalTarget.isInside(canonicalRoot)) {
            "Refusing broad legacy deletion: ${canonicalTarget.path}"
        }
        if (canonicalTarget.exists()) {
            check(canonicalTarget.deleteRecursively()) {
                "Unable to delete legacy directory: ${canonicalTarget.path}"
            }
        }
    }

    private fun deleteFile(file: File) {
        if (file.exists()) {
            check(file.isFile && file.delete()) { "Unable to delete legacy file: ${file.path}" }
        }
    }

    private fun File.isInside(root: File): Boolean =
        path.startsWith(root.path + File.separator)

    internal companion object {
        const val LEGACY_DATABASE_NAME = "longcare_database"
        const val LEGACY_APP_DATASTORE_FILE = "app_prefs.preferences_pb"

        val LEGACY_SHARED_PREFERENCES = linkedSetOf(
            "app_prefs",
            "privacy_consent",
            "device_instance_id_store",
            "login_preferences",
            "device_compatibility_prefs",
            "system_config_prefs",
            "pending_orders_storage",
            "service_time_notification_prefs",
            "countdown_notification_prefs",
            "app_update_prefs",
            "update_prefs",
        )
        val LEGACY_FILES_DIRECTORIES = listOf(
            "face_store",
            "face_capture",
            "face_captures",
            "captured_photos",
            "logs",
        )
        val LEGACY_CACHE_DIRECTORIES = listOf(
            "cos_temp",
            "face_temp",
            "http-cache",
            "image_cache",
            "apk_install",
        )
        val LEGACY_BARE_USER_DATASTORE = Regex("user_[0-9]+_prefs\\.preferences_pb(?:\\..+)?")
        val LEGACY_TEMP_CAPTURE_FILE = Regex("temp_capture_[0-9]+\\.jpg")
        val LEGACY_ROOT_CAPTURE_FILE = Regex("captured_image_.*\\.jpg", RegexOption.IGNORE_CASE)
        val LEGACY_PICTURE_FILE = Regex("captured_image_.*\\.jpg", RegexOption.IGNORE_CASE)
        val LEGACY_APK_DOWNLOAD = Regex("longcare_.*\\.apk", RegexOption.IGNORE_CASE)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UserStorageNamespaceCutoverModule {
    @Binds
    @Singleton
    abstract fun bindUserStorageNamespaceCutoverGate(
        implementation: UserStorageNamespaceCutover,
    ): UserStorageNamespaceCutoverGate
}
