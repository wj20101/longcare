package com.ytone.longcare.domain.faceauth

import kotlinx.coroutines.flow.StateFlow

/** An opaque operator-session generation, independent of the person being verified. */
interface FaceVerificationSession {
    val sessionGeneration: StateFlow<Long?>

    fun isCurrent(generation: Long): Boolean = sessionGeneration.value == generation
}
