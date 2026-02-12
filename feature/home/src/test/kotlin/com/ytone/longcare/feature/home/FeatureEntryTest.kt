package com.ytone.longcare.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureEntryTest {

    @Test
    fun `route should stay stable`() {
        assertEquals("feature_home", FeatureEntry.ROUTE)
    }

    @Test
    fun `route should use feature prefix`() {
        assertTrue(FeatureEntry.ROUTE.startsWith("feature_"))
    }
}
