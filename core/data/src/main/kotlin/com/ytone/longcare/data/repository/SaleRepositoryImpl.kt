package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.model.AddUserLatentRequestDto
import com.ytone.longcare.api.model.AddUserLatentResponseDto
import com.ytone.longcare.api.model.CheckTokenDto
import com.ytone.longcare.api.model.GetCheckTokenRequestDto
import com.ytone.longcare.api.model.SearchUserLatentRequestDto
import com.ytone.longcare.api.model.ToDoCountDto
import com.ytone.longcare.api.model.ToDoItemDto
import com.ytone.longcare.api.model.UserLatentDetailDto
import com.ytone.longcare.api.model.UserLatentListDto
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.sale.SaleRepository
import com.ytone.longcare.model.AddUserLatentParamModel
import com.ytone.longcare.model.AddUserLatentResultModel
import com.ytone.longcare.model.CheckTokenModel
import com.ytone.longcare.model.SearchUserLatentParamModel
import com.ytone.longcare.model.ToDoNumResultModel
import com.ytone.longcare.model.ToDoResultModel
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.model.UserLatentListModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaleRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
) : SaleRepository {

    override suspend fun getCheckToken(
        customerId: Int,
        checkDeviceId: String,
    ): ApiResult<CheckTokenModel> =
        apiService.getCheckToken(
            GetCheckTokenRequestDto(
                id = customerId,
                checkDeviceId = checkDeviceId,
            )
        ).mapData(CheckTokenDto::toModel)

    override suspend fun addUserLatent(
        request: AddUserLatentParamModel,
    ): ApiResult<AddUserLatentResultModel> =
        apiService.addUserLatent(request.toDto()).mapData(AddUserLatentResponseDto::toModel)

    override suspend fun getRecentUserLatentList(): ApiResult<List<UserLatentListModel>> =
        apiService.getRecentUserLatentList().mapData { items -> items.map(UserLatentListDto::toModel) }

    override suspend fun getToDoCount(): ApiResult<ToDoNumResultModel> =
        apiService.getToDoCount().mapData(ToDoCountDto::toModel)

    override suspend fun getToDoList(): ApiResult<List<ToDoResultModel>> =
        apiService.getToDoList().mapData { items -> items.map(ToDoItemDto::toModel) }

    override suspend fun searchUserLatentList(
        request: SearchUserLatentParamModel,
    ): ApiResult<List<UserLatentListModel>> =
        apiService.searchUserLatentList(request.toDto())
            .mapData { items -> items.map(UserLatentListDto::toModel) }

    override suspend fun getUserLatentDetail(
        customerId: Int,
    ): ApiResult<UserLatentDetailModel> =
        apiService.getUserLatentDetail(customerId).mapData(UserLatentDetailDto::toModel)
}

private inline fun <T, R> ApiResult<T>.mapData(transform: (T) -> R): ApiResult<R> =
    when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(data))
        is ApiResult.Failure -> this
        is ApiResult.Exception -> this
    }

private fun AddUserLatentParamModel.toDto(): AddUserLatentRequestDto =
    AddUserLatentRequestDto(
        userName = userName,
        identityCardNumber = identityCardNumber,
        guardianName = guardianName,
        guardianPhone = guardianPhone,
        guardianRelation = guardianRelation,
        liveAddress = liveAddress,
        liveLng = liveLng,
        liveLat = liveLat,
        img1 = img1,
        img2 = img2,
        img3 = img3,
    )

private fun SearchUserLatentParamModel.toDto(): SearchUserLatentRequestDto =
    SearchUserLatentRequestDto(
        pageIndex = pageIndex,
        userName = userName,
        checkState = checkState,
    )

private fun CheckTokenDto.toModel(): CheckTokenModel =
    CheckTokenModel(
        token = token.orEmpty(),
        tokenType = tokenType,
        expireAt = expireAt,
        bizType = bizType,
    )

private fun AddUserLatentResponseDto.toModel(): AddUserLatentResultModel =
    AddUserLatentResultModel(id = id, pgUrl = pgUrl.orEmpty())

private fun UserLatentListDto.toModel(): UserLatentListModel =
    UserLatentListModel(
        id = id,
        userName = userName.orEmpty(),
        checkState = checkState,
        liveAddress = liveAddress.orEmpty(),
        identityCardNumber = identityCardNumber.orEmpty(),
    )

private fun ToDoCountDto.toModel(): ToDoNumResultModel = ToDoNumResultModel(num = num)

private fun ToDoItemDto.toModel(): ToDoResultModel =
    ToDoResultModel(title = title, content = content, createTime = createTime)

private fun UserLatentDetailDto.toModel(): UserLatentDetailModel =
    UserLatentDetailModel(
        id = id,
        userName = userName,
        identityCardNumber = identityCardNumber,
        guardianName = guardianName,
        guardianPhone = guardianPhone,
        guardianRelation = guardianRelation,
        liveAddress = liveAddress,
        liveLng = liveLng,
        liveLat = liveLat,
        img1 = img1,
        img2 = img2,
        img3 = img3,
        checkStatus = checkStatus,
        checkTime = checkTime,
        checkDesc = checkDesc,
        createTime = createTime,
        pgId = pgId,
        pgResult = pgResult,
        pgScore = pgScore,
        pgUrl = pgUrl,
    )
