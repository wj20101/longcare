package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.model.*
import io.mockk.coEvery
import io.mockk.mockk

internal fun uploadedNfcPhotos(orderId: Long = 1) = listOf(
    ImageType.BEFORE_CARE to "before", ImageType.CENTER_CARE to "middle", ImageType.AFTER_CARE to "after",
).mapIndexed { index, (type, key) -> OrderImageEntity(id = index.toLong(), orderId = orderId,
    imageType = type.value, localUri = "file:///test", uploadStatus = ImageUploadStatus.SUCCESS.value, cloudKey = key) }

internal fun nfcTestImageRepository() = mockk<OrderImageRepository>(relaxed = true) {
    coEvery { getImagesByOrderId(any()) } coAnswers { uploadedNfcPhotos(firstArg<OrderKey>().orderId) }
}
