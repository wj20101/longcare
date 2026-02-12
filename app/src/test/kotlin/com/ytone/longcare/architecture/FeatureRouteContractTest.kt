package com.ytone.longcare.architecture

import com.ytone.longcare.feature.home.FeatureEntry as HomeFeatureEntry
import com.ytone.longcare.feature.identification.FeatureEntry as IdentificationFeatureEntry
import com.ytone.longcare.feature.login.FeatureEntry as LoginFeatureEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRouteContractTest {

    @Test
    fun `feature routes should be unique and prefixed`() {
        val routes = listOf(
            LoginFeatureEntry.ROUTE,
            HomeFeatureEntry.ROUTE,
            IdentificationFeatureEntry.ROUTE
        )

        assertEquals(routes.size, routes.toSet().size)
        assertTrue(routes.all { it.startsWith("feature_") })
    }
}
