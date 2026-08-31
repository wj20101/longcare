package com.ytone.longcare.navigation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDestinationContractTest {

    @Test
    fun `home destination keeps graph owner and acknowledged camera result`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/navigation/AppNavGraphsEntry.kt"
        ).readText()

        assertTrue(source.contains("navController.requireHomeGraphBackStackEntry()"))
        assertTrue(source.contains("hiltViewModel(parentEntry)"))
        assertTrue(source.contains("remember(todayOrderViewModel)"))
        assertTrue(source.contains("todayOrderViewModel.asHomeOrderStateSource()"))
        assertTrue(source.contains("orderStateSource = orderStateSource"))
        assertTrue(source.contains("NavigationConstants.CAPTURED_IMAGE_URI_KEY"))
        assertTrue(source.contains("savedStateHandle.remove<String>"))
    }

    @Test
    fun `home web actions remain app-owned and do not filter hosts`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/navigation/AppNavGraphsEntry.kt"
        ).readText()

        assertTrue(source.contains("navController.navigateToWebView(url, title)"))
        assertFalse(source.contains("allowedHost"))
        assertFalse(source.contains("host =="))
    }

    @Test
    fun `home destination composes the feature with app owned config sales and startup adapters`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/navigation/AppNavGraphsEntry.kt"
        ).readText()

        assertTrue(source.contains("HomeFeatureScreen("))
        assertTrue(source.contains("versionName = BuildConfig.VERSION_NAME"))
        assertTrue(source.contains("SalesExperienceScreen("))
        assertTrue(source.contains("ReportHomeStartupRootDrawn(experience)"))
        assertFalse(source.contains("HomeSharedViewModel"))
    }
}
