# Huawei Compliance And Location Quality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix Huawei privacy/permission rejection while preserving service execution and reporting degraded location quality to Bugly.

**Architecture:** Keep the privacy gate in the login feature so no pre-consent network request reaches the interceptor that may read ANDROID ID. Remove home-entry permission requests and move camera/location permission prompts to user-triggered flows. Add a small location quality layer in the location feature, then let NFC/service-start callers continue with warnings and Bugly tracking when location is missing or degraded.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Hilt, StateFlow, AMap location SDK, Bugly `CrashReport`, JUnit4, MockK, kotlinx-coroutines-test.

---

## File Structure

- `feature/login/src/main/kotlin/com/ytone/longcare/features/login/vm/LoginViewModel.kt`
  Purpose: owns login API calls. It must no longer call startup config from `init`; it exposes explicit privacy-confirmation state and loads startup config only after consent.

- `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt`
  Purpose: owns login screen-local agreement checkbox state. It calls the ViewModel privacy-confirmation method only after the user checks or confirms agreement.

- `app/src/test/kotlin/com/ytone/longcare/features/login/vm/LoginViewModelPrivacyGateTest.kt`
  Purpose: verifies startup config is not requested at construction and starts only after explicit privacy confirmation.

- `app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt`
  Purpose: removes automatic runtime permission request from home entry while preserving home-entry reporting and compatibility dialogs.

- `app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreenPermissions.kt`
  Purpose: delete if unused after removing home-entry runtime permission checks.

- `app/src/test/kotlin/com/ytone/longcare/features/home/ui/HomeScreenPermissionPolicyTest.kt`
  Purpose: static policy test preventing future reintroduction of home-entry camera/location permission launch.

- `core/ui/src/main/kotlin/com/ytone/longcare/common/utils/UnifiedPermissionHelper.kt`
  Purpose: adds reusable permission purpose text and keeps actual system permission launch controlled by feature screens.

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreenHandlers.kt`
  Purpose: shows location purpose notice before system location permission and surfaces degraded-location warnings.

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegate.kt`
  Purpose: requests critical location and returns a typed result instead of silently converting failure to blank coordinates.

- `core/ui/src/main/kotlin/com/ytone/longcare/shared/vm/SharedOrderDetailViewModel.kt`
  Purpose: handles start-service critical location degradation and exposes a warning message without blocking the service start.

- `core/model/src/main/kotlin/com/ytone/longcare/model/LocationResult.kt`
  Purpose: adds `timestampMillis` so location freshness can be evaluated explicitly.

- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/quality/LocationQualityEvaluator.kt`
  Purpose: validates coordinates, age, and accuracy for critical location actions.

- `core/domain/src/main/kotlin/com/ytone/longcare/domain/location/CriticalLocationResult.kt`
  Purpose: represents reliable, degraded, and missing critical-location outcomes.

- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationStateManager.kt`
  Purpose: exposes cached location with timestamp for quality checks.

- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacade.kt`
  Purpose: adds fresh-location-first critical retrieval and keeps existing `getCurrentLocation()` behavior for non-critical callers.

- `feature/location/src/main/kotlin/com/ytone/longcare/features/location/tracker/LocationEventTracker.kt`
  Purpose: adds critical-location degradation event types that are posted to Bugly.

- `core/domain/src/main/kotlin/com/ytone/longcare/domain/location/LocationFacade.kt`
  Purpose: exposes a critical-location API while preserving existing methods.

- `app/src/test/kotlin/com/ytone/longcare/features/location/quality/LocationQualityEvaluatorTest.kt`
  Purpose: unit-tests valid, zero-coordinate, out-of-range, stale, and low-accuracy cases.

- `app/src/test/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacadeCriticalLocationTest.kt`
  Purpose: verifies fresh-first behavior, cache fallback, and degraded/missing outcomes.

- `app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsComponents.kt`
  Purpose: hides unimplemented Profile options.

- `app/src/test/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsVisibilityTest.kt`
  Purpose: static policy test preventing empty Profile options from being shown again.

## Task 1: Gate Login Startup Config Behind Privacy Agreement

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/feature/login/src/main/kotlin/com/ytone/longcare/features/login/vm/LoginViewModel.kt`
- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt`
- Create: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/login/vm/LoginViewModelPrivacyGateTest.kt`

- [ ] **Step 1: Write the failing ViewModel privacy gate test**

Create `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/login/vm/LoginViewModelPrivacyGateTest.kt`:

```kotlin
package com.ytone.longcare.features.login.vm

import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.LoginPreferencesManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.StartConfigResultModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelPrivacyGateTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init does not request startup config before privacy confirmation`() = runTest(dispatcher) {
        val repository = mockk<LoginRepository>(relaxed = true)

        createViewModel(repository)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.getStartConfig() }
    }

    @Test
    fun `privacy confirmation loads startup config once`() = runTest(dispatcher) {
        val repository = mockk<LoginRepository>(relaxed = true)
        coEvery { repository.getStartConfig() } returns ApiResult.Success(
            StartConfigResultModel(
                userXieYiUrl = "https://example.com/user",
                yinSiXieYiUrl = "https://example.com/privacy",
            )
        )

        val viewModel = createViewModel(repository)

        viewModel.onPrivacyAgreementConfirmed()
        viewModel.onPrivacyAgreementConfirmed()
        advanceUntilIdle()

        assertTrue(viewModel.startConfigState.value is StartConfigUiState.Success)
        coVerify(exactly = 1) { repository.getStartConfig() }
    }

    private fun createViewModel(repository: LoginRepository): LoginViewModel {
        val preferences = mockk<LoginPreferencesManager>()
        every { preferences.getLastLoginPhoneNumber() } returns ""
        every { preferences.saveLastLoginPhoneNumber(any()) } returns Unit

        return LoginViewModel(
            loginRepository = repository,
            userSessionRepository = mockk<UserSessionRepository>(relaxed = true),
            toastHelper = mockk<ToastHelper>(relaxed = true),
            loginPreferencesManager = preferences,
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.login.vm.LoginViewModelPrivacyGateTest"
```

Expected: FAIL because `LoginViewModel` still calls `loadStartConfig()` in `init` and does not define `onPrivacyAgreementConfirmed()`.

- [ ] **Step 3: Implement the privacy gate in `LoginViewModel`**

Modify `/Users/wajie/StudioProjects/longcare/feature/login/src/main/kotlin/com/ytone/longcare/features/login/vm/LoginViewModel.kt`:

```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginRepository: LoginRepository,
    private val userSessionRepository: UserSessionRepository,
    private val toastHelper: ToastHelper,
    private val loginPreferencesManager: LoginPreferencesManager
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState

    private val _sendSmsCodeState = MutableStateFlow<SendSmsCodeUiState>(SendSmsCodeUiState.Idle)
    val sendSmsCodeState: StateFlow<SendSmsCodeUiState> = _sendSmsCodeState

    private val _countdownSeconds = MutableStateFlow(0)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds

    private val _startConfigState = MutableStateFlow<StartConfigUiState>(StartConfigUiState.Idle)
    val startConfigState: StateFlow<StartConfigUiState> = _startConfigState

    private var countdownJob: Job? = null
    private var nfcEventJob: Job? = null
    private var privacyAgreementConfirmed = false
    private var startConfigRequested = false

    fun onPrivacyAgreementConfirmed() {
        privacyAgreementConfirmed = true
        if (!startConfigRequested) {
            startConfigRequested = true
            loadStartConfig()
        }
    }

    private fun loadStartConfig() {
        viewModelScope.launch {
            _startConfigState.value = StartConfigUiState.Loading
            when (val result = loginRepository.getStartConfig()) {
                is ApiResult.Success -> _startConfigState.value = StartConfigUiState.Success(result.data)
                is ApiResult.Failure -> _startConfigState.value = StartConfigUiState.Error(result.message)
                is ApiResult.Exception -> {
                    _startConfigState.value = StartConfigUiState.Error(result.exception.message ?: "网络异常")
                }
            }
        }
    }

    fun sendSmsCode(mobile: String) {
        if (!privacyAgreementConfirmed) {
            showShortToast("请先阅读并同意用户协议和隐私政策")
            return
        }
        if (!isValidMobileNumber(mobile)) {
            showShortToast("请输入有效的11位手机号")
            return
        }
        viewModelScope.launch {
            _sendSmsCodeState.value = SendSmsCodeUiState.Loading
            when (val result = loginRepository.sendSmsCode(mobile)) {
                is ApiResult.Success -> {
                    _sendSmsCodeState.value = SendSmsCodeUiState.Success
                    showShortToast("验证码已发送")
                    startCountdown()
                }
                is ApiResult.Failure -> {
                    val errorMessage = "发送失败: ${result.message}"
                    _sendSmsCodeState.value = SendSmsCodeUiState.Error(errorMessage)
                    showShortToast(errorMessage)
                }
                is ApiResult.Exception -> {
                    val exceptionMessage = result.exception.message ?: "网络异常"
                    _sendSmsCodeState.value = SendSmsCodeUiState.Error(exceptionMessage)
                    showShortToast(exceptionMessage)
                }
            }
        }
    }

    fun login(mobile: String, code: String) {
        if (!privacyAgreementConfirmed) {
            showShortToast("请先阅读并同意用户协议和隐私政策")
            return
        }
        if (!isValidMobileNumber(mobile) || code.isBlank()) {
            showShortToast("手机号或验证码格式不正确")
            return
        }
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            when (val result = loginRepository.login(mobile, code)) {
                is ApiResult.Success -> {
                    val loginResult = result.data
                    val user = loginResult.toUser()
                    userSessionRepository.login(user)
                    loginPreferencesManager.saveLastLoginPhoneNumber(mobile)
                    _loginState.value = LoginUiState.Success(user)
                    showShortToast("登录成功")
                }
                is ApiResult.Failure -> {
                    val errorMessage = "登录失败: ${result.message}"
                    _loginState.value = LoginUiState.Error(errorMessage)
                    showShortToast(errorMessage)
                }
                is ApiResult.Exception -> {
                    val exceptionMessage = result.exception.message ?: "网络异常"
                    _loginState.value = LoginUiState.Error(exceptionMessage)
                    showShortToast(exceptionMessage)
                }
            }
        }
    }
}
```

Keep existing helper methods and state classes below the shown area. Remove the old `init { loadStartConfig() }` block.

- [ ] **Step 4: Trigger privacy confirmation from `LoginScreen`**

Modify `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt` so `LoginScreenContent` receives a callback:

```kotlin
LoginScreenContent(
    actions = actions,
    loginState = loginState,
    sendSmsState = sendSmsState,
    startConfigState = startConfigState,
    countdownSeconds = countdownSeconds,
    initialPhoneNumber = remember { viewModel.getLastLoginPhoneNumber() },
    onPrivacyAgreementConfirmed = viewModel::onPrivacyAgreementConfirmed,
    onSendCodeClick = { phoneNumber -> viewModel.sendSmsCode(phoneNumber) },
    onLoginClick = { phoneNumber, code -> viewModel.login(phoneNumber, code) }
)
```

Update `LoginScreenContent` signature:

```kotlin
fun LoginScreenContent(
    actions: LoginFeatureActions,
    loginState: LoginUiState,
    sendSmsState: SendSmsCodeUiState,
    startConfigState: StartConfigUiState,
    countdownSeconds: Int,
    initialPhoneNumber: String = "",
    onPrivacyAgreementConfirmed: () -> Unit = {},
    onSendCodeClick: (String) -> Unit,
    onLoginClick: (String, String) -> Unit
)
```

Add this effect after `agreementChecked` is declared:

```kotlin
LaunchedEffect(agreementChecked) {
    if (agreementChecked) {
        onPrivacyAgreementConfirmed()
    }
}
```

Keep the existing dialog confirm path setting `agreementChecked = true`; the effect will load startup config.

- [ ] **Step 5: Run the privacy gate test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.login.vm.LoginViewModelPrivacyGateTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  /Users/wajie/StudioProjects/longcare/feature/login/src/main/kotlin/com/ytone/longcare/features/login/vm/LoginViewModel.kt \
  /Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt \
  /Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/login/vm/LoginViewModelPrivacyGateTest.kt
git commit -m "fix(login): gate startup requests behind privacy agreement"
```

## Task 2: Remove Home-Entry Runtime Permission Requests

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt`
- Delete if unused: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreenPermissions.kt`
- Create: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/home/ui/HomeScreenPermissionPolicyTest.kt`

- [ ] **Step 1: Write the failing static policy test**

Create `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/home/ui/HomeScreenPermissionPolicyTest.kt`:

```kotlin
package com.ytone.longcare.features.home.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeScreenPermissionPolicyTest {

    @Test
    fun `home screen does not auto request camera or location permissions on entry`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt"
        ).readText()

        assertFalse(source.contains("buildRequiredPermissions()"))
        assertFalse(source.contains("ActivityResultContracts.RequestMultiplePermissions"))
        assertFalse(source.contains("Manifest.permission.CAMERA"))
        assertFalse(source.contains("Manifest.permission.ACCESS_FINE_LOCATION"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.home.ui.HomeScreenPermissionPolicyTest"
```

Expected: FAIL because `HomeScreen.kt` still contains the home-entry permission launcher.

- [ ] **Step 3: Remove the home-entry permission launcher**

Modify `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt`:

Remove these imports if present:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
```

Remove this state:

```kotlin
var showPermissionDialog by remember { mutableStateOf(false) }
var permissionDeniedMessage by remember { mutableStateOf("") }
```

Remove this launcher:

```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    val deniedPermissions = permissions.filterValues { !it }.keys
    if (deniedPermissions.isNotEmpty()) {
        val deniedPermissionNames = deniedPermissions.map { permission ->
            when (permission) {
                Manifest.permission.ACCESS_FINE_LOCATION -> "精确定位"
                Manifest.permission.CAMERA -> "拍照"
                Manifest.permission.POST_NOTIFICATIONS -> "通知提醒"
                else -> permission
            }
        }
        permissionDeniedMessage = "应用需要以下权限才能正常工作：${deniedPermissionNames.joinToString("、")}"
        showPermissionDialog = true
    }
}
```

Replace the first `LaunchedEffect(Unit)` body with:

```kotlin
LaunchedEffect(Unit) {
    homeSharedViewModel.reportHomeEntry()
    refreshCompatibilityGuides()
}
```

In the existing `HomeScreenPermissionDialogs(...)` call, replace only the first four permission-dialog arguments with these fixed values and leave the popup and battery arguments as they already are:

```kotlin
showPermissionDialog = false,
permissionDeniedMessage = "",
onDismissPermissionDialog = {},
onRetryPermissionRequest = {},
```

- [ ] **Step 4: Delete the old permission list if it is unused**

Run:

```bash
rg -n "buildRequiredPermissions" /Users/wajie/StudioProjects/longcare/app/src/main/kotlin /Users/wajie/StudioProjects/longcare/app/src/test
```

Expected after Step 3: only `HomeScreenPermissions.kt` remains. If so, delete `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreenPermissions.kt`.

- [ ] **Step 5: Run the policy test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.home.ui.HomeScreenPermissionPolicyTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  /Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt \
  /Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/home/ui/HomeScreenPermissionPolicyTest.kt
git add -u /Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreenPermissions.kt
git commit -m "fix(home): stop requesting permissions on entry"
```

## Task 3: Add Location Quality Evaluation

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/core/model/src/main/kotlin/com/ytone/longcare/model/LocationResult.kt`
- Create: `/Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/quality/LocationQualityEvaluator.kt`
- Create: `/Users/wajie/StudioProjects/longcare/core/domain/src/main/kotlin/com/ytone/longcare/domain/location/CriticalLocationResult.kt`
- Create: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/location/quality/LocationQualityEvaluatorTest.kt`

- [ ] **Step 1: Write the failing evaluator test**

Create `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/location/quality/LocationQualityEvaluatorTest.kt`:

```kotlin
package com.ytone.longcare.features.location.quality

import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.domain.location.LocationQualityIssue
import com.ytone.longcare.model.LocationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationQualityEvaluatorTest {

    private val now = 1_000_000L
    private val evaluator = LocationQualityEvaluator(
        nowProvider = { now },
        maxAgeMs = 10_000L,
        maxAccuracyMeters = 100f,
    )

    @Test
    fun `fresh accurate coordinate is reliable`() {
        val result = evaluator.evaluate(
            LocationResult(
                latitude = 31.2,
                longitude = 121.5,
                provider = "amap_continuous",
                accuracy = 20f,
                timestampMillis = now - 1_000L,
            )
        )

        assertTrue(result is LocationQuality.Reliable)
    }

    @Test
    fun `zero coordinate is invalid`() {
        val result = evaluator.evaluate(
            LocationResult(0.0, 0.0, "amap_continuous", 20f, now)
        )

        assertEquals(LocationQualityIssue.INVALID_COORDINATE, (result as LocationQuality.Degraded).issue)
    }

    @Test
    fun `out of range coordinate is invalid`() {
        val result = evaluator.evaluate(
            LocationResult(91.0, 181.0, "amap_continuous", 20f, now)
        )

        assertEquals(LocationQualityIssue.INVALID_COORDINATE, (result as LocationQuality.Degraded).issue)
    }

    @Test
    fun `old coordinate is stale`() {
        val result = evaluator.evaluate(
            LocationResult(31.2, 121.5, "amap_continuous", 20f, now - 20_000L)
        )

        assertEquals(LocationQualityIssue.STALE, (result as LocationQuality.Degraded).issue)
    }

    @Test
    fun `low accuracy coordinate is degraded`() {
        val result = evaluator.evaluate(
            LocationResult(31.2, 121.5, "amap_continuous", 150f, now)
        )

        assertEquals(LocationQualityIssue.LOW_ACCURACY, (result as LocationQuality.Degraded).issue)
    }
}
```

- [ ] **Step 2: Run the evaluator test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.location.quality.LocationQualityEvaluatorTest"
```

Expected: FAIL because `LocationQualityEvaluator`, `LocationQuality`, `LocationQualityIssue`, and `LocationResult.timestampMillis` do not exist.

- [ ] **Step 3: Add timestamp metadata to `LocationResult`**

Modify `/Users/wajie/StudioProjects/longcare/core/model/src/main/kotlin/com/ytone/longcare/model/LocationResult.kt`:

```kotlin
package com.ytone.longcare.model

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
    val accuracy: Float = 0f,
    val timestampMillis: Long = System.currentTimeMillis()
)
```

This keeps existing four-argument call sites source-compatible.

- [ ] **Step 4: Add critical location result contracts**

Create `/Users/wajie/StudioProjects/longcare/core/domain/src/main/kotlin/com/ytone/longcare/domain/location/CriticalLocationResult.kt`:

```kotlin
package com.ytone.longcare.domain.location

import com.ytone.longcare.model.LocationResult

sealed interface CriticalLocationResult {
    val location: LocationResult?

    data class Reliable(
        override val location: LocationResult
    ) : CriticalLocationResult

    data class Degraded(
        override val location: LocationResult?,
        val issue: LocationQualityIssue,
        val message: String
    ) : CriticalLocationResult
}

sealed interface LocationQuality {
    data class Reliable(val location: LocationResult) : LocationQuality
    data class Degraded(
        val location: LocationResult?,
        val issue: LocationQualityIssue,
        val message: String
    ) : LocationQuality
}

enum class LocationQualityIssue {
    MISSING,
    INVALID_COORDINATE,
    STALE,
    LOW_ACCURACY
}
```

- [ ] **Step 5: Add the quality evaluator**

Create `/Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/quality/LocationQualityEvaluator.kt`:

```kotlin
package com.ytone.longcare.features.location.quality

import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.domain.location.LocationQualityIssue
import com.ytone.longcare.model.LocationResult
import javax.inject.Inject

class LocationQualityEvaluator @Inject constructor(
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
    private val maxAccuracyMeters: Float = DEFAULT_MAX_ACCURACY_METERS,
) {

    fun evaluate(location: LocationResult?): LocationQuality {
        if (location == null) {
            return LocationQuality.Degraded(
                location = null,
                issue = LocationQualityIssue.MISSING,
                message = "无法获取当前位置，本次操作将继续。"
            )
        }

        if (!location.latitude.isFinite() ||
            !location.longitude.isFinite() ||
            (location.latitude == 0.0 && location.longitude == 0.0) ||
            location.latitude !in -90.0..90.0 ||
            location.longitude !in -180.0..180.0
        ) {
            return LocationQuality.Degraded(
                location = location,
                issue = LocationQualityIssue.INVALID_COORDINATE,
                message = "当前定位坐标异常，本次操作将继续。"
            )
        }

        val ageMs = nowProvider() - location.timestampMillis
        if (ageMs > maxAgeMs) {
            return LocationQuality.Degraded(
                location = location,
                issue = LocationQualityIssue.STALE,
                message = "当前定位可能不是最新位置，本次操作将继续。"
            )
        }

        if (location.accuracy <= 0f || location.accuracy > maxAccuracyMeters) {
            return LocationQuality.Degraded(
                location = location,
                issue = LocationQualityIssue.LOW_ACCURACY,
                message = "当前定位精度较低，本次操作将继续。"
            )
        }

        return LocationQuality.Reliable(location)
    }

    companion object {
        const val DEFAULT_MAX_AGE_MS = 10_000L
        const val DEFAULT_MAX_ACCURACY_METERS = 100f
    }
}
```

- [ ] **Step 6: Run the evaluator test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.location.quality.LocationQualityEvaluatorTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add \
  /Users/wajie/StudioProjects/longcare/core/model/src/main/kotlin/com/ytone/longcare/model/LocationResult.kt \
  /Users/wajie/StudioProjects/longcare/core/domain/src/main/kotlin/com/ytone/longcare/domain/location/CriticalLocationResult.kt \
  /Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/quality/LocationQualityEvaluator.kt \
  /Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/location/quality/LocationQualityEvaluatorTest.kt
git commit -m "feat(location): add critical location quality evaluator"
```

## Task 4: Add Fresh-First Critical Location API And Bugly Tracking

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/core/domain/src/main/kotlin/com/ytone/longcare/domain/location/LocationFacade.kt`
- Modify: `/Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationStateManager.kt`
- Modify: `/Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacade.kt`
- Modify: `/Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/tracker/LocationEventTracker.kt`
- Create: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacadeCriticalLocationTest.kt`

- [ ] **Step 1: Write the failing fresh-first facade test**

Create `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacadeCriticalLocationTest.kt`:

```kotlin
package com.ytone.longcare.features.location.core

import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.manager.LocationStateManager
import com.ytone.longcare.features.location.provider.SystemLocationProvider
import com.ytone.longcare.domain.location.CriticalLocationResult
import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.domain.location.LocationQualityIssue
import com.ytone.longcare.features.location.quality.LocationQualityEvaluator
import com.ytone.longcare.model.LocationResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLocationFacadeCriticalLocationTest {

    private val now = 1_000_000L
    private val evaluator = LocationQualityEvaluator(
        nowProvider = { now },
        maxAgeMs = 10_000L,
        maxAccuracyMeters = 100f,
    )

    @Test
    fun `critical location prefers fresh amap result over cache`() = runTest {
        val cache = LocationResult(30.0, 120.0, "cache", 10f, now)
        val fresh = LocationResult(31.2, 121.5, "amap", 10f, now)
        val stateManager = mockk<LocationStateManager>()
        val amap = mockk<ContinuousAmapLocationManager>()

        every { stateManager.getValidLocationWithTimestamp(any()) } returns cache
        coEvery { amap.getCurrentLocation(any()) } returns fresh

        val result = facade(amap, stateManager).getCriticalLocation(timeoutMs = 1000)

        assertEquals(fresh, (result as CriticalLocationResult.Reliable).location)
    }

    @Test
    fun `critical location falls back to reliable cache when fresh lookup fails`() = runTest {
        val cache = LocationResult(30.0, 120.0, "cache", 10f, now)
        val stateManager = mockk<LocationStateManager>()
        val amap = mockk<ContinuousAmapLocationManager>()
        val system = mockk<SystemLocationProvider>()

        every { stateManager.getValidLocationWithTimestamp(any()) } returns cache
        coEvery { amap.getCurrentLocation(any()) } returns null
        coEvery { system.getCurrentLocation() } returns null

        val result = facade(amap, stateManager, system).getCriticalLocation(timeoutMs = 1000)

        assertEquals(cache, (result as CriticalLocationResult.Reliable).location)
    }

    @Test
    fun `critical location returns degraded missing when no provider has location`() = runTest {
        val stateManager = mockk<LocationStateManager>()
        val amap = mockk<ContinuousAmapLocationManager>()
        val system = mockk<SystemLocationProvider>()

        every { stateManager.getValidLocationWithTimestamp(any()) } returns null
        coEvery { amap.getCurrentLocation(any()) } returns null
        coEvery { system.getCurrentLocation() } returns null

        val result = facade(amap, stateManager, system).getCriticalLocation(timeoutMs = 1000)

        assertTrue(result is CriticalLocationResult.Degraded)
        assertEquals(null, result.location)
    }

    private fun facade(
        amap: ContinuousAmapLocationManager,
        stateManager: LocationStateManager,
        system: SystemLocationProvider = mockk(relaxed = true),
    ): DefaultLocationFacade {
        return DefaultLocationFacade(
            continuousAmapLocationManager = amap,
            locationStateManager = stateManager,
            systemLocationProvider = system,
            locationKeepAliveManager = mockk(relaxed = true),
            locationQualityEvaluator = evaluator,
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.location.core.DefaultLocationFacadeCriticalLocationTest"
```

Expected: FAIL because `getCriticalLocation(...)`, `getValidLocationWithTimestamp(...)`, and the new constructor dependency do not exist.

- [ ] **Step 3: Extend `LocationFacade`**

Modify `/Users/wajie/StudioProjects/longcare/core/domain/src/main/kotlin/com/ytone/longcare/domain/location/LocationFacade.kt`:

```kotlin
package com.ytone.longcare.domain.location

import com.ytone.longcare.model.LocationResult
import kotlinx.coroutines.flow.Flow

interface LocationFacade {
    fun observeLocations(intervalMs: Long = 30_000L): Flow<LocationResult>
    suspend fun getCurrentLocation(timeoutMs: Long = 10_000L): LocationResult?
    suspend fun getCriticalLocation(timeoutMs: Long = 10_000L): CriticalLocationResult
    fun getCachedLocation(maxAgeMs: Long = 30_000L): LocationResult?
    fun acquireKeepAlive(owner: String)
    fun releaseKeepAlive(owner: String)
}
```

- [ ] **Step 4: Add timestamp-preserving cache access**

Modify `/Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationStateManager.kt`:

```kotlin
fun getValidLocationWithTimestamp(maxAgeMs: Long = 30_000L): LocationResult? {
    val state = _state.value
    val location = state.lastLocation ?: return null
    val time = state.lastLocationTime ?: return null

    if (System.currentTimeMillis() - time > maxAgeMs) {
        return null
    }
    return location.copy(timestampMillis = time)
}

fun getValidLocation(maxAgeMs: Long = 30_000L): LocationResult? {
    return getValidLocationWithTimestamp(maxAgeMs)
}
```

Replace the old `getValidLocation(...)` body with the delegation above.

- [ ] **Step 5: Add Bugly event type**

Modify `/Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/tracker/LocationEventTracker.kt` by adding this enum entry before `QUEUE_CLEANUP_ERROR`:

```kotlin
CRITICAL_LOCATION_DEGRADED("critical_location_degraded", "关键业务定位质量降级"),
```

- [ ] **Step 6: Implement critical location retrieval**

Modify `/Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacade.kt`:

Add these imports if they are not already present:

```kotlin
import com.ytone.longcare.domain.location.CriticalLocationResult
import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.domain.location.LocationQualityIssue
import com.ytone.longcare.features.location.quality.LocationQualityEvaluator
```

Update the constructor and add `getCriticalLocation`:

```kotlin
@Singleton
class DefaultLocationFacade @Inject constructor(
    private val continuousAmapLocationManager: ContinuousAmapLocationManager,
    private val locationStateManager: LocationStateManager,
    private val systemLocationProvider: SystemLocationProvider,
    private val locationKeepAliveManager: LocationKeepAliveManager,
    private val locationQualityEvaluator: LocationQualityEvaluator
) : LocationFacade {

    override suspend fun getCriticalLocation(timeoutMs: Long): CriticalLocationResult {
        val fresh = try {
            continuousAmapLocationManager.getCurrentLocation(timeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_ERROR,
                throwable = e,
                extras = mapOf("critical" to true, "errorMsg" to e.message)
            )
            null
        }

        val freshQuality = locationQualityEvaluator.evaluate(fresh)
        if (freshQuality is LocationQuality.Reliable) {
            locationStateManager.recordLocationSuccess(freshQuality.location)
            return CriticalLocationResult.Reliable(freshQuality.location)
        }

        val system = try {
            systemLocationProvider.getCurrentLocation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocationEventTracker.trackError(
                LocationEventTracker.EventType.SYSTEM_SINGLE_LOCATION_ERROR,
                throwable = e,
                extras = mapOf("critical" to true, "errorMsg" to e.message)
            )
            null
        }

        val systemQuality = locationQualityEvaluator.evaluate(system)
        if (systemQuality is LocationQuality.Reliable) {
            locationStateManager.recordLocationSuccess(systemQuality.location)
            return CriticalLocationResult.Reliable(systemQuality.location)
        }

        val cache = locationStateManager.getValidLocationWithTimestamp(LocationQualityEvaluator.DEFAULT_MAX_AGE_MS)
        val cacheQuality = locationQualityEvaluator.evaluate(cache)
        if (cacheQuality is LocationQuality.Reliable) {
            return CriticalLocationResult.Reliable(cacheQuality.location)
        }

        val degraded = listOf(freshQuality, systemQuality, cacheQuality)
            .filterIsInstance<LocationQuality.Degraded>()
            .firstOrNull { it.location != null }
            ?: LocationQuality.Degraded(
                location = null,
                issue = LocationQualityIssue.MISSING,
                message = "无法获取当前位置，本次操作将继续。"
            )

        LocationEventTracker.trackEvent(
            LocationEventTracker.EventType.CRITICAL_LOCATION_DEGRADED,
            extras = mapOf(
                "issue" to degraded.issue.name,
                "provider" to degraded.location?.provider,
                "accuracy" to degraded.location?.accuracy,
                "timestampMillis" to degraded.location?.timestampMillis,
            )
        )

        return CriticalLocationResult.Degraded(
            location = degraded.location,
            issue = degraded.issue,
            message = degraded.message
        )
    }
}
```

Keep the existing `observeLocations`, `getCurrentLocation`, `getCachedLocation`, `acquireKeepAlive`, and `releaseKeepAlive` methods unchanged except for constructor injection imports.

- [ ] **Step 7: Run the critical location test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.location.core.DefaultLocationFacadeCriticalLocationTest"
```

Expected: PASS.

- [ ] **Step 8: Run existing location facade cancellation tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.location.core.DefaultLocationFacadeCancellationTest"
```

Expected: PASS after updating test constructors to pass `locationQualityEvaluator = LocationQualityEvaluator()`.

- [ ] **Step 9: Commit**

```bash
git add \
  /Users/wajie/StudioProjects/longcare/core/domain/src/main/kotlin/com/ytone/longcare/domain/location/LocationFacade.kt \
  /Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/manager/LocationStateManager.kt \
  /Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacade.kt \
  /Users/wajie/StudioProjects/longcare/feature/location/src/main/kotlin/com/ytone/longcare/features/location/tracker/LocationEventTracker.kt \
  /Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacadeCriticalLocationTest.kt \
  /Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/location/core/DefaultLocationFacadeCancellationTest.kt
git commit -m "feat(location): add critical location retrieval"
```

## Task 5: Surface Location Permission Purpose And Degraded Location Warning In NFC

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegate.kt`
- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreenHandlers.kt`
- Test: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt`

- [ ] **Step 1: Add NFC warning copy tests**

Append to `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt`:

```kotlin
@Test
fun `location purpose copy names permission feature and purpose`() {
    assertEquals(
        "需要定位权限用于记录到岗位置和服务位置，本权限仅在您进行NFC签到或开始服务时使用。",
        nfcLocationPermissionPurposeMessage()
    )
}

@Test
fun `degraded location warning explains continuation`() {
    assertEquals(
        "当前定位可能不准确或无法获取，本次操作将继续。",
        nfcLocationDegradedWarningMessage()
    )
}
```

- [ ] **Step 2: Run the copy tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.ui.NfcWorkflowUiCopyTest"
```

Expected: FAIL because the two functions do not exist.

- [ ] **Step 3: Add copy helpers**

Append to `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreenHandlers.kt`:

```kotlin
internal fun nfcLocationPermissionPurposeMessage(): String {
    return "需要定位权限用于记录到岗位置和服务位置，本权限仅在您进行NFC签到或开始服务时使用。"
}

internal fun nfcLocationDegradedWarningMessage(): String {
    return "当前定位可能不准确或无法获取，本次操作将继续。"
}
```

- [ ] **Step 4: Use critical location result in NFC delegate**

Modify `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegate.kt`:

```kotlin
import com.ytone.longcare.domain.location.CriticalLocationResult

internal data class NfcLocationCoordinatesResult(
    val longitude: String,
    val latitude: String,
    val warningMessage: String? = null
)

suspend fun getCurrentLocationCoordinates(): NfcLocationCoordinatesResult {
    return try {
        when (val result = locationFacade.getCriticalLocation()) {
            is CriticalLocationResult.Reliable -> NfcLocationCoordinatesResult(
                longitude = result.location.longitude.toString(),
                latitude = result.location.latitude.toString()
            )
            is CriticalLocationResult.Degraded -> NfcLocationCoordinatesResult(
                longitude = result.location?.longitude?.toString().orEmpty(),
                latitude = result.location?.latitude?.toString().orEmpty(),
                warningMessage = result.message
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        NfcLocationCoordinatesResult(
            longitude = "",
            latitude = "",
            warningMessage = "无法获取当前位置，本次操作将继续。"
        )
    }
}
```

Update `NfcWorkflowViewModel.getCurrentLocationCoordinates()` to return `NfcLocationCoordinatesResult` instead of `Pair<String, String>`.

- [ ] **Step 5: Show purpose before permission launch in NFC handlers**

Modify `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreenHandlers.kt` so the screen owns a Boolean state such as `showLocationPurposeDialog`. The confirm action should call:

```kotlin
locationOnlyPermissionLauncher.launch(UnifiedPermissionHelper.getLocationRequiredPermissions())
```

or:

```kotlin
trackingPermissionLauncher.launch(UnifiedPermissionHelper.getLocationRequiredPermissions())
```

depending on whether the pending action is only coordinate lookup or tracking start.

Use `AlertDialog` with:

```kotlin
title = { Text("定位权限说明") }
text = { Text(nfcLocationPermissionPurposeMessage()) }
confirmButton = { TextButton(onClick = onConfirm) { Text("继续授权") } }
dismissButton = { TextButton(onClick = onDismiss) { Text("暂不授权") } }
```

- [ ] **Step 6: Surface degraded warning to NFC flow**

In the handler that calls `nfcViewModel.getCurrentLocationCoordinates()`, if `warningMessage` is non-null, return `LocationRequestResult.Error(warningMessage)` for the UI to show while still allowing the existing action path to proceed with available or empty coordinates. Preserve existing exception handling.

- [ ] **Step 7: Run NFC copy tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.nfc.ui.NfcWorkflowUiCopyTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add \
  /Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcActivityAndLocationDelegate.kt \
  /Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfc/vm/NfcWorkflowViewModel.kt \
  /Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowScreenHandlers.kt \
  /Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowUiCopyTest.kt
git commit -m "fix(nfc): warn and continue on degraded location"
```

## Task 6: Warn And Continue When Service Start Location Is Degraded

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/core/ui/src/main/kotlin/com/ytone/longcare/shared/vm/SharedOrderDetailViewModel.kt`
- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/selectservice/ui/SelectServiceScreen.kt`
- Create: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/service/SharedOrderDetailLocationPolicyTest.kt`

- [ ] **Step 1: Write static policy test for service-start location behavior**

Create `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/service/SharedOrderDetailLocationPolicyTest.kt`:

```kotlin
package com.ytone.longcare.features.service

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedOrderDetailLocationPolicyTest {

    @Test
    fun `service start uses critical location and exposes warning state`() {
        val source = File(
            "../core/ui/src/main/kotlin/com/ytone/longcare/shared/vm/SharedOrderDetailViewModel.kt"
        ).readText()

        assertTrue(source.contains("getCriticalLocation"))
        assertTrue(source.contains("locationWarning"))
        assertTrue(source.contains("CriticalLocationResult.Degraded"))
    }
}
```

- [ ] **Step 2: Run the policy test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.service.SharedOrderDetailLocationPolicyTest"
```

Expected: FAIL because the ViewModel still uses `getCurrentLocation()`.

- [ ] **Step 3: Add warning state and critical location handling**

Modify `/Users/wajie/StudioProjects/longcare/core/ui/src/main/kotlin/com/ytone/longcare/shared/vm/SharedOrderDetailViewModel.kt`:

Add imports:

```kotlin
import com.ytone.longcare.domain.location.CriticalLocationResult
import kotlinx.coroutines.flow.asStateFlow
```

Add state near other UI state:

```kotlin
private val _locationWarning = MutableStateFlow<String?>(null)
val locationWarning = _locationWarning.asStateFlow()

fun clearLocationWarning() {
    _locationWarning.value = null
}
```

Replace the body of `getCurrentLocationCoordinates()` with:

```kotlin
private suspend fun getCurrentLocationCoordinates(): Pair<String, String> {
    return try {
        if (!hasLocationPermission() || !isLocationServiceEnabled()) {
            _locationWarning.value = "当前定位不可用，本次操作将继续。"
            return Pair("", "")
        }

        when (val result = locationFacade.getCriticalLocation()) {
            is CriticalLocationResult.Reliable -> {
                _locationWarning.value = null
                Pair(result.location.longitude.toString(), result.location.latitude.toString())
            }
            is CriticalLocationResult.Degraded -> {
                _locationWarning.value = result.message
                Pair(
                    result.location?.longitude?.toString().orEmpty(),
                    result.location?.latitude?.toString().orEmpty()
                )
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        _locationWarning.value = "无法获取当前位置，本次操作将继续。"
        Pair("", "")
    }
}
```

- [ ] **Step 4: Wire warning display in the service-start screen**

Modify `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/selectservice/ui/SelectServiceScreen.kt`. Add imports:

```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
```

Collect the warning near the existing `starOrderState` collection:

```kotlin
val locationWarning by sharedViewModel.locationWarning.collectAsStateWithLifecycle()
```

Add this dialog inside the root `Box`, after the `Scaffold` block:

```kotlin
locationWarning?.let { message ->
    AlertDialog(
        onDismissRequest = sharedViewModel::clearLocationWarning,
        title = { Text("定位提示") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = sharedViewModel::clearLocationWarning) {
                Text("我知道了")
            }
        }
    )
}
```

Do not change the existing `onNextStep` callback or the `starOrder(...)` success path.

- [ ] **Step 5: Run the policy test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.service.SharedOrderDetailLocationPolicyTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  /Users/wajie/StudioProjects/longcare/core/ui/src/main/kotlin/com/ytone/longcare/shared/vm/SharedOrderDetailViewModel.kt \
  /Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/service/SharedOrderDetailLocationPolicyTest.kt
git commit -m "fix(service): warn and continue on degraded start location"
```

## Task 7: Hide Unimplemented Profile Options

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsComponents.kt`
- Create: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsVisibilityTest.kt`

- [ ] **Step 1: Write the failing static visibility test**

Create `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsVisibilityTest.kt`:

```kotlin
package com.ytone.longcare.features.profile.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfileOptionsVisibilityTest {

    @Test
    fun `profile does not show unimplemented option labels`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsComponents.kt"
        ).readText()

        assertFalse(source.contains("\"信息上报\""))
        assertFalse(source.contains("\"个人信息\""))
        assertFalse(source.contains("\"设置\""))
        assertFalse(source.contains("onClick = {}"))
    }
}
```

- [ ] **Step 2: Run the visibility test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.profile.ui.ProfileOptionsVisibilityTest"
```

Expected: FAIL because the three unimplemented labels and empty click handlers are present.

- [ ] **Step 3: Hide the unimplemented options**

Modify `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsComponents.kt`:

Replace `OptionsCard()` with:

```kotlin
@Composable
fun OptionsCard() {
    // Unimplemented Profile entries are intentionally hidden for Huawei review compliance.
}
```

Remove now-unused imports from this file:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.ytone.longcare.R
```

Keep `LogoutButton` unchanged.

- [ ] **Step 4: Run the visibility test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.profile.ui.ProfileOptionsVisibilityTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  /Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsComponents.kt \
  /Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsVisibilityTest.kt
git commit -m "fix(profile): hide unimplemented options"
```

## Task 8: Final Verification

**Files:**
- Modify only if verification reveals a defect in files changed by Tasks 1-7.

- [ ] **Step 1: Run targeted tests**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.ytone.longcare.features.login.vm.LoginViewModelPrivacyGateTest" \
  --tests "com.ytone.longcare.features.home.ui.HomeScreenPermissionPolicyTest" \
  --tests "com.ytone.longcare.features.location.quality.LocationQualityEvaluatorTest" \
  --tests "com.ytone.longcare.features.location.core.DefaultLocationFacadeCriticalLocationTest" \
  --tests "com.ytone.longcare.features.nfc.ui.NfcWorkflowUiCopyTest" \
  --tests "com.ytone.longcare.features.service.SharedOrderDetailLocationPolicyTest" \
  --tests "com.ytone.longcare.features.profile.ui.ProfileOptionsVisibilityTest"
```

Expected: PASS.

- [ ] **Step 2: Run app unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS, or document unrelated pre-existing failures with exact failing test names.

- [ ] **Step 3: Run lint**

Run:

```bash
./gradlew :app:lintDebug
```

Expected: PASS, or document unrelated pre-existing lint failures.

- [ ] **Step 4: Manual Huawei regression checklist**

Verify on a clean install:

```text
1. Open login page and do not check privacy: no API request should be made.
2. Tap send-code while privacy is unchecked: show privacy prompt/toast and do not call API.
3. Check privacy: startup config may load.
4. Login and enter home: no camera/location permission dialog appears automatically.
5. Deny location from a feature action, kill app, relaunch: no automatic location permission dialog appears.
6. Trigger NFC/start-service location: purpose dialog appears before Android permission dialog.
7. Disable system location: service action can continue after warning.
8. Simulate missing/degraded location: warning appears and Bugly event is posted through LocationEventTracker.
9. Open Profile page: 信息上报, 个人信息, 设置 are not visible.
```

- [ ] **Step 5: Commit verification fixes when verification changed files**

If Step 1-4 changed files, commit only those concrete files:

```bash
git status --short
git add /Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/profile/ui/ProfileOptionsVisibilityTest.kt
git commit -m "fix: address Huawei compliance verification issues"
```

If no fixes were needed, do not create an empty commit.
