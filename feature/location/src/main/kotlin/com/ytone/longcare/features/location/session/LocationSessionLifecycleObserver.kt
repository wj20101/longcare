package com.ytone.longcare.features.location.session

import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.location.manager.LocationTrackingManager
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 将定位会话绑定到登录态，但绝不在登录或进程重建后恢复定位。
 *
 * 登出、Token 失效以及账号切换都会统一停止当前订单定位；首次读取到已登录用户时
 * 只记录身份，等待订单流程显式启动定位。
 */
@Singleton
class LocationSessionLifecycleObserver @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val userSessionRepository: UserSessionRepository,
    private val locationTrackingManager: LocationTrackingManager,
) {
    private val started = AtomicBoolean(false)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        applicationScope.launch {
            userSessionRepository.sessionState.collect(locationTrackingManager::onSessionStateChanged)
        }
    }
}
