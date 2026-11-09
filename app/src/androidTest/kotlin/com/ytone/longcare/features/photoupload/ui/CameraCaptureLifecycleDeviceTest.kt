package com.ytone.longcare.features.photoupload.ui

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.view.allViews
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.decode.DataSource
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.result
import com.ytone.longcare.common.image.UnifiedImagePipeline
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.system.WatermarkConfigProvider
import com.ytone.longcare.feature.photoupload.R
import com.ytone.longcare.features.photoupload.api.CameraActions
import com.ytone.longcare.features.photoupload.vm.CameraViewModel
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.User
import com.ytone.longcare.model.UserSessionSnapshot
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.navigation.AppNavEntry
import com.ytone.longcare.navigation.AppNavigator
import com.ytone.longcare.navigation.CameraRoute
import com.ytone.longcare.navigation.HomeRoute
import com.ytone.longcare.navigation.NavigationResults
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraCaptureLifecycleDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private enum class AfterCapture { RESUME, CHANGE_ACCOUNT, EXIT, CAMERA_POPPED }

    @Test fun sameCameraReturnsItsPhotoOnceAfterPause() = verifyCapture(AfterCapture.RESUME)
    @Test fun changingAccountWhilePausedDeletesTheUndeliveredPhoto() = verifyCapture(AfterCapture.CHANGE_ACCOUNT)
    @Test fun exitingDuringEncodingDeletesTheUndeliveredPhoto() = verifyCapture(AfterCapture.EXIT)
    @Test fun backBeforeResultCallbackDeletesTheUnclaimedPhoto() = verifyCapture(AfterCapture.CAMERA_POPPED)

    @Before fun grantCameraPermissions() {
        val activity = compose.activity
        assumeTrue("Requires a back camera", activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (permission in listOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) {
            instrumentation.uiAutomation.grantRuntimePermission(activity.packageName, permission)
        }
    }

    private fun verifyCapture(action: AfterCapture) {
        val activity = compose.activity
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pictures = File(activity.cacheDir, "camera-test-" + UUID.randomUUID())
        val context = object : ContextWrapper(activity.applicationContext) {
            override fun getExternalFilesDir(type: String?): File = pictures
        }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val encoder = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                Dispatchers.IO.dispatch(context) {
                    entered.countDown()
                    check(release.await(20, TimeUnit.SECONDS))
                    try { block.run() } finally { finished.countDown() }
                }
            }
        }
        val session = CameraTestSession()
        val config = object : WatermarkConfigProvider { override suspend fun getSyLogoImg() = "" }
        val camera = CameraViewModel(config, TestLocation, UnifiedImagePipeline(context, encoder), session)
        val models = ViewModelStore().apply { put("camera", camera) }
        val cameraVisible = mutableStateOf(true)
        val caller = AppNavEntry(HomeRoute)
        val nav = AppNavigator(mutableListOf<NavKey>(caller), NavigationResults())
        val data = WatermarkData("服务前", "测试", "测试", "地址")
        nav.navigate(CameraRoute(data))
        val cameraPage = nav.forEntry((nav.backStack.last() as AppNavEntry).id)
        val key = NavigationConstants.CAPTURED_IMAGE_URI_KEY
        val received = nav.handle(caller).results.getStateFlow<String>(key, null)
        val callbacks = CopyOnWriteArrayList<File>()
        val existingInputs = activity.cacheDir.listFiles().orEmpty().toSet()
        try {
            compose.setContent {
                if (cameraVisible.value) CameraScreen(CameraActions { uri ->
                    callbacks.add(File(requireNotNull(Uri.parse(uri).path)))
                    cameraVisible.value = false
                    cameraPage.returnResult(key, uri)
                }, data, camera)
            }
            compose.waitUntil(20_000) {
                compose.runOnIdle {
                    activity.window.decorView.allViews.filterIsInstance<PreviewView>().any {
                        it.previewStreamState.value == PreviewView.StreamState.STREAMING
                    }
                }
            }
            compose.onNodeWithContentDescription(activity.getString(R.string.camera_shutter_description)).performClick()
            compose.waitUntil(15_000) { entered.count == 0L }
            val raw = activity.cacheDir.listFiles().orEmpty().single { it !in existingInputs && it.name.startsWith("temp_capture_") }
            assertTrue(raw.length() > 0)
            if (action == AfterCapture.EXIT) {
                compose.runOnIdle { cameraVisible.value = false }
                compose.waitForIdle()
            } else if (action != AfterCapture.CAMERA_POPPED) {
                compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                if (action == AfterCapture.CHANGE_ACCOUNT) session.sessionSnapshot.value = UserSessionSnapshot(2, User(userId = 8))
            }

            // Observe the real JPEG before the main-thread coroutine can return it or clean it up.
            lateinit var jpeg: File
            instrumentation.runOnMainSync {
                release.countDown()
                assertTrue(finished.await(5, TimeUnit.SECONDS))
                jpeg = pictures.walkTopDown().single { it.extension == "jpg" }
                assertTrue(jpeg.length() > 0)
                if (action == AfterCapture.CAMERA_POPPED) assertTrue(cameraPage.popBackStack())
            }
            compose.waitUntil(10_000) { !raw.exists() }
            assertNull(received.value)
            if (action == AfterCapture.RESUME) {
                assertTrue(jpeg.isFile)
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                compose.waitUntil(10_000) { received.value != null }
                compose.waitForIdle()
                assertEquals(Uri.fromFile(jpeg).toString(), received.value)
                assertEquals(listOf(jpeg), callbacks)
                assertTrue(jpeg.isFile)
            } else {
                compose.waitUntil(10_000) { !jpeg.exists() }
                if (action == AfterCapture.CHANGE_ACCOUNT) compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                compose.runOnIdle {
                    assertNull(received.value)
                    assertEquals(if (action == AfterCapture.CAMERA_POPPED) listOf(jpeg) else emptyList(), callbacks)
                }
            }
        } finally {
            release.countDown()
            instrumentation.runOnMainSync { models.clear() }
            pictures.deleteRecursively()
        }
    }

    @Test fun cached404LogoRetriesOnResume() = verifyLogoRecovery(firstStatus = 404)
    @Test fun cachedInvalidImageRetriesOnResume() = verifyLogoRecovery(firstStatus = 200)

    @OptIn(DelicateCoilApi::class)
    private fun verifyLogoRecovery(firstStatus: Int) {
        val activity = compose.activity
        val url = "https://watermark.invalid/${UUID.randomUUID()}.webp"
        val logoBytes = activity.resources.openRawResource(R.drawable.app_watermark_image).use { it.readBytes() }
        val available = AtomicBoolean(false)
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            check(request.url.toString() == url)
            requests.incrementAndGet()
            val ready = available.get()
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(if (ready) 200 else firstStatus)
                .message("Test logo")
                .body((if (ready) logoBytes else byteArrayOf(0)).toResponseBody("image/webp".toMediaType()))
                .build()
        }.build()
        val originalLoader = SingletonImageLoader.get(activity)
        val loader = originalLoader.newBuilder().components { add(OkHttpNetworkFetcherFactory(client)) }.build()
        val config = object : WatermarkConfigProvider {
            override suspend fun getSyLogoImg() = url
        }
        val fix = CompletableDeferred<LocationAcquisition>()
        val location = object : LocationFacade by TestLocation {
            override suspend fun acquireCurrentLocation() = fix.await()
        }
        val camera = CameraViewModel(config, location, UnifiedImagePipeline(activity.applicationContext, Dispatchers.IO), CameraTestSession())
        val models = ViewModelStore().apply { put("camera", camera) }
        fun logo() = activity.findViewById<ImageView>(R.id.logoImageView)
        try {
            SingletonImageLoader.setUnsafe(loader)
            compose.setContent {
                CameraScreen(CameraActions { error("No photo requested") }, WatermarkData("服务前", "", "", ""), camera)
            }
            compose.waitUntil(10_000) { compose.runOnIdle { logo()?.result is ErrorResult } }
            val failure = compose.runOnIdle { logo().result }
            assertEquals(1, requests.get())
            available.set(true)
            fix.complete(LocationAcquisition.Success(TestLocation.sample))
            compose.waitUntil(10_000) { compose.runOnIdle {
                activity.findViewById<TextView>(R.id.coordinatesTextView)?.text?.startsWith("120.3") == true
            } }
            compose.runOnIdle { assertSame(failure, logo().result) }
            assertEquals(1, requests.get())

            val scenario = compose.activityRule.scenario
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitUntil(10_000) { compose.runOnIdle { logo().result?.let { it !== failure } == true } }
            val success = compose.runOnIdle { logo().result }
            assertEquals(2, requests.get())
            assertTrue(success is SuccessResult)
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.runOnIdle { assertSame(success, logo().result) }
            val fromDisk = runBlocking {
                loader.execute(ImageRequest.Builder(activity).data(url).memoryCachePolicy(CachePolicy.DISABLED).build())
            }
            assertEquals(DataSource.DISK, (fromDisk as? SuccessResult)?.dataSource)
            assertEquals(2, requests.get())
        } finally {
            SingletonImageLoader.setUnsafe(originalLoader)
            loader.diskCache?.remove(url)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() }
        }
    }

    private object TestLocation : LocationFacade {
        val sample = LocationResult(30.2, 120.3, "test")
        override suspend fun acquireCurrentLocation() = LocationAcquisition.Success(sample)
        override fun isUsable(location: LocationResult) = location == sample
        override suspend fun acquireServiceOrderLocation() = error("No service-order lookup expected")
        override fun serviceOrderLocationFailure(location: LocationResult) = error("No service-order lookup expected")
    }

    private class CameraTestSession : UserSessionRepository {
        private val user = User(userId = 7)
        override val sessionSnapshot = MutableStateFlow(UserSessionSnapshot(1, user))
        override val sessionState = MutableStateFlow<SessionState>(SessionState.LoggedIn(user))
        override suspend fun logoutIfCurrent(generation: Long) = error("Unexpected logout")
        override suspend fun login(user: User) = error("Unexpected login")
        override suspend fun updateUser(user: User) = error("Unexpected account update")
        override suspend fun logout() = error("Unexpected logout")
    }
}
