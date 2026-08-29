package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.domain.facecache.FaceCacheCleaner
import com.ytone.longcare.domain.profile.ProfileRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.NurseServiceTimeModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
    private val userSessionRepository: UserSessionRepository,
    private val faceCacheCleaner: FaceCacheCleaner,
) : ProfileRepository {
    override suspend fun getServiceStatistics(): ApiResult<NurseServiceTimeModel> =
        apiService.getServiceStatistics()

    override suspend fun logout(): ApiResult<Unit> {
        val userId = userSessionRepository.sessionState.value.user?.userId
        return try {
            apiService.logout()
        } finally {
            withContext(NonCancellable) {
                var cancellation: CancellationException? = null
                if (userId != null) {
                    try {
                        faceCacheCleaner.clearUserFaceArtifacts(userId)
                    } catch (error: CancellationException) {
                        cancellation = error
                    } catch (error: Exception) {
                        logE("Failed to clear face cache for current user", throwable = error)
                    }
                }
                try {
                    userSessionRepository.logout()
                } catch (error: CancellationException) {
                    cancellation?.addSuppressed(error)
                    if (cancellation == null) {
                        cancellation = error
                    }
                } catch (error: Exception) {
                    logE("Failed to complete local session logout", throwable = error)
                }
                cancellation?.let { throw it }
            }
        }
    }
}
