package com.ytone.longcare.features.sales

import android.content.Context
import android.net.Uri
import com.ytone.longcare.R
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploadException
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploader
import com.ytone.longcare.features.photoupload.upload.UploadedPhoto
import com.ytone.longcare.integration.qlz.QlzSdkClient
import com.ytone.longcare.model.AddUserLatentParamModel
import com.ytone.longcare.model.AddUserLatentResultModel
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelSubmissionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `customer submission only requires user name`() =
        runTest {
            val applicationContext = mockk<Context>(relaxed = true)
            val submittedRequest = slot<AddUserLatentParamModel>()
            val saleRepository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns ApiResult.Success(emptyList())
                    coEvery { addUserLatent(capture(submittedRequest)) } returns
                        ApiResult.Success(AddUserLatentResultModel(id = 7))
                }
            val photoUploader = QueuePhotoCloudUploader(results = ArrayDeque())
            val viewModel =
                createViewModel(
                    saleRepository = saleRepository,
                    photoCloudUploader = photoUploader,
                    applicationContext = applicationContext,
                )

            viewModel.submitCustomer(
                draft = SalesCustomerDraft(userName = "  测试老人  "),
                photoUris = emptyList(),
            )
            advanceUntilIdle()

            assertEquals("测试老人", submittedRequest.captured.userName)
            assertEquals("", submittedRequest.captured.identityCardNumber)
            assertEquals("", submittedRequest.captured.guardianName)
            assertEquals("", submittedRequest.captured.guardianPhone)
            assertEquals("", submittedRequest.captured.guardianRelation)
            assertEquals("", submittedRequest.captured.liveAddress)
            assertEquals("", submittedRequest.captured.liveLng)
            assertEquals("", submittedRequest.captured.liveLat)
            assertEquals(emptyList<Uri>(), photoUploader.uploadedUris)
            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `optional identity and phone are validated only when entered`() {
        assertEquals(
            R.string.sales_registration_name_hint,
            SalesCustomerDraft(
                identityCardNumber = "330106199001011234",
                guardianPhone = "13800138000",
            ).validationMessageRes(),
        )
        assertNull(SalesCustomerDraft(userName = "测试老人").validationMessageRes())
        assertEquals(
            R.string.sales_validation_identity,
            SalesCustomerDraft(
                userName = "测试老人",
                identityCardNumber = "123456",
            ).validationMessageRes(),
        )
        assertEquals(
            R.string.sales_validation_phone,
            SalesCustomerDraft(
                userName = "测试老人",
                guardianPhone = "123456",
            ).validationMessageRes(),
        )
    }

    @Test
    fun `customer submission sends COS keys instead of private URLs`() =
        runTest {
            val photoOne = mockk<Uri>()
            val photoTwo = mockk<Uri>()
            val applicationContext = mockk<Context>(relaxed = true)

            val submittedRequest = slot<AddUserLatentParamModel>()
            val saleRepository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns ApiResult.Success(emptyList())
                    coEvery { addUserLatent(capture(submittedRequest)) } returns
                        ApiResult.Success(AddUserLatentResultModel(id = 7))
                }
            val photoUploader =
                QueuePhotoCloudUploader(
                    results =
                        ArrayDeque(
                            listOf(
                                Result.success(
                                    UploadedPhoto(
                                        url = "https://private.example/one",
                                        key = "customer/one.jpg",
                                    )
                                ),
                                Result.success(
                                    UploadedPhoto(
                                        url = "https://private.example/two",
                                        key = "customer/two.jpg",
                                    )
                                ),
                            )
                        )
                )
            val viewModel =
                createViewModel(
                    saleRepository = saleRepository,
                    photoCloudUploader = photoUploader,
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
            assertEquals(listOf(photoOne, photoTwo), photoUploader.uploadedUris)
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
            val saleRepository =
                mockk<SaleRepository>(relaxed = true) {
                    coEvery { getRecentUserLatentList() } returns ApiResult.Success(emptyList())
                }
            val photoUploader =
                QueuePhotoCloudUploader(
                    results =
                        ArrayDeque(
                            listOf(
                                Result.failure(
                                    PhotoCloudUploadException("图片上传未返回有效文件信息")
                                )
                            )
                        )
                )
            val viewModel =
                createViewModel(
                    saleRepository = saleRepository,
                    photoCloudUploader = photoUploader,
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
    fun `captured photos are deduplicated and limited to three photos`() {
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
        photoCloudUploader: PhotoCloudUploader,
        applicationContext: Context,
    ): SalesViewModel =
        SalesViewModel(
            saleRepository = saleRepository,
            locationFacade = mockk<LocationFacade>(relaxed = true),
            photoCloudUploader = photoCloudUploader,
            imagePipeline = testImagePipeline(applicationContext),
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

    private class QueuePhotoCloudUploader(
        private val results: ArrayDeque<Result<UploadedPhoto>>,
    ) : PhotoCloudUploader {
        val uploadedUris = mutableListOf<Uri>()

        override suspend fun upload(
            uri: Uri,
            folderType: Int,
        ): UploadedPhoto {
            uploadedUris += uri
            return results.removeFirst().getOrThrow()
        }
    }
}
