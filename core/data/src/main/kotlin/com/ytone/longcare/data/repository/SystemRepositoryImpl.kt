package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.system.SystemRepository
import com.ytone.longcare.model.AppVersionModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
) : SystemRepository {

    override suspend fun checkVersion(): ApiResult<AppVersionModel> =
        apiService.checkVersion()
}
