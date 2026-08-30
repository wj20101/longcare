package com.ytone.longcare.navigation

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.runtime.Composable

/** Connects the single Activity fully-drawn reporter to the resolved, mutually exclusive root. */
@Composable
internal fun ReportStartupRootDrawn(
    expectedRoot: StartupRoot,
    actualReadiness: StartupRootReadiness,
) {
    ReportDrawnWhen {
        isExpectedStartupRootReady(
            expected = expectedRoot,
            actual = actualReadiness,
        )
    }
}
