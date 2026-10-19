package com.ytone.longcare.features.sales

import android.content.Context
import android.net.Uri
import com.ytone.longcare.R
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploadException
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploader
import com.ytone.longcare.features.photoupload.upload.UploadedPhoto
import com.ytone.longcare.model.AddUserLatentParamModel
import com.ytone.longcare.model.AddUserLatentResultModel
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.platform.sales.SalesEvaluationDeviceGateway
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
            assertEquals(0, submittedRequest.captured.isDisability)
            assertEquals("", submittedRequest.captured.remarks)
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
                                        key = "customer/one.jpg",
                                    )
                                ),
                                Result.success(
                                    UploadedPhoto(
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
            assertEquals(listOf(15, 15), photoUploader.folderTypes)
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

    @Test
    fun `retry after submission failure retains disability and remarks`() = runTest {
        val requests = mutableListOf<AddUserLatentParamModel>()
        val repository = mockk<SaleRepository>(relaxed = true) {
            coEvery { getRecentUserLatentList() } returns ApiResult.Success(emptyList())
            coEvery { addUserLatent(capture(requests)) } returnsMany listOf(
                ApiResult.Failure(400, "请重试"),
                ApiResult.Success(AddUserLatentResultModel(id = 7)),
            )
        }
        val viewModel = createViewModel(repository, QueuePhotoCloudUploader(ArrayDeque()), mockk(relaxed = true))
        val draft = validDraft().copy(isDisability = true, remarks = "  测试备注\n第二行  ")

        viewModel.submitCustomer(draft, emptyList())
        advanceUntilIdle()
        assertEquals("请重试", viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.submissionResult)

        viewModel.submitCustomer(draft, emptyList())
        advanceUntilIdle()
        assertEquals(2, requests.size)
        assertEquals(requests.first(), requests.last())
        assertEquals(1, requests.last().isDisability)
        assertEquals("测试备注\n第二行", requests.last().remarks)
        assertEquals(7, viewModel.uiState.value.submissionResult?.id)
    }


    @Test fun `failed relocation clears old point and optional submission has no stale coordinates`() = runTest {
        val sample = com.ytone.longcare.model.LocationResult(31.0, 121.0, "test")
        val location = mockk<LocationFacade> {
            coEvery { acquireCurrentLocation() } returnsMany listOf(
                com.ytone.longcare.domain.location.LocationAcquisition.Success(sample),
                com.ytone.longcare.domain.location.LocationAcquisition.Failure(
                    com.ytone.longcare.domain.location.LocationFailure.TIMEOUT),
            )
        }
        val request = slot<AddUserLatentParamModel>()
        val repository = mockk<SaleRepository>(relaxed = true) {
            coEvery { addUserLatent(capture(request)) } returns ApiResult.Success(AddUserLatentResultModel(id = 7))
        }
        val model = createViewModel(repository, QueuePhotoCloudUploader(ArrayDeque()), mockk(relaxed = true), location)
        model.requestCurrentLocation()
        advanceUntilIdle()
        assertEquals(sample, model.uiState.value.currentLocation)
        model.requestCurrentLocation()
        assertNull(model.uiState.value.currentLocation)
        advanceUntilIdle()
        model.submitCustomer(validDraft(), emptyList())
        advanceUntilIdle()
        assertEquals("", request.captured.liveLng)
        assertEquals("", request.captured.liveLat)
    }

    @Test fun `photo upload delay preserves the location obtained and displayed by the user`() = runTest {
        val sample = LocationResult(31.123456789, 121.987654321, "amap")
        val location = mockk<LocationFacade> {
            coEvery { acquireCurrentLocation() } returns LocationAcquisition.Success(sample)
            every { isUsable(sample) } returns false
        }
        val request = slot<AddUserLatentParamModel>()
        val repository = mockk<SaleRepository>(relaxed = true) {
            coEvery { addUserLatent(capture(request)) } returns ApiResult.Success(AddUserLatentResultModel(id = 7))
        }
        val uploader = mockk<PhotoCloudUploader> {
            coEvery { upload(any(), any()) } coAnswers {
                kotlinx.coroutines.delay(16_000)
                UploadedPhoto(key = "customer/test.jpg")
            }
        }
        val model = createViewModel(repository, uploader, mockk(relaxed = true), location)
        model.requestCurrentLocation()
        advanceUntilIdle()
        assertEquals(sample, model.uiState.value.currentLocation)
        model.submitCustomer(validDraft(), listOf(mockk(relaxed = true)))
        advanceUntilIdle()
        assertEquals("121.987654321", request.captured.liveLng)
        assertEquals("31.123456789", request.captured.liveLat)
        assertEquals("customer/test.jpg", request.captured.img1)
        assertEquals(sample, model.uiState.value.currentLocation)
        coVerify(exactly = 1) { location.acquireCurrentLocation() }
        verify(exactly = 0) { location.isUsable(any()) }
    }

    @Test fun `confirmation submits its displayed snapshot even when viewmodel has a different point`() = runTest {
        val displayed = LocationResult(30.25, 120.75, "amap")
        val other = LocationResult(32.0, 122.0, "amap")
        val location = mockk<LocationFacade> {
            coEvery { acquireCurrentLocation() } returns LocationAcquisition.Success(other)
            every { isUsable(any()) } returns false
        }
        val request = slot<AddUserLatentParamModel>()
        val repository = mockk<SaleRepository>(relaxed = true) {
            coEvery { addUserLatent(capture(request)) } returns ApiResult.Success(AddUserLatentResultModel(id = 7))
        }
        val model = createViewModel(repository, QueuePhotoCloudUploader(ArrayDeque()), mockk(relaxed = true), location)
        model.requestCurrentLocation()
        advanceUntilIdle()
        assertEquals(other, model.uiState.value.currentLocation)

        model.submitCustomer(validDraft(), emptyList(), displayed)
        advanceUntilIdle()

        assertEquals("120.75", request.captured.liveLng)
        assertEquals("30.25", request.captured.liveLat)
        assertEquals(displayed, model.uiState.value.currentLocation)
        coVerify(exactly = 1) { location.acquireCurrentLocation() }
        verify(exactly = 0) { location.isUsable(any()) }
    }

    private fun createViewModel(
        saleRepository: SaleRepository,
        photoCloudUploader: PhotoCloudUploader,
        applicationContext: Context,
        locationFacade: LocationFacade = mockk(relaxed = true),
    ): SalesViewModel =
        SalesViewModel(
            saleRepository = saleRepository,
            locationFacade = locationFacade,
            photoCloudUploader = photoCloudUploader,
            imagePipeline = testImagePipeline(applicationContext),
            evaluationDeviceGateway = mockk<SalesEvaluationDeviceGateway>(relaxed = true),
            systemConfigManager = mockk<SystemConfigManager>(relaxed = true),
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
            textResolver = ResourceTextResolver(applicationContext),
            cosRepository = mockk(relaxed = true),
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
        val folderTypes = mutableListOf<Int>()

        override suspend fun upload(
            uri: Uri,
            folderType: Int,
        ): UploadedPhoto {
            uploadedUris += uri
            folderTypes += folderType
            return results.removeFirst().getOrThrow()
        }
    }
}
