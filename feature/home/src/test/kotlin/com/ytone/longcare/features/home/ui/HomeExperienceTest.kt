package com.ytone.longcare.features.home.ui

import com.ytone.longcare.features.home.api.HomeExperience
import com.ytone.longcare.features.home.api.resolveHomeExperience
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeExperienceTest {
    @Test
    fun salesRoleUsesSalesExperience() {
        assertEquals(HomeExperience.Sales, resolveHomeExperience(userIdentity = 2))
    }

    @Test
    fun careRolesUseCareExperience() {
        assertEquals(HomeExperience.Care, resolveHomeExperience(userIdentity = 1))
        assertEquals(HomeExperience.Care, resolveHomeExperience(userIdentity = 4))
    }

    @Test
    fun missingUserUsesLoadingInsteadOfRetainingPreviousRole() {
        val accountSwitch = listOf(2, null, 1).map(::resolveHomeExperience)

        assertEquals(
            listOf(HomeExperience.Sales, HomeExperience.Loading, HomeExperience.Care),
            accountSwitch,
        )
    }
}
