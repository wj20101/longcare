package com.ytone.longcare.data.startup

import com.ytone.longcare.domain.startup.UserStorageNamespaceCutoverGate

/** Default only for direct construction in focused tests; Hilt always supplies the production gate. */
internal object AssumedCompletedCutoverGate : UserStorageNamespaceCutoverGate {
    override val isCompleted: Boolean = true

    override suspend fun ensureCompleted() = Unit
}
