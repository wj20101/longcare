package com.ytone.longcare.features.location.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 防止恢复服务时重新引入坐标补传队列或后台调度框架。 */
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
    fun `only foreground location service calls location API`() {
        val callers = mainKotlinSources()
            .filter { it.readText().contains("locationRepository.addPosition(") }
            .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath }
            .toList()

        assertEquals(
            listOf(
                "feature/location/src/main/kotlin/com/ytone/longcare/features/location/service/LocationTrackingService.kt",
            ),
            callers,
        )
        val service = source(
            "feature/location/src/main/kotlin/com/ytone/longcare/features/location/service/LocationTrackingService.kt",
        )
        assertFalse(service.contains("orderRepository"))
        assertTrue(service.contains("reportingManager.sessionFor(owner, orderId)"))
        assertTrue(service.contains("const val EXTRA_ORDER_ID"))
    }

    @Test
    fun `upload executor never queries order state or controls UI`() {
        val executor = source(
            "feature/location/src/main/kotlin/com/ytone/longcare/features/location/service/ServiceLocationSession.kt",
        )
        assertFalse(executor.contains("getOrderState"))
        assertFalse(executor.contains("Toast."))
    }

    @Test
    fun `foreground service owns collection and validates sticky restoration`() {
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
        assertTrue(service.contains("return START_STICKY"))
        assertTrue(service.contains("restoreFromStore()"))
        assertTrue(service.contains("session.awaitReady()"))
        val manifest = File(
            repositoryRoot,
            "feature/location/src/main/AndroidManifest.xml",
        ).readText()
        assertTrue(manifest.contains("android:stopWithTask=\"false\""))
        assertTrue(service.contains("override fun onTaskRemoved"))
    }

    @Test
    fun `logout observer stops but never restores location`() {
        val manager = source(
            "feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationTrackingManager.kt",
        )

        assertTrue(manager.contains("SessionState.LoggedOut"))
        assertTrue(manager.contains("locationReportingManager.setAccount(null)"))
    }

    @Test
    fun `NFC success never starts reporting before formal service start`() {
        val handler = source(
            "app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreenHandlers.kt",
        )
        val startSuccessBranch = handler.substringAfter("internal fun handleNfcSuccessAction")
            .substringAfter("SignInMode.START_ORDER ->")
            .substringBefore("SignInMode.END_ORDER ->")

        assertFalse(startSuccessBranch.contains("startTracking"))
        assertTrue(startSuccessBranch.contains("onNavigateToIdentification(orderKey)"))
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
