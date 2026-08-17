package com.ytone.longcare.features.photoupload.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicePhotoLimitPolicyTest {
    @Test
    fun `positive config enables limit`() {
        assertEquals(9, ServicePhotoLimitPolicy.normalize(9))
    }

    @Test
    fun `non-positive config keeps workflow unrestricted`() {
        assertNull(ServicePhotoLimitPolicy.normalize(0))
        assertNull(ServicePhotoLimitPolicy.normalize(-1))
    }

    @Test
    fun `category at limit cannot add photo`() {
        assertFalse(ServicePhotoLimitPolicy.canAdd(currentCount = 3, maxCount = 3))
        assertTrue(ServicePhotoLimitPolicy.canAdd(currentCount = 2, maxCount = 3))
    }

    @Test
    fun `incoming photos are trimmed to remaining category slots`() {
        assertEquals(
            1,
            ServicePhotoLimitPolicy.allowedIncomingCount(
                currentCount = 2,
                requestedCount = 3,
                maxCount = 3,
            ),
        )
        assertEquals(
            0,
            ServicePhotoLimitPolicy.allowedIncomingCount(
                currentCount = 4,
                requestedCount = 1,
                maxCount = 3,
            ),
        )
    }
}
