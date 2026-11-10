package com.ytone.longcare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.network.ApiRequestException
import com.ytone.longcare.common.utils.DeviceUtils
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.system.SystemRepository
import com.ytone.longcare.model.AppVersionModel
import com.ytone.longcare.model.result.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** MainActivity 的会话状态，以及本次启动的更新检测和弹窗结果。 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
    private val systemRepository: SystemRepository,
    private val deviceUtils: DeviceUtils,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = userSessionRepository.sessionState

    private val _appVersionModel = MutableStateFlow<AppVersionModel?>(null)
    val appVersionModel = _appVersionModel.asStateFlow()
    private var startupCheckStarted = false

    /** 隐私同意后的应用入口调用；重组、页面切换及前后台切换均不重新检查。 */
    fun checkForUpdateOnStartup() {
        if (startupCheckStarted) return
        startupCheckStarted = true
        viewModelScope.launch {
            val result = try {
                systemRepository.checkVersion()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ApiResult.Exception(error)
            }
            currentCoroutineContext().ensureActive()
            when (result) {
                is ApiResult.Success -> {
                    _appVersionModel.value = result.data?.takeIf {
                        it.platform.equals("android", ignoreCase = true) &&
                            it.versionCode.toLong() > deviceUtils.getAppVersionCode()
                    }
                }
                is ApiResult.Failure -> {
                    DiagnosticEventTracker.trackError(
                        category = DiagnosticCategory.APP_UPDATE,
                        event = "version_check_failure",
                        description = "版本检查业务失败",
                        extras = mapOf("failureCode" to result.code, "failureMessage" to result.message),
                    )
                }
                is ApiResult.Exception -> {
                    val requestError = result.exception as? ApiRequestException
                    DiagnosticEventTracker.trackError(
                        category = DiagnosticCategory.APP_UPDATE,
                        event = "version_check_exception",
                        description = "版本检查接口异常",
                        throwable = result.exception,
                        extras = mapOf(
                            "attempt" to 1,
                            "errorKind" to (requestError?.kind ?: ApiRequestException.Kind.UNKNOWN).name,
                            "httpCode" to requestError?.httpCode,
                            "willRetry" to false,
                        ),
                    )
                }
            }
        }
    }

    fun clearAppVersionModel() {
        _appVersionModel.value = null
    }
}
