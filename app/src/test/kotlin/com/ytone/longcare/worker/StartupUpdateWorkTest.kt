package com.ytone.longcare.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.ytone.longcare.common.utils.DeviceUtils
import com.ytone.longcare.model.AppVersionModel
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartupUpdateWorkTest {
    @Test
    fun `output round trips update model`() {
        val update = AppVersionModel(
            versionCode = 42,
            versionName = "4.2.0",
            upType = 2,
            remarks = "Important fixes",
            platform = "android",
            downUrl = "https://example.test/app.apk",
        )

        assertEquals(update, StartupUpdateWork.read(StartupUpdateWork.output(update)))
    }

    @Test
    fun `empty output means no available update`() {
        assertNull(StartupUpdateWork.read(androidx.work.Data.EMPTY))
    }

    @Test
    fun `startup check replaces previous generation and observes only its request`() {
        val workManager = mockk<WorkManager>(relaxed = true)
        val observer =
            StartupUpdateWorkObserver(
                workManager = workManager,
                deviceUtils = mockk<DeviceUtils>(relaxed = true),
            )

        observer.enqueueLatestCheck()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                StartupUpdateWork.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>(),
            )
        }
    }
}
