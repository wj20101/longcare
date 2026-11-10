package com.ytone.longcare.features.update.viewmodel

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.common.utils.ApkInstallUtils
import com.ytone.longcare.platform.update.ApkInstallGateway
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], application = Application::class)
class AppUpdateViewModelTest {
    @get:Rule val files = TemporaryFolder()

    private val workManager = mockk<WorkManager>()
    private val installGateway = mockk<ApkInstallGateway>()
    private val textResolver = mockk<ResourceTextResolver>()

    @Test
    fun `restored download resumes installation once after permission is granted`() {
        val apk = files.newFile("completed-update.apk")
        val savedState = SavedStateHandle(mapOf(PENDING_INSTALL_PATH to apk.absolutePath))
        every { installGateway.canInstallApk() } returns true
        every { installGateway.installApk(apk.absolutePath) } returns ApkInstallUtils.LaunchResult.Launched

        val viewModel = createViewModel(savedState)
        viewModel.onDialogPresented()

        // The dialog resumes installation only while this state is true.
        assertTrue(viewModel.uiState.value.hasPendingInstall)
        viewModel.checkPermissionAndInstall()
        viewModel.checkPermissionAndInstall()

        assertFalse(viewModel.uiState.value.hasPendingInstall)
        assertNull(savedState.get<String>(PENDING_INSTALL_PATH))

        val recreatedViewModel = createViewModel(savedState)
        recreatedViewModel.onDialogPresented()
        assertFalse(recreatedViewModel.uiState.value.hasPendingInstall)
        recreatedViewModel.checkPermissionAndInstall()

        verify(exactly = 1) { installGateway.installApk(apk.absolutePath) }
        verify(exactly = 0) { installGateway.requestInstallPermission() }
        verify { workManager wasNot Called }
    }

    @Test
    fun `opening a dialog without a pending download does not launch installation`() {
        val viewModel = createViewModel(SavedStateHandle())

        viewModel.onDialogPresented()
        viewModel.checkPermissionAndInstall()

        assertFalse(viewModel.uiState.value.hasPendingInstall)
        verify { installGateway wasNot Called }
        verify { workManager wasNot Called }
    }

    private fun createViewModel(savedState: SavedStateHandle) = AppUpdateViewModel(
        workManager = workManager,
        apkInstallGateway = installGateway,
        savedStateHandle = savedState,
        textResolver = textResolver,
    )

    private companion object {
        const val PENDING_INSTALL_PATH = "app_update.pending_install_path"
    }
}
