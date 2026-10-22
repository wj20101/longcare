package com.ytone.longcare.features.maindashboard.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.R
import com.ytone.longcare.domain.home.HomeDeviceRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.home.vm.HomeDeviceViewModel
import com.ytone.longcare.model.User
import com.ytone.longcare.model.ZhiChiDeviceStatusModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.NavigationStateTestActivity
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.theme.bgGradientBrush
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Isolated device status data: no real account, device binding or H5 request is changed. */
class HomeDeviceStatusCardTest {
    @get:Rule val compose = createAndroidComposeRule<NavigationStateTestActivity>()
    private val opened = mutableListOf<Pair<String, String>>()
    private val installationTitle get() = compose.activity.getString(R.string.home_device_installation)
    private val previewTitle get() = compose.activity.getString(R.string.home_device_preview)
    private val status = ZhiChiDeviceStatusModel(
        deviceSn = "TEST-DEVICE", status = "在线 · 网络正常", statusColor = "#27AE60",
        installUrl = "https://device.invalid/install", previewUrl = "https://device.invalid/preview",
    )

    private var expectedSurfaceColor = 0

    private fun showCard(value: ZhiChiDeviceStatusModel, fontScale: Float = 1f, darkTheme: Boolean = false, width: Dp = 328.dp) {
        compose.setContent {
            LongCareTheme(darkTheme = darkTheme) {
                expectedSurfaceColor = MaterialTheme.colorScheme.surface.toArgb()
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Column(Modifier.width(width).background(bgGradientBrush).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeDeviceStatusCard(value) { url, title -> opened += url to title }
                    }
                }
            }
        }
    }

    @Test fun boundDeviceKeepsServerTextAndOpensTheTwoServerUrls() {
        showCard(status)
        compose.onNodeWithText(status.status).assertIsDisplayed()
        compose.onNodeWithTag(HomeDeviceTestTags.INSTALLATION).performClick()
        compose.onNodeWithTag(HomeDeviceTestTags.PREVIEW).performClick()
        assertEquals(listOf(status.installUrl to installationTitle, status.previewUrl to previewTitle), opened)
        captureCard("home-device-bound.png")
    }

    @Test fun unboundDeviceHidesPreviewEvenWhenTheServerReturnsAPreviewUrl() {
        showCard(status.copy(deviceSn = "", status = "尚未绑定", statusColor = "#E53935"))
        compose.onNodeWithText("尚未绑定").assertIsDisplayed()
        compose.onNodeWithTag(HomeDeviceTestTags.PREVIEW).assertDoesNotExist()
        compose.onNodeWithTag(HomeDeviceTestTags.INSTALLATION).performClick()
        assertEquals(listOf(status.installUrl to installationTitle), opened)
        captureCard("home-device-unbound.png")
    }

    @Test fun unavailableOrUnsupportedUrlsCannotBeOpened() {
        showCard(status.copy(installUrl = "", previewUrl = "javascript:alert(1)", statusColor = "bad"))
        compose.onNodeWithTag(HomeDeviceTestTags.INSTALLATION).assertIsNotEnabled()
        compose.onNodeWithTag(HomeDeviceTestTags.PREVIEW).assertIsNotEnabled()
        compose.onNodeWithText(status.status).assertIsDisplayed()
        assertEquals(emptyList<Pair<String, String>>(), opened)
    }

    @Test fun cleartextHttpUrlsRemainDisabledToMatchTheAppNetworkPolicy() {
        showCard(status.copy(installUrl = "http://zcclient.ytone.cn/zhichi/install", previewUrl = "http://device.invalid/preview"))
        compose.onNodeWithTag(HomeDeviceTestTags.INSTALLATION).assertIsNotEnabled()
        compose.onNodeWithTag(HomeDeviceTestTags.PREVIEW).assertIsNotEnabled()
        assertEquals(emptyList<Pair<String, String>>(), opened)
    }

    @Test fun largeTextKeepsStatusAndBothActionsVisible() {
        showCard(status, fontScale = 1.6f)
        compose.onNodeWithText(status.status).assertIsDisplayed()
        compose.onNodeWithText(installationTitle).assertIsDisplayed()
        compose.onNodeWithText(previewTitle).assertIsDisplayed()
        captureCard("home-device-large-text.png")
    }

    @Test fun darkThemeKeepsTheCardSurfacePairedWithItsForeground() {
        showCard(status, darkTheme = true)
        compose.onNodeWithText(status.status).assertIsDisplayed()
        compose.onNodeWithText(installationTitle).assertIsDisplayed()
        compose.onNodeWithText(previewTitle).assertIsDisplayed()
        val bitmap = compose.onNodeWithTag(HomeDeviceTestTags.CARD).captureToImage().asAndroidBitmap()
        assertEquals(expectedSurfaceColor, bitmap.getPixel(bitmap.width / 2, 1))
        captureCard("home-device-dark-theme.png")
    }

    @Test fun normalTextKeepsTheDesignOnOneRowWithSeparateAccessibleButtons() {
        showCard(status.copy(status = "在线"))
        val name = compose.onNodeWithText(compose.activity.getString(R.string.home_device_name)).fetchSemanticsNode().boundsInRoot
        val state = compose.onNodeWithTag(HomeDeviceTestTags.STATUS).fetchSemanticsNode().boundsInRoot
        val install = compose.onNodeWithTag(HomeDeviceTestTags.INSTALLATION).fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithTag(HomeDeviceTestTags.PREVIEW).fetchSemanticsNode().boundsInRoot
        assertEquals(name.center.y, state.center.y, 1f)
        assertEquals(state.center.y, install.center.y, 1f)
        assertEquals(install.center.y, preview.center.y, 1f)
        assertTrue(install.right <= preview.left)
        val minimumTarget = 48 * compose.activity.resources.displayMetrics.density
        assertTrue("Installation bounds $install must reserve at least $minimumTarget px per side",
            install.height >= minimumTarget && install.width >= minimumTarget)
        assertTrue("Preview bounds $preview must reserve at least $minimumTarget px per side",
            preview.height >= minimumTarget && preview.width >= minimumTarget)
        captureCard("home-device-new-design.png")
    }

    @Test fun narrowWidthKeepsLongServerStatusAndBothButtonsVisible() {
        showCard(status, fontScale = 1.6f, width = 240.dp)
        compose.onNodeWithText(status.status).assertIsDisplayed()
        compose.onNodeWithTag(HomeDeviceTestTags.INSTALLATION).assertIsDisplayed()
        compose.onNodeWithTag(HomeDeviceTestTags.PREVIEW).assertIsDisplayed()
        val install = compose.onNodeWithTag(HomeDeviceTestTags.INSTALLATION).fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithTag(HomeDeviceTestTags.PREVIEW).fetchSemanticsNode().boundsInRoot
        assertTrue(!install.overlaps(preview))
        captureCard("home-device-narrow-large-text.png")
    }

    @Test fun returningToHomeKeepsCardAndLayoutVisibleDuringASlowRefresh() {
        val calls = AtomicInteger()
        val resumedResponse = CompletableDeferred<Unit>()
        val session = object : UserSessionRepository {
            override val sessionState = MutableStateFlow<SessionState>(SessionState.LoggedIn(User(userIdentity = 1)))
            // This UI/probe fake never authenticates real network requests.
            override val sessionSnapshot = kotlinx.coroutines.flow.MutableStateFlow(com.ytone.longcare.model.UserSessionSnapshot(0L, null))
            override suspend fun logoutIfCurrent(generation: Long): Long? = null
            override suspend fun login(user: User) { sessionState.value = SessionState.LoggedIn(user) }
            override suspend fun updateUser(user: User) = login(user)
            override suspend fun logout() { sessionState.value = SessionState.LoggedOut }
        }
        val repository = object : HomeDeviceRepository {
            override suspend fun getDeviceStatus(): ApiResult<ZhiChiDeviceStatusModel> {
                if (calls.incrementAndGet() == 2) resumedResponse.await()
                return ApiResult.Success(status)
            }
        }
        val viewModel = HomeDeviceViewModel(repository, session)
        val selected = mutableStateOf(true)
        lateinit var owner: LifecycleOwner
        lateinit var testLifecycle: LifecycleRegistry
        compose.runOnUiThread {
            compose.activity.viewModelStore.put("homeDeviceTest", viewModel)
            owner = object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this).also { testLifecycle = it }
            }
            testLifecycle.currentState = Lifecycle.State.RESUMED
        }
        compose.setContent {
            LongCareTheme {
                HomeDevicePollingEffect(viewModel, selected.value, owner)
                val currentStatus by viewModel.deviceStatus.collectAsStateWithLifecycle()
                Column {
                    currentStatus?.let { HomeDeviceStatusCard(it) { _, _ -> } }
                    Text("下方订单", Modifier.testTag("content_below_device"))
                }
            }
        }
        compose.waitUntil(5_000) { calls.get() == 1 }
        compose.onNodeWithTag(HomeDeviceTestTags.CARD).assertIsDisplayed()
        val belowCardBounds = compose.onNodeWithTag("content_below_device").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { selected.value = false }
        compose.onNodeWithTag(HomeDeviceTestTags.CARD).assertIsDisplayed()
        Thread.sleep(5_200)
        assertEquals(1, calls.get())
        compose.runOnIdle { selected.value = true }
        compose.waitUntil(5_000) { calls.get() == 2 }
        // The request stays suspended: this catches a disappearing card even on a fast network.
        compose.onNodeWithTag(HomeDeviceTestTags.CARD).assertIsDisplayed()
        assertEquals(belowCardBounds, compose.onNodeWithTag("content_below_device").fetchSemanticsNode().boundsInRoot)
        resumedResponse.complete(Unit)
        compose.runOnUiThread { testLifecycle.currentState = Lifecycle.State.STARTED }
        compose.onNodeWithTag(HomeDeviceTestTags.CARD).assertIsDisplayed()
        Thread.sleep(5_200)
        assertEquals(2, calls.get())
        compose.runOnUiThread { testLifecycle.currentState = Lifecycle.State.RESUMED }
        compose.waitUntil(5_000) { calls.get() == 3 }
        compose.runOnUiThread { testLifecycle.currentState = Lifecycle.State.DESTROYED }
        assertEquals(status, viewModel.deviceStatus.value)
        session.sessionState.value = SessionState.LoggedOut
        compose.waitUntil(5_000) { viewModel.deviceStatus.value == null }
        compose.onNodeWithTag(HomeDeviceTestTags.CARD).assertDoesNotExist()
        assertNull(viewModel.deviceStatus.value)
    }

    private fun captureCard(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = requireNotNull(compose.activity.getExternalFilesDir("home-device-tests"))
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
