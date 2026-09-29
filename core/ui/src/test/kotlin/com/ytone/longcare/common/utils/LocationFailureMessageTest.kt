package com.ytone.longcare.common.utils

import com.ytone.longcare.core.ui.R
import com.ytone.longcare.domain.location.LocationFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationFailureMessageTest {
    @Test fun `configuration quality network and permission failures have distinct actionable messages`() {
        val expected = mapOf(
            LocationFailure.PERMISSION to R.string.location_error_permission,
            LocationFailure.SERVICE_DISABLED to R.string.location_error_disabled,
            LocationFailure.BUSY to R.string.location_error_busy,
            LocationFailure.TIMEOUT to R.string.location_error_timeout,
            LocationFailure.QUALITY to R.string.location_error_quality,
            LocationFailure.NETWORK to R.string.location_error_network,
            LocationFailure.CONFIGURATION to R.string.location_error_configuration,
            LocationFailure.UNAVAILABLE to R.string.location_error_unavailable,
        )
        assertEquals(LocationFailure.entries.toSet(), expected.keys)
        expected.forEach { (failure, resource) -> assertEquals(resource, failure.messageRes()) }
        assertEquals(expected.size, expected.values.toSet().size)
    }
}
