package com.ytone.longcare.navigation

import androidx.navigation.NavController
import com.ytone.longcare.model.OrderKey

fun NavController.navigateToService(orderKey: OrderKey) {
    navigateToService(orderKey.toOrderNavParams())
}

fun NavController.navigateToNursingExecution(orderKey: OrderKey) {
    navigateToNursingExecution(orderKey.toOrderNavParams())
}

fun NavController.navigateToNfcSignInForStartOrder(orderKey: OrderKey) {
    navigateToNfcSignInForStartOrder(orderKey.toOrderNavParams())
}

fun NavController.navigateToNfcSignInForEndOrder(orderKey: OrderKey, params: EndOderInfo) {
    navigateToNfcSignInForEndOrder(orderKey.toOrderNavParams(), params)
}

fun NavController.navigateToSelectService(orderKey: OrderKey) {
    navigateToSelectService(orderKey.toOrderNavParams())
}

fun NavController.navigateToPhotoUpload(orderKey: OrderKey) {
    navigateToPhotoUpload(orderKey.toOrderNavParams())
}

fun NavController.navigateToServiceCountdown(orderKey: OrderKey, projectIdList: List<Int> = emptyList()) {
    navigateToServiceCountdown(orderKey.toOrderNavParams(), projectIdList)
}

fun NavController.navigateToEndServiceSelection(orderKey: OrderKey, endType: Int, projectIdList: List<Int> = emptyList()) {
    navigateToEndServiceSelection(orderKey.toOrderNavParams(), endType, projectIdList)
}

fun NavController.navigateToServiceComplete(orderKey: OrderKey, serviceCompleteData: ServiceCompleteData) {
    navigateToServiceComplete(orderKey.toOrderNavParams(), serviceCompleteData)
}

fun NavController.navigateToFaceRecognitionGuide(orderKey: OrderKey) {
    navigateToFaceRecognitionGuide(orderKey.toOrderNavParams())
}

fun NavController.navigateToSelectDevice(orderKey: OrderKey) {
    navigateToSelectDevice(orderKey.toOrderNavParams())
}

fun NavController.navigateToIdentification(orderKey: OrderKey) {
    navigateToIdentification(orderKey.toOrderNavParams())
}

fun NavController.navigateToDefaultFaceVerification(orderKey: OrderKey) {
    navigateToDefaultFaceVerification(orderKey.toOrderNavParams())
}
