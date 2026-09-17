package com.ytone.longcare.navigation

import com.ytone.longcare.features.userlist.ui.UserListType
import com.ytone.longcare.model.WatermarkData

fun AppNavigator.navigateToHomeFromLogin() {
    resetToHome()
}

fun AppNavigator.navigateToService(orderParams: OrderNavParams) {
    navigate(ServiceRoute(orderParams))
}

fun AppNavigator.navigateToNursingExecution(orderParams: OrderNavParams) {
    navigate(NursingExecutionRoute(orderParams))
}

fun AppNavigator.navigateToNfcSignInForStartOrder(orderParams: OrderNavParams) {
    navigate(NfcSignInRoute(orderParams = orderParams, signInMode = SignInMode.START_ORDER))
}

fun AppNavigator.navigateToNfcSignInForEndOrder(orderParams: OrderNavParams, params: EndOderInfo) {
    navigate(
        NfcSignInRoute(
            orderParams = orderParams,
            signInMode = SignInMode.END_ORDER,
            endOrderParams = params
        )
    )
}

fun AppNavigator.navigateToCarePlansList() {
    navigate(CarePlansListRoute)
}

fun AppNavigator.navigateToServiceRecordsList() {
    navigate(ServiceRecordsListRoute)
}

fun AppNavigator.navigateToSelectService(orderParams: OrderNavParams) {
    navigate(SelectServiceRoute(orderParams))
}

fun AppNavigator.navigateToPhotoUpload(orderParams: OrderNavParams) {
    navigate(PhotoUploadRoute(orderParams))
}

fun AppNavigator.navigateToServiceCountdown(orderParams: OrderNavParams, projectIdList: List<Int> = emptyList()) {
    navigate(ServiceCountdownRoute(orderParams = orderParams, projectIdList = projectIdList))
}

fun AppNavigator.navigateToEndServiceSelection(
    orderParams: OrderNavParams,
    endType: Int,
    projectIdList: List<Int> = emptyList()
) {
    navigateWhenResumed(
        EndServiceSelectionRoute(
            orderParams = orderParams,
            endType = endType,
            initialProjectIdList = projectIdList
        )
    )
}

fun AppNavigator.navigateToServiceComplete(
    orderParams: OrderNavParams,
    serviceCompleteData: ServiceCompleteData
) {
    completeService(ServiceCompleteRoute(orderParams = orderParams, serviceCompleteData = serviceCompleteData))
}

fun AppNavigator.navigateToIdentification(orderParams: OrderNavParams) {
    replaceTop(IdentificationRoute(orderParams))
}

fun AppNavigator.navigateToDefaultFaceVerification(orderParams: OrderNavParams) {
    navigate(DefaultFaceVerificationRoute(orderParams = orderParams))
}

fun AppNavigator.navigateToUserList(listType: String) {
    navigate(UserListRoute(listType))
}

fun AppNavigator.navigateToHaveServiceUserList() {
    navigateToUserList(UserListType.HAVE_SERVICE.name)
}

fun AppNavigator.navigateToNoServiceUserList() {
    navigateToUserList(UserListType.NO_SERVICE.name)
}

fun AppNavigator.navigateToHomeAndClearStack() {
    resetToHome()
}

fun AppNavigator.navigateToUserServiceRecord(userId: Long, userName: String, userAddress: String) {
    navigate(UserServiceRecordRoute(userId, userName, userAddress))
}

fun AppNavigator.navigateToCamera(watermarkData: WatermarkData) {
    navigate(CameraRoute(watermarkData))
}

fun AppNavigator.navigateToManualFaceCapture() {
    navigate(ManualFaceCaptureRoute)
}

fun AppNavigator.navigateToWebView(url: String, title: String) {
    navigate(WebViewRoute(url, title))
}
