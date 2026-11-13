package com.ytone.longcare.domain.order

import com.ytone.longcare.model.*
import org.junit.Assert.*
import org.junit.Test

class CheckoutPhotosTest {
    private val key = OrderKey(7, 3)
    private val before = row(ImageType.BEFORE_CARE, "before")
    private val after = row(ImageType.AFTER_CARE, "after")

    @Test fun `required uploaded photos create exactly the submitted lists and center is optional`() {
        assertEquals(CheckoutPhotos(listOf("before"), emptyList(), listOf("after")), CheckoutPhotos.from(key, listOf(before, after)))
        assertEquals(listOf("center"), CheckoutPhotos.from(key, listOf(before, row(ImageType.CENTER_CARE, "center"), after))?.center)
    }

    @Test fun `every unfinished status blocks even when other required photos are uploaded`() {
        for (status in ImageUploadStatus.entries.filter { it != ImageUploadStatus.SUCCESS }) {
            assertNull(status.name, CheckoutPhotos.from(key, listOf(before, after,
                row(ImageType.CENTER_CARE, "pending").copy(uploadStatus = status.value))))
        }
    }

    @Test fun `missing empty blank foreign order or missing cloud keys never pass`() {
        val invalid = listOf(emptyList(), listOf(before), listOf(after),
            listOf(before, after.copy(cloudKey = null)), listOf(before, after.copy(cloudKey = " ")),
            listOf(before, after.copy(orderId = 8)))
        invalid.forEach { assertNull(CheckoutPhotos.from(key, it)) }
    }

    @Test fun `customer pictures do not satisfy service photos and selected rows preserve request ordering and multiplicity`() {
        val customer = row(ImageType.CUSTOMER, "customer").copy(uploadStatus = ImageUploadStatus.PENDING.value)
        assertNull(CheckoutPhotos.from(key, listOf(customer)))
        assertEquals(CheckoutPhotos(listOf("before", "before"), emptyList(), listOf("after")),
            CheckoutPhotos.from(key, listOf(customer, before, before.copy(id = 22), after)))
    }

    private fun row(type: ImageType, key: String) = OrderImageEntity(orderId = 7,
        imageType = type.value, localUri = "file:///test", uploadStatus = ImageUploadStatus.SUCCESS.value, cloudKey = key)
}
