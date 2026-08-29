package com.ytone.longcare.features.identification.data

import android.util.Base64
import android.util.Base64OutputStream
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.domain.facecache.FaceCacheCleaner
import com.ytone.longcare.domain.facecache.UserFaceArtifactStorage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Request-scoped face encoding backed by the current composite user's managed storage. */
@Singleton
class IdentificationFaceDataSource @Inject constructor(
    private val userFaceArtifactStorage: UserFaceArtifactStorage,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FaceCacheCleaner {
    override suspend fun clearUserFaceArtifacts(userId: Int) {
        try {
            userFaceArtifactStorage.clearCurrentFaceArtifacts(userId)
            logD("清除当前用户的受管人脸临时数据", tag = TAG)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            logE("清除当前用户受管人脸数据异常", tag = TAG, throwable = error)
        }
    }

    suspend fun imageFileToBase64(imageFile: File): String = withContext(ioDispatcher) {
        val output = ByteArrayOutputStream()
        Base64OutputStream(output, Base64.NO_WRAP).use { base64Output ->
            imageFile.inputStream().use { input -> input.copyTo(base64Output) }
        }
        output.toString(Charsets.US_ASCII.name())
    }

    private companion object {
        const val TAG = "IdentificationFaceDataSource"
    }
}
