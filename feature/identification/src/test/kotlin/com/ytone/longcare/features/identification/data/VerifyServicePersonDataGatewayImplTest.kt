package com.ytone.longcare.features.identification.data

import com.google.common.truth.Truth.assertThat
import com.ytone.longcare.domain.facecache.FaceCacheCleaner
import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.features.identification.domain.ServicePersonFaceSource
import com.ytone.longcare.model.CheckFaceParamModel
import com.ytone.longcare.model.FaceResultModel
import com.ytone.longcare.model.SetFaceParamModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.result.SessionInvalidationCode
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VerifyServicePersonDataGatewayImplTest {
    @Test
    fun `nonblank server face url is available`() = runTest {
        val gateway = gateway(ApiResult.Success(FaceResultModel("https://face.url")))

        assertThat(gateway.resolveFaceSource()).isEqualTo(
            ServicePersonFaceSource.RemoteFace("https://face.url"),
        )
    }

    @Test
    fun `blank face url means setup is required`() = runTest {
        val gateway = gateway(ApiResult.Success(FaceResultModel("")))

        assertThat(gateway.resolveFaceSource())
            .isEqualTo(ServicePersonFaceSource.RequireFaceSetup)
    }

    @Test
    fun `any non-session business failure means setup is required`() = runTest {
        val gateway = gateway(ApiResult.Failure(code = 4321, message = "未采集人脸"))

        assertThat(gateway.resolveFaceSource())
            .isEqualTo(ServicePersonFaceSource.RequireFaceSetup)
    }

    @Test
    fun `request exception means setup is required by business policy`() = runTest {
        val gateway = gateway(ApiResult.Exception(IOException("offline")))

        assertThat(gateway.resolveFaceSource())
            .isEqualTo(ServicePersonFaceSource.RequireFaceSetup)
    }

    @Test
    fun `session failure is not converted into face setup navigation`() = runTest {
        val gateway = gateway(
            ApiResult.Failure(
                code = SessionInvalidationCode.INVALID_SESSION,
                message = "登录已失效",
            ),
        )

        assertThat(gateway.resolveFaceSource())
            .isEqualTo(ServicePersonFaceSource.SessionInvalidated)
    }

    @Test
    fun `legacy face cleanup delegates to the shared cleaner`() = runTest {
        val cleaner = RecordingFaceCacheCleaner()
        val gateway = gateway(
            faceResult = ApiResult.Success(FaceResultModel("https://face.url")),
            cleaner = cleaner,
        )

        gateway.clearLegacyFaceArtifacts(123)

        assertThat(cleaner.clearedUserIds).containsExactly(123)
    }

    private fun gateway(
        faceResult: ApiResult<FaceResultModel>,
        cleaner: RecordingFaceCacheCleaner = RecordingFaceCacheCleaner(),
    ): VerifyServicePersonDataGatewayImpl = VerifyServicePersonDataGatewayImpl(
        faceCacheCleaner = cleaner,
        identificationRepository = FakeIdentificationRepository(faceResult),
    )

    private class RecordingFaceCacheCleaner : FaceCacheCleaner {
        val clearedUserIds = mutableListOf<Int>()

        override suspend fun clearUserFaceBase64(userId: Int) {
            clearedUserIds += userId
        }
    }

    private class FakeIdentificationRepository(
        private val faceResult: ApiResult<FaceResultModel>,
    ) : IdentificationRepository {
        override suspend fun setFace(setFaceParamModel: SetFaceParamModel): ApiResult<Unit> =
            error("Unexpected call")

        override suspend fun getFace(): ApiResult<FaceResultModel> = faceResult

        override suspend fun checkFace(
            checkFaceParamModel: CheckFaceParamModel,
        ): ApiResult<Unit> = error("Unexpected call")
    }
}
