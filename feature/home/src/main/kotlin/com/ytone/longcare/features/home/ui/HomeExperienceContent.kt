package com.ytone.longcare.features.home.ui

import androidx.compose.runtime.Composable
import com.ytone.longcare.features.home.api.HomeExperience

@Composable
internal fun HomeExperienceContent(
    experience: HomeExperience,
    loadingContent: @Composable () -> Unit,
    careContent: @Composable () -> Unit,
    salesContent: @Composable () -> Unit,
) {
    when (experience) {
        HomeExperience.Loading -> loadingContent()
        HomeExperience.Care -> careContent()
        HomeExperience.Sales -> salesContent()
    }
}
