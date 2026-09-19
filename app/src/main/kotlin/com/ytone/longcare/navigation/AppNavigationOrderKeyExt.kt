package com.ytone.longcare.navigation

import com.ytone.longcare.model.OrderKey

fun AppNavigator.navigateToService(orderKey: OrderKey) {
    navigateToService(orderKey.toOrderNavParams())
}

fun AppNavigator.navigateToNursingExecution(orderKey: OrderKey) {
    navigateToNursingExecution(orderKey.toOrderNavParams())
}

fun AppNavigator.navigateToNfcSignInForStartOrder(orderKey: OrderKey) {
    navigateToNfcSignInForStartOrder(orderKey.toOrderNavParams())
}

fun AppNavigator.navigateToNfcSignInForEndOrder(orderKey: OrderKey, params: EndOderInfo) {
    navigateToNfcSignInForEndOrder(orderKey.toOrderNavParams(), params)
}

fun AppNavigator.navigateToSelectService(orderKey: OrderKey) {
    navigateToSelectService(orderKey.toOrderNavParams())
}

fun AppNavigator.navigateToPhotoUpload(orderKey: OrderKey) {
    navigateToPhotoUpload(orderKey.toOrderNavParams())
}

fun AppNavigator.navigateToServiceCountdown(orderKey: OrderKey, projectIdList: List<Int> = emptyList()) {
    navigateToServiceCountdown(orderKey.toOrderNavParams(), projectIdList)
}

fun AppNavigator.navigateToEndServiceSelection(orderKey: OrderKey, endType: Int, projectIdList: List<Int> = emptyList()) {
    navigateToEndServiceSelection(orderKey.toOrderNavParams(), endType, projectIdList)
}

fun AppNavigator.navigateToServiceComplete(orderKey: OrderKey, serviceCompleteData: ServiceCompleteData) {
    navigateToServiceComplete(orderKey.toOrderNavParams(), serviceCompleteData)
}

fun AppNavigator.navigateToIdentification(orderKey: OrderKey) {
    navigateToIdentification(orderKey.toOrderNavParams())
}

fun AppNavigator.navigateToDefaultFaceVerification(orderKey: OrderKey) {
    navigateToDefaultFaceVerification(orderKey.toOrderNavParams())
}
