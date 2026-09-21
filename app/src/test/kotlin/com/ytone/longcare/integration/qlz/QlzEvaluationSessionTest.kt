package com.ytone.longcare.integration.qlz

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QlzEvaluationSessionTest {
    @Test
    fun `authorization rejects blank input active steps ordinary upload failure and terminal states`() {
        val driver = FakeQlzEvaluationDriver()
        val session = session(driver)
        try {
            assertFalse(session.authorize("  "))
            assertTrue(driver.tokens.isEmpty())
            assertTrue(session.authorize("first"))
            assertFalse(session.authorize("duplicate"))
            for (event in listOf(
                QlzEvaluationDriverEvent.Authorized,
                QlzEvaluationDriverEvent.DevicesChanged(listOf(DEVICE)),
                QlzEvaluationDriverEvent.CheckStarted,
                QlzEvaluationDriverEvent.MeasurementCompleted,
                QlzEvaluationDriverEvent.UploadFailed(503),
            )) {
                driver.emit(event)
                assertFalse(session.authorize("must-not-restart"))
                if (event is QlzEvaluationDriverEvent.DevicesChanged) session.selectDevice(DEVICE.id)
            }
            assertEquals(QlzEvaluationRecoveryAction.RETRY_UPLOAD, session.state.value.recoveryAction)
            session.retryUpload()
            driver.emit(QlzEvaluationDriverEvent.UploadSucceeded("record"))
            assertFalse(session.authorize("after-completion"))
            assertEquals(listOf("first"), driver.tokens)
            assertTrue("close" !in driver.cleanupOrder)
        } finally { session.close() }
        assertFalse(session.authorize("after-close"))
    }

    @Test
    fun `upload auth codes reauthorize same driver without scanning or destroying pending measurement`() {
        for (code in listOf(401, 2001, 100, 102)) {
            val driver = FakeQlzEvaluationDriver()
            val events = mutableListOf<QlzSdkEvent>()
            val session = session(driver, onEvent = events::add)
            try {
                advanceToMeasurement(session, driver)
                driver.emit(QlzEvaluationDriverEvent.MeasurementCompleted)
                assertFalse(session.authorize("must-not-interrupt-upload"))
                val cleanup = driver.cleanupOrder.toList()
                val stale = driver.callbacks.single()
                driver.emit(QlzEvaluationDriverEvent.UploadFailed(code))
                assertTrue(events.filterIsInstance<QlzSdkEvent.Error>().single().requiresTokenRefresh)
                assertTrue(session.authorize("fresh"))
                stale(QlzEvaluationDriverEvent.Authorized)
                stale(QlzEvaluationDriverEvent.UploadSucceeded("stale"))
                assertEquals(0, driver.uploadRetries)
                driver.emit(QlzEvaluationDriverEvent.Authorized)
                driver.emit(QlzEvaluationDriverEvent.Authorized)
                assertEquals(1, driver.uploadRetries)
                assertEquals(1, driver.uploads.size)
                assertEquals(1, driver.scanStarts)
                assertEquals(listOf("token", "fresh"), driver.tokens)
                assertEquals(cleanup, driver.cleanupOrder)
                driver.emit(QlzEvaluationDriverEvent.UploadSucceeded("record-1"))
                assertEquals("record-1", events.filterIsInstance<QlzSdkEvent.Completed>().single().recordId)
            } finally { session.close() }
        }
    }

    @Test
    fun `failed upload reauthorization retains driver with exit instead of measurement retry`() {
        val driver = FakeQlzEvaluationDriver()
        val session = session(driver)
        try {
            advanceToMeasurement(session, driver)
            driver.emit(QlzEvaluationDriverEvent.MeasurementCompleted)
            driver.emit(QlzEvaluationDriverEvent.UploadFailed(401))
            session.authorize("fresh")
            driver.emit(QlzEvaluationDriverEvent.Failed(1, QlzEvaluationIssue.NETWORK, QlzEvaluationRecoveryAction.RETRY_AUTHORIZATION))
            assertEquals(QlzEvaluationRecoveryAction.EXIT, session.state.value.recoveryAction)
            assertEquals(0, driver.uploadRetries)
            assertEquals(1, driver.uploads.size)
            assertTrue("close" !in driver.cleanupOrder)
            driver.emit(QlzEvaluationDriverEvent.Authorized)
            assertEquals(0, driver.uploadRetries)
        } finally { session.close() }
    }

    @Test
    fun `authorization starts one bounded scan and repeated selection connects once`() {
        val driver = FakeQlzEvaluationDriver()
        val session = session(driver)

        session.authorize("token")
        driver.emit(QlzEvaluationDriverEvent.Authorized)
        driver.emit(
            QlzEvaluationDriverEvent.DevicesChanged(
                listOf(DEVICE)
            )
        )
        session.selectDevice(DEVICE.id)
        session.selectDevice(DEVICE.id)

        assertEquals(1, driver.scanStarts)
        assertEquals(listOf(DEVICE.id), driver.connectedIds)
        assertEquals(QlzEvaluationStage.CONNECTING, session.state.value.stage)
    }

    @Test
    fun `measurement uploads once and retry resends only the in-memory upload`() {
        val driver = FakeQlzEvaluationDriver()
        val sdkEvents = mutableListOf<QlzSdkEvent>()
        val context =
            QlzEvaluationUploadContext(
                latitude = " 30.1 ",
                longitude = " 120.2 ",
                address = " 杭州 ",
            )
        val session = session(driver, context, sdkEvents::add)
        advanceToMeasurement(session, driver)

        driver.emit(QlzEvaluationDriverEvent.MeasurementCompleted)
        driver.emit(QlzEvaluationDriverEvent.MeasurementCompleted)

        assertEquals(
            listOf(
                QlzEvaluationUploadContext("30.1", "120.2", "杭州")
            ),
            driver.uploads,
        )
        driver.emit(
            QlzEvaluationDriverEvent.UploadFailed(
                com.evenmed.sdk.call.ErrorCodeConfig.error_check_up_neterror
            )
        )
        session.retryUpload()
        session.retryUpload()
        assertEquals(1, driver.uploadRetries)

        driver.emit(
            QlzEvaluationDriverEvent.UploadSucceeded(
                recordId = "record-1",
            )
        )
        val completed = sdkEvents.filterIsInstance<QlzSdkEvent.Completed>().single()
        assertEquals("record-1", completed.recordId)
        assertEquals(QlzEvaluationStage.COMPLETED, session.state.value.stage)
    }

    @Test
    fun `completed session ignores late token and progress business events`() {
        val driver = FakeQlzEvaluationDriver()
        val sdkEvents = mutableListOf<QlzSdkEvent>()
        val session = session(driver, onEvent = sdkEvents::add)
        advanceToMeasurement(session, driver)
        driver.emit(QlzEvaluationDriverEvent.MeasurementCompleted)
        driver.emit(
            QlzEvaluationDriverEvent.UploadSucceeded(
                recordId = "record-1",
            )
        )

        val completedState = session.state.value
        driver.emit(QlzEvaluationDriverEvent.TokenExpired())
        driver.emit(QlzEvaluationDriverEvent.ProgressChanged(9, 10))

        assertEquals(QlzEvaluationStage.COMPLETED, session.state.value.stage)
        assertEquals(1, sdkEvents.filterIsInstance<QlzSdkEvent.Completed>().size)
        assertTrue(sdkEvents.none { it is QlzSdkEvent.Error })
        assertEquals(completedState, session.state.value)
    }

    @Test
    fun `host stop prevents an authorization callback from starting background scan`() {
        val driver = FakeQlzEvaluationDriver()
        val session = session(driver)

        session.authorize("token")
        session.onHostStopped()
        driver.emit(QlzEvaluationDriverEvent.Authorized)

        assertEquals(0, driver.scanStarts)
        assertEquals(QlzEvaluationStage.READY_TO_SCAN, session.state.value.stage)

        session.onHostStarted()

        assertEquals(1, driver.scanStarts)
        assertEquals(QlzEvaluationStage.SCANNING, session.state.value.stage)
    }

    @Test
    fun `connection failure allows one reconnect while timeout remains recoverable`() {
        val driver = FakeQlzEvaluationDriver()
        val session = session(driver)
        session.authorize("token")
        driver.emit(QlzEvaluationDriverEvent.Authorized)
        driver.emit(QlzEvaluationDriverEvent.DevicesChanged(listOf(DEVICE)))
        session.selectDevice(DEVICE.id)
        driver.emit(
            QlzEvaluationDriverEvent.Failed(
                code = 12,
                issue = QlzEvaluationIssue.CONNECTION_FAILED,
                recoveryAction = QlzEvaluationRecoveryAction.RETRY_CONNECTION,
            )
        )

        session.retryConnection()
        session.retryConnection()

        assertEquals(1, driver.reconnects)
        assertEquals(QlzEvaluationStage.CONNECTING, session.state.value.stage)
        driver.emit(
            QlzEvaluationDriverEvent.Failed(
                code = 311,
                issue = QlzEvaluationIssue.CHECK_TIMEOUT,
                recoveryAction = QlzEvaluationRecoveryAction.RETRY_CONNECTION,
            )
        )
        assertEquals(QlzEvaluationIssue.CHECK_TIMEOUT, session.state.value.issue)
    }

    @Test
    fun `reauthorization invalidates late callbacks and close releases in safe idempotent order`() {
        val first = FakeQlzEvaluationDriver()
        val second = FakeQlzEvaluationDriver()
        val drivers = ArrayDeque(listOf(first, second))
        val cleanupOrder = mutableListOf<String>()
        val sdkEvents = mutableListOf<QlzSdkEvent>()
        val session =
            QlzEvaluationSession(
                driverFactory = QlzEvaluationDriverFactory { drivers.removeFirst() },
                uploadContext = QlzEvaluationUploadContext(),
                onEvent = sdkEvents::add,
                releaseLease = { cleanupOrder += "release" },
            )
        session.authorize("old-token")
        first.emit(QlzEvaluationDriverEvent.Failed(1, QlzEvaluationIssue.NETWORK, QlzEvaluationRecoveryAction.RETRY_AUTHORIZATION))
        first.onStop = { first.emit(QlzEvaluationDriverEvent.Authorized) }
        assertTrue(session.authorize("new-token"))

        assertEquals(0, first.scanStarts)
        first.emit(QlzEvaluationDriverEvent.Authorized)
        assertEquals(QlzEvaluationStage.AUTHORIZING, session.state.value.stage)
        second.emit(QlzEvaluationDriverEvent.Authorized)
        assertEquals(QlzEvaluationStage.SCANNING, session.state.value.stage)

        second.cleanupOrder = cleanupOrder
        second.onStop = {
            second.emit(
                QlzEvaluationDriverEvent.TokenExpired(
                    com.evenmed.sdk.call.ErrorCodeConfig.error_no_token
                )
            )
        }
        cleanupOrder.clear()
        session.close()
        session.close()
        second.emit(
            QlzEvaluationDriverEvent.DevicesChanged(listOf(DEVICE))
        )

        assertEquals(
            listOf("stop", "abort", "close", "release"),
            cleanupOrder,
        )
        assertEquals(QlzEvaluationStage.CLOSED, session.state.value.stage)
        assertTrue(session.state.value.devices.isEmpty())
        assertTrue(sdkEvents.isEmpty())
    }

    @Test
    fun `lease registry allows only one concurrent session`() {
        val registry = QlzSessionLeaseRegistry()
        val ready = CountDownLatch(12)
        val start = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<Long?>())
        val threads =
            List(12) {
                Thread {
                    ready.countDown()
                    start.await(2, TimeUnit.SECONDS)
                    results += registry.acquire()
                }.apply { start() }
            }
        assertTrue(ready.await(2, TimeUnit.SECONDS))
        start.countDown()
        threads.forEach(Thread::join)

        assertEquals(1, results.count { it != null })
        assertTrue(registry.hasActiveLease())
        val lease = results.single { it != null }!!
        registry.release(lease + 1)
        assertNull(registry.acquire())
        registry.release(lease)
        assertTrue(registry.acquire() != null)
    }

    private fun advanceToMeasurement(
        session: QlzEvaluationSession,
        driver: FakeQlzEvaluationDriver,
    ) {
        session.authorize("token")
        driver.emit(QlzEvaluationDriverEvent.Authorized)
        driver.emit(QlzEvaluationDriverEvent.DevicesChanged(listOf(DEVICE)))
        session.selectDevice(DEVICE.id)
        driver.emit(
            QlzEvaluationDriverEvent.Connected(
                deviceName = DEVICE.displayName,
                deviceId = DEVICE.id,
            )
        )
        driver.emit(QlzEvaluationDriverEvent.CheckStarted)
    }

    private fun session(
        driver: FakeQlzEvaluationDriver,
        uploadContext: QlzEvaluationUploadContext = QlzEvaluationUploadContext(),
        onEvent: (QlzSdkEvent) -> Unit = {},
    ) = QlzEvaluationSession(
        driverFactory = QlzEvaluationDriverFactory { driver },
        uploadContext = uploadContext,
        onEvent = onEvent,
        releaseLease = {},
    )

    private class FakeQlzEvaluationDriver : QlzEvaluationDriver {
        val tokens = mutableListOf<String>()
        val callbacks = mutableListOf<(QlzEvaluationDriverEvent) -> Unit>()
        private var listener: ((QlzEvaluationDriverEvent) -> Unit)? = null
        var scanStarts = 0
        val connectedIds = mutableListOf<String>()
        val uploads = mutableListOf<QlzEvaluationUploadContext>()
        var uploadRetries = 0
        var reconnects = 0
        var cleanupOrder: MutableList<String> = mutableListOf()
        var onStop: (() -> Unit)? = null

        override fun authorize(
            token: String,
            listener: (QlzEvaluationDriverEvent) -> Unit,
        ) {
            tokens += token
            callbacks += listener
            this.listener = listener
        }

        override fun startScan() {
            scanStarts += 1
        }

        override fun stopScan() {
            cleanupOrder += "stop"
            onStop?.invoke()
        }

        override fun connect(deviceId: String) {
            connectedIds += deviceId
        }

        override fun reconnect() {
            reconnects += 1
        }

        override fun upload(context: QlzEvaluationUploadContext) {
            uploads += context
        }

        override fun retryUpload() {
            uploadRetries += 1
        }

        override fun abortCheck() {
            cleanupOrder += "abort"
        }

        override fun close() {
            cleanupOrder += "close"
        }

        fun emit(event: QlzEvaluationDriverEvent) {
            listener?.invoke(event)
        }
    }

    private companion object {
        val DEVICE = QlzDeviceOption("qlz-device-1", "BM-S 100", "••:••:••:••:AA:BB")
    }
}
