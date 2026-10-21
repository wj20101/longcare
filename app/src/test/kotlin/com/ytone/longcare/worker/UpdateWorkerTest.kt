package com.ytone.longcare.worker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.network.ApiRequestException
import com.ytone.longcare.common.utils.DeviceUtils
import com.ytone.longcare.domain.system.SystemRepository
import com.ytone.longcare.model.AppVersionModel
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UpdateWorkerTest {
    private val repository = mockk<SystemRepository>()
    private val deviceUtils = mockk<DeviceUtils>()

    @Before
    fun setUp() {
        mockkObject(DiagnosticEventTracker)
        every { DiagnosticEventTracker.currentUserId() } returns "42"
        every { DiagnosticEventTracker.trackError(any(), any(), any(), any(), any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkObject(DiagnosticEventTracker)
    }

    @Test
    fun `invalid response fails without retry and records the first attempt`() = runTest {
        val error = requestError(ApiRequestException.Kind.INVALID_RESPONSE)
        coEvery { repository.checkVersion() } returns ApiResult.Exception(error)

        assertEquals(ListenableWorker.Result.failure(), worker().doWork())

        verify {
            DiagnosticEventTracker.trackError(
                category = DiagnosticCategory.APP_UPDATE,
                event = "version_check_exception",
                description = "版本检查接口异常",
                throwable = error,
                extras = mapOf(
                    "attempt" to 1,
                    "errorKind" to "INVALID_RESPONSE",
                    "httpCode" to null,
                    "willRetry" to false,
                ),
                userId = "42",
            )
        }
    }

    @Test
    fun `connection and timeout failures retry at most twice`() = runTest {
        for (kind in listOf(ApiRequestException.Kind.CONNECTION, ApiRequestException.Kind.TIMEOUT)) {
            coEvery { repository.checkVersion() } returns ApiResult.Exception(requestError(kind))

            assertEquals(ListenableWorker.Result.retry(), worker(runAttemptCount = 0).doWork())
            assertEquals(ListenableWorker.Result.retry(), worker(runAttemptCount = 1).doWork())
            assertEquals(ListenableWorker.Result.failure(), worker(runAttemptCount = 2).doWork())
        }
    }

    @Test
    fun `only explicitly transient http failures retry`() = runTest {
        for (code in listOf(408, 429, 500, 502, 503, 504)) {
            coEvery { repository.checkVersion() } returns ApiResult.Exception(
                requestError(ApiRequestException.Kind.HTTP, code),
            )
            assertEquals("HTTP $code", ListenableWorker.Result.retry(), worker().doWork())
        }
        for (code in listOf(null, 400, 401, 403, 404, 409, 422, 501, 505)) {
            coEvery { repository.checkVersion() } returns ApiResult.Exception(
                requestError(ApiRequestException.Kind.HTTP, code),
            )
            assertEquals("HTTP $code", ListenableWorker.Result.failure(), worker().doWork())
        }
    }

    @Test
    fun `final transient attempt records failure without another retry`() = runTest {
        val error = requestError(ApiRequestException.Kind.HTTP, 503)
        coEvery { repository.checkVersion() } returns ApiResult.Exception(error)

        assertEquals(ListenableWorker.Result.failure(), worker(runAttemptCount = 2).doWork())

        verify {
            DiagnosticEventTracker.trackError(
                category = DiagnosticCategory.APP_UPDATE,
                event = "version_check_exception",
                description = "版本检查接口异常",
                throwable = error,
                extras = mapOf(
                    "attempt" to 3,
                    "errorKind" to "HTTP",
                    "httpCode" to 503,
                    "willRetry" to false,
                ),
                userId = "42",
            )
        }
    }

    @Test
    fun `unknown exceptions do not retry regardless of their message`() = runTest {
        for (error in listOf(
            requestError(ApiRequestException.Kind.UNKNOWN),
            IllegalStateException("API request timed out"),
        )) {
            coEvery { repository.checkVersion() } returns ApiResult.Exception(error)
            assertEquals(ListenableWorker.Result.failure(), worker().doWork())
        }
    }

    @Test
    fun `available update still returns its persisted output`() = runTest {
        val update = AppVersionModel(versionCode = 76, platform = "android", versionName = "1.0.8")
        coEvery { repository.checkVersion() } returns ApiResult.Success(update)
        every { deviceUtils.getAppVersionCode() } returns 75L

        assertEquals(ListenableWorker.Result.success(StartupUpdateWork.output(update)), worker().doWork())
        verify(exactly = 0) { DiagnosticEventTracker.trackError(any(), any(), any(), any(), any(), any()) }
    }

    private fun requestError(kind: ApiRequestException.Kind, code: Int? = null): ApiRequestException =
        mockk<ApiRequestException>().also {
            every { it.kind } returns kind
            every { it.httpCode } returns code
        }

    private fun worker(runAttemptCount: Int = 0): UpdateWorker =
        TestListenableWorkerBuilder<UpdateWorker>(
            context = ApplicationProvider.getApplicationContext(),
            runAttemptCount = runAttemptCount,
        ).setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = UpdateWorker(appContext, workerParameters, deviceUtils, repository)
        }).build()
}
