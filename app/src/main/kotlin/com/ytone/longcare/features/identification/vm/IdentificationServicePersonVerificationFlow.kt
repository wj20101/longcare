package com.ytone.longcare.features.identification.vm

import android.content.Context
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.features.identification.data.IdentificationFaceDataSource
import com.ytone.longcare.models.protos.User
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
    sourcePhotoUrl: String,
    faceDataSource: IdentificationFaceDataSource,
    resolveCurrentUser: suspend () -> User?,
    beginVerification: (VerificationType) -> Unit,
    startVerification: suspend (Context, String, String, String, String, String) -> Unit,
    onFailure: (String) -> Unit,
) {
    scope.launch {
        beginVerification(VerificationType.SERVICE_PERSON)

        try {
            logD("从服务器下载人脸图片: $sourcePhotoUrl", tag = "IdentificationVM")
            val sourcePhotoBase64 = faceDataSource.downloadAndConvertToBase64(sourcePhotoUrl)
            logD("下载成功，Base64长度: ${sourcePhotoBase64.length}", tag = "IdentificationVM")

            val currentUser = resolveCurrentUser()
            if (currentUser != null) {
                faceDataSource.writeUserFaceBase64(currentUser.userId, sourcePhotoBase64)
                logD("已保存到本地缓存", tag = "IdentificationVM")
            }

            startVerification(context, name, idNo, orderNo, userId, sourcePhotoBase64)
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
    startVerification: suspend (Context, String, String, String, String, String) -> Unit,
    onFailure: (String, Throwable?) -> Unit,
) {
    scope.launch {
        beginVerification(VerificationType.SERVICE_PERSON)

        try {
            startVerification(context, name, idNo, orderNo, userId, sourcePhotoBase64)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure("人脸验证失败: ${e.message}", e)
        }
    }
}
