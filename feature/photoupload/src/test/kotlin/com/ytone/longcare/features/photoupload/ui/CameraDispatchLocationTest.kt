package com.ytone.longcare.features.photoupload.ui

import android.app.Application
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.widget.ImageView
import androidx.camera.view.LifecycleCameraController
import androidx.lifecycle.ViewModelStore
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.system.WatermarkConfigProvider
import com.ytone.longcare.features.photoupload.vm.CameraViewModel
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.User
import com.ytone.longcare.model.UserSessionSnapshot
import com.ytone.longcare.model.WatermarkData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class CameraDispatchLocationTest {
    private val main = StandardTestDispatcher()
    private val models = ViewModelStore()
    private val context get() = RuntimeEnvironment.getApplication()
    private val session = MutableStateFlow(UserSessionSnapshot(1, User(userId = 3)))
    private val locations = mockk<LocationFacade>()
    private val camera = mockk<LifecycleCameraController>()
    private lateinit var viewModel: CameraViewModel

    @Before fun setUp() {
        Dispatchers.setMain(main)
        val sessions = mockk<UserSessionRepository> { every { sessionSnapshot } returns session }
        val branding = mockk<WatermarkConfigProvider> { coEvery { getSyLogoImg() } returns "" }
        viewModel = CameraViewModel(branding, locations, mockk(), sessions)
        models.put("camera", viewModel)
    }

    @After fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test fun fixExpiringDuringLogoPreparationNeverReachesCameraX() = runTest(main) {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(20))
        val start = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        val sample = LocationResult(30.2, 120.3, "amap", accuracy = 5f, coordType = "GCJ02",
            locationType = 1, trustedLevel = 1, locationTime = wall, receivedAt = wall,
            receivedElapsedRealtime = start, sourceElapsedRealtime = start)
        every { locations.isUsable(sample) } answers {
            LocationQuality.rejection(sample, System.currentTimeMillis(), SystemClock.elapsedRealtime()) == null
        }
        coEvery { locations.acquireCurrentLocation() } returnsMany listOf(
            LocationAcquisition.Success(sample), LocationAcquisition.Failure(LocationFailure.UNAVAILABLE),
        )
        viewModel.onCameraResume(true)
        runCurrent()
        val logo = ImageView(context).apply {
            setImageDrawable(object : ColorDrawable(Color.BLUE) {
                override fun draw(canvas: Canvas) {
                    ShadowSystemClock.advanceBy(Duration.ofMillis(4))
                    super.draw(canvas)
                }
            })
            layout(0, 0, 80, 30)
        }
        ShadowSystemClock.advanceBy(Duration.ofMillis(start + 14_999L - SystemClock.elapsedRealtime()))
        assertTrue(locations.isUsable(sample))
        var failure: LocationFailure? = null
        takePhoto(
            context = context, watermarkData = WatermarkData("服务前", "", "", ""), previewLogo = logo,
            isFrontCamera = false, lifecycle = mockk(),
            captureImage = { file, prepare ->
                camera.saveLocatedImage(context, file, viewModel, prepare, { error("Unexpected shutter") }) { failure = it }
            },
            processCapturedImage = { error("No file expected") },
            deliver = { error("No result expected") },
        )
        assertEquals(15_003L, SystemClock.elapsedRealtime() - start)
        assertEquals(LocationFailure.UNAVAILABLE, failure)
        coVerify(exactly = 2) { locations.acquireCurrentLocation() }
        verify(exactly = 0) { camera.takePicture(any(), any(), any()) }
    }

    @Test fun accountChangeDuringFinalLocationCheckBlocksCameraX() = runTest(main) {
        val sample = LocationResult(30.2, 120.3, "amap")
        coEvery { locations.acquireCurrentLocation() } returns LocationAcquisition.Success(sample)
        var checks = 0
        every { locations.isUsable(sample) } answers {
            if (++checks == 2) session.value = UserSessionSnapshot(2, User(userId = 4))
            true
        }
        viewModel.onCameraResume(true)
        runCurrent()
        val file = File.createTempFile("account-change", ".jpg", context.cacheDir)
        try {
            val result = runCatching {
                camera.saveLocatedImage(context, file, viewModel, { true }, { error("Old session dispatched") }) {
                    error("Unexpected location failure")
                }
            }
            assertEquals(2, checks)
            assertTrue(result.exceptionOrNull() is CancellationException)
            verify(exactly = 0) { camera.takePicture(any(), any(), any()) }
        } finally {
            file.delete()
        }
    }
}
