package com.ytone.longcare.data.database.entity

import com.ytone.longcare.model.LocationUploadStatus
import com.ytone.longcare.model.OrderLocationEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderLocationEntityMapperTest {

    @Test
    fun `order location mapper preserves sdk metadata round trip`() {
        val model = OrderLocationEntity(
            id = 9L,
            orderId = 100L,
            latitude = 30.506,
            longitude = 120.226,
            accuracy = 8.5f,
            provider = "amap_continuous",
            uploadStatus = LocationUploadStatus.PENDING.value,
            timestamp = 1_717_000_000_000L,
            coordType = "GCJ02",
            locationType = 5,
            trustedLevel = 2,
            locationTime = 1_717_000_000_123L
        )

        val roundTrip = model.toDb().toModel()

        assertEquals(model, roundTrip)
    }
}
