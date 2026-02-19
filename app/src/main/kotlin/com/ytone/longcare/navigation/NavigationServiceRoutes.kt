package com.ytone.longcare.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class SignInMode {
    START_ORDER,
    END_ORDER
}

@Keep
@Serializable
data class NfcSignInRoute(
    val orderParams: OrderNavParams,
    val signInMode: SignInMode,
    val endOrderParams: EndOderInfo? = null
)

@Keep
@Serializable
data class ServiceCountdownRoute(
    val orderParams: OrderNavParams,
    val projectIdList: List<Int> = emptyList()
)

@Keep
@Serializable
data class EndOderInfo(
    val projectIdList: List<Int> = emptyList(),
    val beginImgList: List<String> = emptyList(),
    val centerImgList: List<String> = emptyList(),
    val endImgList: List<String> = emptyList(),
    val endType: Int = 1
)

@Keep
@Serializable
data class ServiceCompleteRoute(
    val orderParams: OrderNavParams,
    val serviceCompleteData: ServiceCompleteData
)

@Keep
@Serializable
data class ServiceCompleteData(
    val clientName: String = "",
    val clientAge: Int = 0,
    val clientIdNumber: String = "",
    val clientAddress: String = "",
    val serviceContent: String = "",
    val trueServiceTime: Int = 0
)

@Keep
@Serializable
data class EndServiceSelectionRoute(
    val orderParams: OrderNavParams,
    val endType: Int,
    val initialProjectIdList: List<Int> = emptyList()
)
