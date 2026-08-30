package com.ytone.longcare.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.ytone.longcare.common.utils.DeviceUtils
import com.ytone.longcare.domain.system.SystemRepository
import com.ytone.longcare.model.AppVersionModel
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val deviceUtils = mockk<DeviceUtils>()
    private val systemRepository = mockk<SystemRepository>()

    @Test
    fun `current server version succeeds with empty output`() =
        runTest {
            val currentVersionCode = 42L
            every { deviceUtils.getAppVersionCode() } returns currentVersionCode
            coEvery { systemRepository.checkVersion() } returns
                ApiResult.Success(
                    testVersion(
                        versionCode = currentVersionCode.toInt(),
                        downUrl = "",
                    ),
                )

            val result = worker().doWork()

            assertThat(result).isEqualTo(ListenableWorker.Result.success())
            assertThat(StartupUpdateWork.read(result.outputData)).isNull()
            coVerify(exactly = 1) { systemRepository.checkVersion() }
        }

    @Test
    fun `higher server version persists prompt data without creating download work`() =
        runTest {
            val update =
                testVersion(
                    versionCode = 43,
                    downUrl = "https://updates.mock.invalid/longcare-43.apk",
                )
            every { deviceUtils.getAppVersionCode() } returns 42L
            coEvery { systemRepository.checkVersion() } returns ApiResult.Success(update)

            val result = worker().doWork()

            assertThat(result).isEqualTo(
                ListenableWorker.Result.success(StartupUpdateWork.output(update)),
            )
            assertThat(StartupUpdateWork.read(result.outputData)).isEqualTo(update)
            assertThat(update.downUrl).contains(".mock.invalid/")
            coVerify(exactly = 1) { systemRepository.checkVersion() }
        }

    private fun worker(): UpdateWorker {
        val workerFactory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? =
                    if (workerClassName == UpdateWorker::class.java.name) {
                        UpdateWorker(
                            appContext = appContext,
                            workerParams = workerParameters,
                            deviceUtils = deviceUtils,
                            systemRepository = systemRepository,
                        )
                    } else {
                        null
                    }
            }
        return TestListenableWorkerBuilder<UpdateWorker>(
            context = context,
            runAttemptCount = 0,
        ).setWorkerFactory(workerFactory).build()
    }

    private fun testVersion(versionCode: Int, downUrl: String) =
        AppVersionModel(
            versionCode = versionCode,
            versionName = "test-$versionCode",
            upType = 1,
            remarks = "Test-owned update prompt",
            platform = "android",
            downUrl = downUrl,
        )
}
