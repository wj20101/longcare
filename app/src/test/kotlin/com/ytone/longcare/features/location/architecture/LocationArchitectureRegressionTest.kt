package com.ytone.longcare.features.location.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source guards for the process-bound, non-persistent location reporting contract. */
class LocationArchitectureRegressionTest {
    private val repositoryRoot = File("src/main/kotlin").canonicalFile
        .resolve("../../../..")
        .canonicalFile

    @Test
    fun `location reporting has no Room outbox or WorkManager recovery path`() {
        val source = mainKotlinSources().joinToString("\n") { it.readText() }
        listOf(
            "location_upload_outbox",
            "OrderLocationDao",
            "LocationUploadQueueRepository",
            "LocationUploadWorker",
            "WorkManagerLocationUploadScheduler",
        ).forEach { forbidden ->
            assertFalse("Persistent location recovery path remains: $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `only current order reporting manager calls location API`() {
        val callers = mainKotlinSources()
            .filter { it.readText().contains("locationRepository.addPosition(") }
            .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
            .toList()

        assertEquals(
            listOf(
                "feature/location/src/main/kotlin/com/ytone/longcare/features/location/reporting/LocationReportingManager.kt",
            ),
            callers,
        )
    }

    @Test
    fun `continuous locations keep only latest pending sample and do not replay`() {
        val sampleStore = source(
            "feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationSampleStore.kt",
        )

        assertTrue(sampleStore.contains("replay = 0"))
        assertTrue(sampleStore.contains("extraBufferCapacity = LATEST_SAMPLE_BUFFER_SIZE"))
        assertTrue(sampleStore.contains("BufferOverflow.DROP_OLDEST"))
    }

    @Test
    fun `foreground service owns collection and is not restarted after process death`() {
        val callers = mainKotlinSources()
            .filter { it.readText().contains("continuousAmapLocationManager.startContinuousLocation(") }
            .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
            .toList()
        val service = source(
            "feature/location/src/main/kotlin/com/ytone/longcare/features/location/service/LocationTrackingService.kt",
        )

        assertEquals(
            listOf(
                "feature/location/src/main/kotlin/com/ytone/longcare/features/location/service/LocationTrackingService.kt",
            ),
            callers,
        )
        assertTrue(service.contains("return START_NOT_STICKY"))
        val manifest = File(
            repositoryRoot,
            "feature/location/src/main/AndroidManifest.xml",
        ).readText()
        assertTrue(manifest.contains("android:stopWithTask=\"true\""))
    }

    @Test
    fun `logout observer stops but never restores location`() {
        val manager = source(
            "feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationTrackingManager.kt",
        )

        assertTrue(manager.contains("SessionState.LoggedOut"))
        assertTrue(manager.contains("locationReportingManager.stopReporting()"))
        assertTrue(manager.contains("if (!hasActiveSession()) return"))
    }

    @Test
    fun `Room schema contains no location upload entity`() {
        val database = source(
            "core/data/src/main/kotlin/com/ytone/longcare/data/database/LongCareDatabase.kt",
        )
        val entities = source(
            "core/data/src/main/kotlin/com/ytone/longcare/data/database/entity/RoomEntities.kt",
        )

        assertFalse(database.contains("OrderLocationEntity"))
        assertFalse(entities.contains("order_locations"))
        assertFalse(entities.contains("location_upload_outbox"))
    }

    private fun source(relativePath: String): String = File(repositoryRoot, relativePath).readText()

    private fun mainKotlinSources(): Sequence<File> =
        listOf("app", "core", "feature")
            .asSequence()
            .map { File(repositoryRoot, it) }
            .filter { it.exists() }
            .flatMap { root -> root.walkTopDown().asSequence() }
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    file.invariantSeparatorsPath.contains("/src/main/") &&
                    !file.invariantSeparatorsPath.contains("/build/")
            }
}
