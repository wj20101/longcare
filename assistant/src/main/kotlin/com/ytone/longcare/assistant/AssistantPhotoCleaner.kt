package com.ytone.longcare.assistant

import androidx.core.net.toUri
import com.ytone.longcare.common.image.UnifiedImagePipeline
import com.ytone.longcare.core.common.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Delete only relinquished, managed files in this application's sandbox. */
@Singleton
class AssistantPhotoCleaner @Inject constructor(
    private val imagePipeline: UnifiedImagePipeline,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    fun discard(uri: String) {
        if (uri.isBlank()) return
        // Outlive the screen/ViewModel so leaving immediately cannot cancel cleanup.
        applicationScope.launch {
            imagePipeline.deleteManagedImage(uri.toUri())
        }
    }
}
