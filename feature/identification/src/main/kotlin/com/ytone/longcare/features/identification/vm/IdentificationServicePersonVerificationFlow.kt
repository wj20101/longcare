package com.ytone.longcare.features.identification.vm

import android.content.Context
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.domain.faceauth.model.FaceVerificationRequest
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun launchSelfProvidedFaceVerificationAndCache(
    scope: CoroutineScope,
    context: Context,
    name: String,
    idNo: String,
    orderNo: String,
    userId: String,
    cacheUserId: Int,
    sourcePhotoUrl: String,
    faceDataSource: IdentificationFaceDataSource,
    beginVerification: (VerificationType) -> Unit,
    startVerificationWithRequest: suspend (Context, FaceVerificationRequest) -> Unit,
    onFailure: (String) -> Unit,
) {
    scope.launch {
        beginVerification(VerificationType.SERVICE_PERSON)

        try {
            logD("从服务器下载人脸图片: $sourcePhotoUrl", tag = "IdentificationVM")
            val sourcePhotoBase64 = faceDataSource.downloadCacheAndConvertToBase64(
                url = sourcePhotoUrl,
                userId = cacheUserId,
            )
            logD("下载成功，Base64长度: ${sourcePhotoBase64.length}", tag = "IdentificationVM")
            logD("已保存到本地缓存", tag = "IdentificationVM")

            executeSelfProvidedVerification(
                context = context,
                name = name,
                idNo = idNo,
                orderNo = orderNo,
                userId = userId,
                sourcePhotoBase64 = sourcePhotoBase64,
                startVerificationWithRequest = startVerificationWithRequest,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("下载人脸图片失败", tag = "IdentificationVM", throwable = e)
            onFailure("获取人脸照片失败: ${e.message}")
        }
    }
}

internal fun launchSelfProvidedFaceVerificationWithBase64(
    scope: CoroutineScope,
    context: Context,
    name: String,
    idNo: String,
    orderNo: String,
    userId: String,
    sourcePhotoBase64: String,
    beginVerification: (VerificationType) -> Unit,
    startVerificationWithRequest: suspend (Context, FaceVerificationRequest) -> Unit,
    onFailure: (String, Throwable?) -> Unit,
) {
    scope.launch {
        beginVerification(VerificationType.SERVICE_PERSON)

        try {
            executeSelfProvidedVerification(
                context = context,
                name = name,
                idNo = idNo,
                orderNo = orderNo,
                userId = userId,
                sourcePhotoBase64 = sourcePhotoBase64,
                startVerificationWithRequest = startVerificationWithRequest,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure("人脸验证失败: ${e.message}", e)
        }
    }
}

private suspend fun executeSelfProvidedVerification(
    context: Context,
    name: String,
    idNo: String,
    orderNo: String,
    userId: String,
    sourcePhotoBase64: String,
    startVerificationWithRequest: suspend (Context, FaceVerificationRequest) -> Unit,
) {
    val request = createFaceVerificationRequest(
        name = name,
        idNo = idNo,
        orderNo = orderNo,
        userId = userId,
        sourcePhotoBase64 = sourcePhotoBase64
    )
    startVerificationWithRequest(context, request)
}
