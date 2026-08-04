package com.ytone.longcare.features.sales

import android.content.Context
import android.net.Uri
import com.ytone.longcare.R
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.integration.qlz.QlzSdkClient
import com.ytone.longcare.model.AddUserLatentParamModel
import com.ytone.longcare.model.AddUserLatentResultModel
import com.ytone.longcare.model.CosUploadResult
import com.ytone.longcare.model.UploadParams
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelSubmissionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @After
    fun tearDown() {
        unmockkObject(CosUtils)
    }

    @Test
    fun `customer submission sends COS keys instead of private URLs`() =
        runTest {
            val photoOne = mockk<Uri>()
            val photoTwo = mockk<Uri>()
            val applicationContext = mockk<Context>(relaxed = true)
            val firstUpload = uploadParams("content://photo/one")
            val secondUpload = uploadParams("content://photo/two")
            mockkObject(CosUtils)
            every {
                CosUtils.createUploadParams(
                    applicationContext,
                    photoOne,
                    CosConstants.DEFAULT_FOLDER_TYPE,
                )
            } returns firstUpload
            every {
                CosUtils.createUploadParams(
                    applicationContext,
                    photoTwo,
                    CosConstants.DEFAULT_FOLDER_TYPE,
                )
            } returns secondUpload

            val submittedRequest = slot<AddUserLatentParamModel>()
            val saleRepository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns ApiResult.Success(emptyList())
                    coEvery { addUserLatent(capture(submittedRequest)) } returns
                        ApiResult.Success(AddUserLatentResultModel(id = 7))
                }
            val cosRepository =
                mockk<CosRepository>(relaxed = true) {
                    coEvery { uploadFile(firstUpload) } returns
                        CosUploadResult(
                            success = true,
                            url = "https://private.example/one",
                            key = "customer/one.jpg",
                        )
                    coEvery { uploadFile(secondUpload) } returns
                        CosUploadResult(
                            success = true,
                            url = "https://private.example/two",
                            key = "customer/two.jpg",
                        )
                }
            val viewModel =
                createViewModel(
                    saleRepository = saleRepository,
                    cosRepository = cosRepository,
                    applicationContext = applicationContext,
                )

            viewModel.submitCustomer(
                draft = validDraft(),
                photoUris = listOf(photoOne, photoTwo),
            )
            advanceUntilIdle()

            assertEquals("customer/one.jpg", submittedRequest.captured.img1)
            assertEquals("customer/two.jpg", submittedRequest.captured.img2)
            assertEquals("", submittedRequest.captured.img3)
            assertEquals("13800138000", submittedRequest.captured.guardianPhone)
        }

    @Test
    fun `customer submission stops when COS does not return a key`() =
        runTest {
            val photo = mockk<Uri>()
            val applicationContext =
                mockk<Context>(relaxed = true) {
                    every {
                        getString(R.string.sales_error_photo_upload, 1)
                    } returns "第 1 张照片上传失败，请稍后重试"
                }
            val upload = uploadParams("content://photo/without-key")
            mockkObject(CosUtils)
            every {
                CosUtils.createUploadParams(
                    applicationContext,
                    photo,
                    CosConstants.DEFAULT_FOLDER_TYPE,
                )
            } returns upload

            val saleRepository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns ApiResult.Success(emptyList())
                }
            val cosRepository =
                mockk<CosRepository>(relaxed = true) {
                    coEvery { uploadFile(upload) } returns
                        CosUploadResult(
                            success = true,
                            url = "https://private.example/without-key",
                            key = null,
                        )
                }
            val viewModel =
                createViewModel(
                    saleRepository = saleRepository,
                    cosRepository = cosRepository,
                    applicationContext = applicationContext,
                )

            viewModel.submitCustomer(
                draft = validDraft(),
                photoUris = listOf(photo),
            )
            advanceUntilIdle()

            coVerify(exactly = 0) { saleRepository.addUserLatent(any()) }
            assertEquals(
                "第 1 张照片上传失败，请稍后重试",
                viewModel.uiState.value.errorMessage,
            )
        }

    @Test
    fun `camera and gallery selections are deduplicated and limited to three photos`() {
        val first = mockk<Uri>(relaxed = true)
        val second = mockk<Uri>(relaxed = true)
        val third = mockk<Uri>(relaxed = true)
        val fourth = mockk<Uri>(relaxed = true)

        val merged =
            mergeSalesCustomerPhotoUris(
                existing = listOf(first),
                added = listOf(first, second, third, fourth),
            )

        assertEquals(listOf(first, second, third), merged)
    }

    private fun createViewModel(
        saleRepository: SaleRepository,
        cosRepository: CosRepository,
        applicationContext: Context,
    ): SalesViewModel =
        SalesViewModel(
            saleRepository = saleRepository,
            locationFacade = mockk<LocationFacade>(relaxed = true),
            cosRepository = cosRepository,
            qlzSdkClient = mockk<QlzSdkClient>(relaxed = true),
            systemConfigManager = mockk<SystemConfigManager>(relaxed = true),
            applicationContext = applicationContext,
        )

    private fun validDraft(): SalesCustomerDraft =
        SalesCustomerDraft(
            userName = "测试老人",
            identityCardNumber = "330106199001011234",
            guardianName = "测试联系人",
            guardianPhone = "13800138000",
            guardianRelation = "子女",
            liveAddress = "杭州市测试地址",
        )

    private fun uploadParams(uri: String): UploadParams =
        UploadParams(
            fileUri = uri,
            key = "",
            folderType = CosConstants.DEFAULT_FOLDER_TYPE,
            contentType = "image/jpeg",
        )
}
