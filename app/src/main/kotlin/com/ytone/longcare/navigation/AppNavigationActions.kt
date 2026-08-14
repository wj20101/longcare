package com.ytone.longcare.navigation

import androidx.navigation.NavController
import com.ytone.longcare.common.utils.safeNavigate
import com.ytone.longcare.features.userlist.ui.UserListType
import com.ytone.longcare.model.WatermarkData

fun NavController.navigateToHomeFromLogin() {
    navigate(HomeGraphRoute) {
        popUpTo(LoginRoute) { inclusive = true }
    }
}

fun NavController.navigateToService(orderParams: OrderNavParams) {
    navigate(ServiceRoute(orderParams))
}

fun NavController.navigateToNursingExecution(orderParams: OrderNavParams) {
    navigate(NursingExecutionRoute(orderParams))
}

fun NavController.navigateToNfcSignInForStartOrder(orderParams: OrderNavParams) {
    navigate(NfcSignInRoute(orderParams = orderParams, signInMode = SignInMode.START_ORDER))
}

fun NavController.navigateToNfcSignInForEndOrder(orderParams: OrderNavParams, params: EndOderInfo) {
    navigate(
        NfcSignInRoute(
            orderParams = orderParams,
            signInMode = SignInMode.END_ORDER,
            endOrderParams = params
        )
    )
}

fun NavController.navigateToCarePlansList() {
    navigate(CarePlansListRoute)
}

fun NavController.navigateToServiceRecordsList() {
    navigate(ServiceRecordsListRoute)
}

fun NavController.navigateToSelectService(orderParams: OrderNavParams) {
    navigate(SelectServiceRoute(orderParams))
}

fun NavController.navigateToPhotoUpload(orderParams: OrderNavParams) {
    navigate(PhotoUploadRoute(orderParams))
}

fun NavController.navigateToServiceCountdown(orderParams: OrderNavParams, projectIdList: List<Int> = emptyList()) {
    navigate(ServiceCountdownRoute(orderParams = orderParams, projectIdList = projectIdList))
}

fun NavController.navigateToEndServiceSelection(
    orderParams: OrderNavParams,
    endType: Int,
    projectIdList: List<Int> = emptyList()
) {
    safeNavigate(
        EndServiceSelectionRoute(
            orderParams = orderParams,
            endType = endType,
            initialProjectIdList = projectIdList
        )
    )
}

fun NavController.navigateToServiceComplete(
    orderParams: OrderNavParams,
    serviceCompleteData: ServiceCompleteData
) {
    navigate(ServiceCompleteRoute(orderParams = orderParams, serviceCompleteData = serviceCompleteData)) {
        popUpTo(HomeGraphRoute) { inclusive = false }
        launchSingleTop = true
    }
}

fun NavController.navigateToFaceRecognitionGuide(orderParams: OrderNavParams) {
    navigate(FaceRecognitionGuideRoute(orderParams = orderParams))
}

fun NavController.navigateToSelectDevice(orderParams: OrderNavParams) {
    navigateToNfcSignInForStartOrder(orderParams)
}

fun NavController.navigateToIdentification(orderParams: OrderNavParams) {
    val currentRoute = this.currentBackStackEntry?.destination?.route ?: return
    navigate(IdentificationRoute(orderParams)) {
        popUpTo(currentRoute) { inclusive = true }
        launchSingleTop = true
    }
}

fun NavController.navigateToDefaultFaceVerification(orderParams: OrderNavParams) {
    navigate(DefaultFaceVerificationRoute(orderParams = orderParams))
}

fun NavController.navigateToUserList(listType: String) {
    navigate(UserListRoute(listType))
}

fun NavController.navigateToHaveServiceUserList() {
    navigateToUserList(UserListType.HAVE_SERVICE.name)
}

fun NavController.navigateToNoServiceUserList() {
    navigateToUserList(UserListType.NO_SERVICE.name)
}

fun NavController.navigateToHomeAndClearStack() {
    safeNavigate(HomeGraphRoute) {
        popUpTo(0) { inclusive = false }
        launchSingleTop = true
    }
}

fun NavController.navigateToUserServiceRecord(userId: Long, userName: String, userAddress: String) {
    navigate(UserServiceRecordRoute(userId, userName, userAddress))
}

fun NavController.navigateToCamera(watermarkData: WatermarkData) {
    navigate(CameraRoute(watermarkData))
}

fun NavController.navigateToFaceVerificationWithAutoSign() {
    navigate(TxFaceRoute)
}

fun NavController.navigateToManualFaceCapture() {
    navigate(ManualFaceCaptureRoute)
}

fun NavController.navigateToWebView(url: String, title: String) {
    navigate(WebViewRoute(url, title))
}
