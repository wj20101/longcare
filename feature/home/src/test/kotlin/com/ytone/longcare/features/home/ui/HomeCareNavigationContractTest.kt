package com.ytone.longcare.features.home.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCareNavigationContractTest {

    @Test
    fun `care home keeps three pages in home nursing profile order`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreenContentSections.kt"
        ).readText()

        val home = source.indexOf("R.string.home_navigation_home")
        val nursing = source.indexOf("R.string.home_navigation_nursing")
        val profile = source.indexOf("R.string.home_navigation_profile")

        assertTrue(home >= 0)
        assertTrue(nursing > home)
        assertTrue(profile > nursing)
        assertTrue(source.contains("userScrollEnabled = false"))
    }

    @Test
    fun `care pages map only to explicit home actions`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreenContentSections.kt"
        ).readText()

        assertTrue(source.contains("onNavigateToCarePlansList = actions.onNavigateToCarePlansList"))
        assertTrue(source.contains("onNavigateToServiceRecordsList = actions.onNavigateToServiceRecordsList"))
        assertTrue(source.contains("onNavigateToNursingExecution = actions.onNavigateToNursingExecution"))
        assertTrue(source.contains("onOpenUserAgreement = actions.onOpenUserAgreement"))
        assertTrue(source.contains("onOpenPrivacyPolicy = actions.onOpenPrivacyPolicy"))
    }
}
