package com.ytone.longcare.data.session

import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.model.UserScopeKey

data class RequestAuthSnapshot(
    val scopeKey: UserScopeKey,
    val userIdentity: Int,
    val token: String,
    val sessionEpoch: SessionEpoch,
)

data class FaceSetupIdentitySecret(
    val userId: Int,
    val userName: String,
    val identityCardNumber: String,
)

/** Purpose-limited access to secrets that are deliberately absent from public session state. */
interface SessionSecretProvider {
    fun requestAuthSnapshot(): RequestAuthSnapshot?
    fun faceSetupIdentity(): FaceSetupIdentitySecret?
    fun activeSessionFingerprint(): String?
}
