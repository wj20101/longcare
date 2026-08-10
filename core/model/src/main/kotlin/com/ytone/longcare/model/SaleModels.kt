package com.ytone.longcare.model

/**
 * 可注入俏郎中 SDK 的一次性检测 Token。
 */
data class CheckTokenModel(
    val token: String = "",
    val tokenType: Int = 0,
    val expireAt: Long = 0L,
    val bizType: Int = 0,
)

/**
 * 新增潜在客户的请求参数。
 */
data class AddUserLatentParamModel(
    val userName: String = "",
    val identityCardNumber: String = "",
    val guardianName: String = "",
    val guardianPhone: String = "",
    val guardianRelation: String = "",
    val liveAddress: String = "",
    val liveLng: String = "",
    val liveLat: String = "",
    val img1: String = "",
    val img2: String = "",
    val img3: String = "",
)

/**
 * 新增潜在客户后的标识与评估页地址。
 */
data class AddUserLatentResultModel(
    val id: Int = 0,
    val pgUrl: String = "",
)

/**
 * 搜索潜在客户的筛选条件。
 */
data class SearchUserLatentParamModel(
    val pageIndex: Int = 1,
    val userName: String = "",
    val checkState: Int = UserLatentCheckState.ALL,
)

/**
 * 潜在客户列表项。
 */
data class UserLatentListModel(
    val id: Int = 0,
    val userName: String = "",
    val checkState: Int = UserLatentCheckState.NOT_SUBMITTED,
    val liveAddress: String = "",
    val identityCardNumber: String = "",
)

/**
 * 当前账号的待办事项数量。
 */
data class ToDoNumResultModel(
    val num: Int = 0,
)

/**
 * 待办事项列表项。
 *
 * 接口文档将三个字段均声明为 nullable，因此这里保留可空类型，避免服务端返回
 * 显式 null 时导致整页解析失败。
 */
data class ToDoResultModel(
    val title: String? = null,
    val content: String? = null,
    val createTime: String? = null,
)

/**
 * 潜在客户详情。
 */
data class UserLatentDetailModel(
    val id: Int = 0,
    val userName: String? = null,
    val identityCardNumber: String? = null,
    val guardianName: String? = null,
    val guardianPhone: String? = null,
    val guardianRelation: String? = null,
    val liveAddress: String? = null,
    val liveLng: String? = null,
    val liveLat: String? = null,
    val img1: String? = null,
    val img2: String? = null,
    val img3: String? = null,
    val checkStatus: Int = UserLatentCheckState.NOT_SUBMITTED,
    val checkTime: String? = null,
    val checkDesc: String? = null,
    val createTime: String? = null,
    val pgId: Int = 0,
    val pgResult: String? = null,
    val pgScore: Int = 0,
    val pgUrl: String? = null,
)

object UserLatentCheckState {
    const val ALL = -1
    const val NOT_SUBMITTED = 0
    const val PENDING_REVIEW = 1
    const val APPROVED = 2
    const val REJECTED = 3
}
