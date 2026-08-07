package com.ytone.longcare.data.repository

import android.net.Uri
import com.ytone.longcare.common.image.UnifiedImagePipeline
import com.ytone.longcare.data.database.dao.OrderImageDao
import com.ytone.longcare.data.database.entity.OrderImageEntityDb
import com.ytone.longcare.model.ImageType
import com.ytone.longcare.model.OrderKey
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageRepositoryManagedFileTest {
    private val orderImageDao = mockk<OrderImageDao>(relaxed = true)
    private val imagePipeline = mockk<UnifiedImagePipeline>(relaxed = true)
    private val repository =
        ImageRepository(
            orderImageDao = orderImageDao,
            imagePipeline = imagePipeline,
        )

    @Test
    fun `deleting an order also deletes its managed image files`() =
        runTest {
            val orderKey = OrderKey(orderId = 42L)
            val first = image(id = 1L, orderId = 42L, path = "/managed/first.jpg")
            val second = image(id = 2L, orderId = 42L, path = "/managed/second.jpg")
            coEvery { orderImageDao.getImagesByOrderId(42L) } returns listOf(first, second)

            repository.deleteImagesByOrderId(orderKey)

            coVerifyOrder {
                orderImageDao.getImagesByOrderId(42L)
                orderImageDao.deleteByOrderId(42L)
                imagePipeline.deleteManagedImages(
                    match { uris ->
                        uris.map(Uri::getPath) ==
                            listOf("/managed/first.jpg", "/managed/second.jpg")
                    }
                )
            }
        }

    @Test
    fun `deleting one database image also deletes its managed file`() =
        runTest {
            val image = image(id = 7L, orderId = 42L, path = "/managed/single.jpg")
            coEvery { orderImageDao.getById(7L) } returns image

            repository.deleteImage(7L)

            coVerifyOrder {
                orderImageDao.getById(7L)
                orderImageDao.deleteById(7L)
                imagePipeline.deleteManagedImages(
                    match { uris -> uris.single().path == "/managed/single.jpg" }
                )
            }
        }

    private fun image(
        id: Long,
        orderId: Long,
        path: String,
    ) =
        OrderImageEntityDb(
            id = id,
            orderId = orderId,
            imageType = ImageType.BEFORE_CARE.value,
            localUri = Uri.fromFile(java.io.File(path)).toString(),
            localPath = path,
        )
}
