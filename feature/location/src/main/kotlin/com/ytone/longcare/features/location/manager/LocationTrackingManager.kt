package com.ytone.longcare.features.location.manager

import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationAccount
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.features.location.reporting.LocationReportingManager
import com.ytone.longcare.model.OrderKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 订单定位业务入口。
 *
 * 登录态变化和订单启停共享同一把锁，确保登出或账号切换完成后不能遗留定位上报。
 */
@Singleton
class LocationTrackingManager @Inject constructor(
    private val locationFacade: LocationFacade,
    private val locationReportingManager: LocationReportingManager,
) {
    val pendingRecovery = locationReportingManager.pendingRecovery
    val status = locationReportingManager.status
    fun onAppVisible() = locationReportingManager.onAppVisible()
    fun resumePendingRecovery() = locationReportingManager.resumePendingRecovery()
    private val lifecycleLock = Any()
    private var sessionIdentity: LocationAccount? = null

    /**
     * 启动位置上报业务。
     */
    fun startTracking(orderKey: OrderKey) {
        synchronized(lifecycleLock) {
            startTrackingLocked(orderKey)
        }
    }

    /** 权限授予与订单启动共享登录态锁，避免登出后重新启动定位引擎。 */
    fun startTrackingAfterPermissionGrant(orderKey: OrderKey) {
        synchronized(lifecycleLock) {
            if (!hasActiveSession()) return
            locationFacade.notifyPermissionGranted()
            locationReportingManager.startReporting(orderKey)
        }
    }

    /**
     * 停止位置上报业务。
     */
    fun stopTracking() {
        synchronized(lifecycleLock) { locationReportingManager.stopReporting() }
    }

    fun onSessionStateChanged(state: SessionState) {
        synchronized(lifecycleLock) {
            when (state) {
                SessionState.Unknown -> locationReportingManager.pauseAuthorization()
                SessionState.LoggedOut -> {
                    sessionIdentity = null
                    locationReportingManager.setAccount(null)
                }

                is SessionState.LoggedIn -> {
                    val nextIdentity = LocationAccount(state.user.companyId, state.user.accountId, state.user.userId)
                    sessionIdentity = nextIdentity
                    locationReportingManager.setAccount(nextIdentity)
                }
            }
        }
    }

    private fun startTrackingLocked(orderKey: OrderKey) {
        if (!hasActiveSession()) return
        locationReportingManager.startReporting(orderKey)
    }

    private fun hasActiveSession(): Boolean {
        if (sessionIdentity != null) return true
        logI("登录态未就绪，忽略定位上报启动")
        return false
    }

}
