package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.faceauth.FaceVerificationConfigProvider
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.common.faceauth.FaceSdkEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IdentificationFaceSdkLaunchRequest(
    val id: Long,
    val config: FaceVerificationConfig,
    val request: FaceVerificationRequest,
)

internal sealed interface IdentificationFaceSdkPurpose {
    data object Standard : IdentificationFaceSdkPurpose
    data class FaceSetup(val ready: FaceSetupPreparation.Ready) : IdentificationFaceSdkPurpose
}

internal class IdentificationFaceSdkCoordinator(
    private val configProvider: FaceVerificationConfigProvider,
    private val onStandardConfigMissing: () -> Unit,
    private val onFaceSetupConfigMissing: () -> Unit,
) {
    private val mutableLaunchRequest = MutableStateFlow<IdentificationFaceSdkLaunchRequest?>(null)
    val launchRequest: StateFlow<IdentificationFaceSdkLaunchRequest?> = mutableLaunchRequest.asStateFlow()
    private var activePurpose: Pair<Long, IdentificationFaceSdkPurpose>? = null
    private var nextId = 0L
    private var latestPreparationId = 0L

    suspend fun prepareStandard(request: FaceVerificationRequest) = prepare(
        request = request,
        purpose = IdentificationFaceSdkPurpose.Standard,
        onConfigMissing = onStandardConfigMissing,
    )

    suspend fun prepareFaceSetup(
        request: FaceVerificationRequest,
        ready: FaceSetupPreparation.Ready,
    ) = prepare(request, IdentificationFaceSdkPurpose.FaceSetup(ready), onFaceSetupConfigMissing)

    fun consume(id: Long) {
        if (mutableLaunchRequest.value?.id == id) mutableLaunchRequest.value = null
    }

    fun dispatch(
        id: Long,
        event: FaceSdkEvent,
        onStandard: (FaceSdkEvent) -> Unit,
        onFaceSetup: (FaceSdkEvent, FaceSetupPreparation.Ready) -> Unit,
    ) {
        val purpose = activePurpose?.takeIf { it.first == id }?.second ?: return
        when (purpose) {
            IdentificationFaceSdkPurpose.Standard -> onStandard(event)
            is IdentificationFaceSdkPurpose.FaceSetup -> onFaceSetup(event, purpose.ready)
        }
        if (event !is FaceSdkEvent.InitSuccess) activePurpose = null
    }

    private suspend fun prepare(
        request: FaceVerificationRequest,
        purpose: IdentificationFaceSdkPurpose,
        onConfigMissing: () -> Unit,
    ) {
        val preparationId = ++latestPreparationId
        mutableLaunchRequest.value = null
        activePurpose = null
        val config = configProvider.getFaceVerificationConfig() ?: run {
            onConfigMissing()
            return
        }
        if (preparationId != latestPreparationId) return
        val launch = IdentificationFaceSdkLaunchRequest(++nextId, config, request)
        activePurpose = launch.id to purpose
        mutableLaunchRequest.value = launch
    }
}
