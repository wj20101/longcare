package com.ytone.longcare.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable

/** Debug-only host: lets instrumentation recreate the real navigation composition. */
class NavigationStateTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recreatedContent?.let { content -> setContent { content() } }
    }

    companion object {
        var recreatedContent: (@Composable () -> Unit)? = null
    }
}
