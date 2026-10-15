package com.ytone.longcare.features.location.manager

import com.ytone.longcare.domain.location.LocationAccount
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.features.location.reporting.LocationReportingManager
import com.ytone.longcare.model.OrderKey
import javax.inject.Inject
import javax.inject.Singleton

/** 页面及登录态的适配入口；授权、会话与启停串行化统一由上报管理器负责。 */
@Singleton
class LocationTrackingManager @Inject constructor(
    private val locationReportingManager: LocationReportingManager,
) {
    val pendingRecovery = locationReportingManager.pendingRecovery
    val status = locationReportingManager.status
    fun onAppVisible() = locationReportingManager.onAppVisible()
    fun resumePendingRecovery() = locationReportingManager.resumePendingRecovery()
    fun startTracking(orderKey: OrderKey) = locationReportingManager.startReporting(orderKey)
    fun stopTracking() = locationReportingManager.stopReporting()

    fun onSessionStateChanged(state: SessionState) {
        when (state) {
            SessionState.Unknown -> locationReportingManager.pauseAuthorization()
            SessionState.LoggedOut -> locationReportingManager.setAccount(null)
            is SessionState.LoggedIn -> locationReportingManager.setAccount(
                LocationAccount(state.user.companyId, state.user.accountId, state.user.userId),
            )
        }
    }
}
