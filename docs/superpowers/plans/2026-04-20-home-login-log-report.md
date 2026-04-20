# Home Login Log Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Report the existing `/V1/Login/Log` payload once for each real home-screen entry, without reporting again for plain background/foreground resumes on the same home instance.

**Architecture:** Reuse the existing `LoginLogParamModel`, Retrofit method, and login repository boundary. Add a dedicated device/network info provider, expose `recordLoginLog(...)` through `LoginRepository`, orchestrate entry reporting in `HomeSharedViewModel`, and trigger it from `HomeScreen`’s one-shot entry effect with a narrow in-flight guard only.

**Tech Stack:** Kotlin, Hilt, Retrofit, Moshi DTOs, `ApiResult`, Android `ConnectivityManager`, Android `TelephonyManager`, Jetpack Compose `LaunchedEffect`, `StateFlow`, JUnit4, MockK

---

## File Responsibility Map

- Modify: `/Users/wajie/StudioProjects/longcare/core/domain/src/main/kotlin/com/ytone/longcare/domain/login/LoginRepository.kt`
  Purpose: expose the domain contract for login-log reporting.

- Modify: `/Users/wajie/StudioProjects/longcare/core/data/src/main/kotlin/com/ytone/longcare/data/repository/LoginRepositoryImpl.kt`
  Purpose: implement `recordLoginLog(...)` via the existing Retrofit service using the same dispatcher and `safeApiCall(...)` pattern as the rest of the login repository.

- Create: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/reporting/HomeLoginLogInfoProvider.kt`
  Purpose: collect `phoneSystem`, `phoneVersion`, `networkType`, and `networkOperator` into `LoginLogParamModel` using stable fallback behavior.

- Create: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProvider.kt`
  Purpose: Android implementation of the provider using `Build`, `ConnectivityManager`, and `TelephonyManager`.

- Create: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/di/HomeReportingModule.kt`
  Purpose: bind the provider implementation into Hilt so `HomeSharedViewModel` can depend on an interface.

- Modify: `/Users/wajie/StudioProjects/longcare/feature/home/src/main/kotlin/com/ytone/longcare/features/home/vm/HomeSharedViewModel.kt`
  Purpose: add `reportHomeEntry()` and a narrow in-flight guard so one screen-entry event causes one network attempt without direct UI-layer networking.

- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt`
  Purpose: trigger `homeSharedViewModel.reportHomeEntry()` exactly once for each home-screen entry via `LaunchedEffect(Unit)`.

- Create: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProviderTest.kt`
  Purpose: unit-test device/network field mapping and fallback behavior.

- Create: `/Users/wajie/StudioProjects/longcare/feature/home/src/test/kotlin/com/ytone/longcare/features/home/vm/HomeSharedViewModelTest.kt`
  Purpose: unit-test the repository invocation, in-flight guard, and silent-failure behavior.

- Create: `/Users/wajie/StudioProjects/longcare/core/data/src/test/kotlin/com/ytone/longcare/data/repository/LoginRepositoryImplTest.kt`
  Purpose: unit-test that `recordLoginLog(...)` delegates to the existing API service correctly.

## Task 1: Expose Login Log Reporting Through the Login Repository

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/core/domain/src/main/kotlin/com/ytone/longcare/domain/login/LoginRepository.kt`
- Modify: `/Users/wajie/StudioProjects/longcare/core/data/src/main/kotlin/com/ytone/longcare/data/repository/LoginRepositoryImpl.kt`
- Create: `/Users/wajie/StudioProjects/longcare/core/data/src/test/kotlin/com/ytone/longcare/data/repository/LoginRepositoryImplTest.kt`

- [ ] **Step 1: Write the failing repository implementation test**

Create `/Users/wajie/StudioProjects/longcare/core/data/src/test/kotlin/com/ytone/longcare/data/repository/LoginRepositoryImplTest.kt`:

```kotlin
package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.model.LoginLogParamModel
import com.ytone.longcare.model.Response
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginRepositoryImplTest {

    @Test
    fun `recordLoginLog delegates to api service`() = runTest(StandardTestDispatcher()) {
        val apiService = mockk<LongCareApiService>()
        val eventBus = mockk<AppEventBus>(relaxed = true)
        val request = LoginLogParamModel(
            phoneSystem = "Android",
            phoneVersion = "16",
            networkType = "WIFI",
            networkOperator = "Carrier",
        )

        coEvery { apiService.recordLoginLog(request) } returns Response(code = 200, msg = "ok", data = Unit)

        val repository = LoginRepositoryImpl(
            apiService = apiService,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            eventBus = eventBus,
        )

        val result = repository.recordLoginLog(request)

        assertTrue(result is ApiResult.Success)
        coVerify(exactly = 1) { apiService.recordLoginLog(request) }
    }
}
```

- [ ] **Step 2: Run the repository test and verify it fails**

Run:

```bash
./gradlew :core:data:testDebugUnitTest --tests "com.ytone.longcare.data.repository.LoginRepositoryImplTest"
```

Expected:
- FAIL because `LoginRepository.recordLoginLog(...)` and `LoginRepositoryImpl.recordLoginLog(...)` do not exist yet

- [ ] **Step 3: Add the repository contract and implementation**

Update `/Users/wajie/StudioProjects/longcare/core/domain/src/main/kotlin/com/ytone/longcare/domain/login/LoginRepository.kt`:

```kotlin
package com.ytone.longcare.domain.login

import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.model.LoginLogParamModel
import com.ytone.longcare.model.LoginResultModel
import com.ytone.longcare.model.StartConfigResultModel

interface LoginRepository {
    suspend fun login(mobile: String, code: String): ApiResult<LoginResultModel>
    suspend fun sendSmsCode(mobile: String): ApiResult<Unit>
    suspend fun getStartConfig(): ApiResult<StartConfigResultModel>
    suspend fun recordLoginLog(param: LoginLogParamModel): ApiResult<Unit>
}
```

Update `/Users/wajie/StudioProjects/longcare/core/data/src/main/kotlin/com/ytone/longcare/data/repository/LoginRepositoryImpl.kt`:

```kotlin
package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.safeApiCall
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.model.LoginLogParamModel
import com.ytone.longcare.model.LoginPhoneParamModel
import com.ytone.longcare.model.SendSmsCodeParamModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

class LoginRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val eventBus: AppEventBus
) : LoginRepository {

    override suspend fun login(mobile: String, code: String) = safeApiCall(ioDispatcher, eventBus) {
        apiService.phoneLogin(
            LoginPhoneParamModel(
                mobile = mobile,
                smsCode = code,
                userIdentity = 1,
            ),
        )
    }

    override suspend fun sendSmsCode(mobile: String) = safeApiCall(ioDispatcher, eventBus) {
        apiService.sendSmsCode(SendSmsCodeParamModel(mobile = mobile, codeType = 1))
    }

    override suspend fun getStartConfig() = safeApiCall(ioDispatcher, eventBus) {
        apiService.getStartConfig()
    }

    override suspend fun recordLoginLog(param: LoginLogParamModel) = safeApiCall(ioDispatcher, eventBus) {
        apiService.recordLoginLog(param)
    }
}
```

- [ ] **Step 4: Run the repository test again and verify it passes**

Run:

```bash
./gradlew :core:data:testDebugUnitTest --tests "com.ytone.longcare.data.repository.LoginRepositoryImplTest"
```

Expected:
- PASS with `LoginRepositoryImplTest` green

- [ ] **Step 5: Commit the repository boundary**

```bash
git add \
  /Users/wajie/StudioProjects/longcare/core/domain/src/main/kotlin/com/ytone/longcare/domain/login/LoginRepository.kt \
  /Users/wajie/StudioProjects/longcare/core/data/src/main/kotlin/com/ytone/longcare/data/repository/LoginRepositoryImpl.kt \
  /Users/wajie/StudioProjects/longcare/core/data/src/test/kotlin/com/ytone/longcare/data/repository/LoginRepositoryImplTest.kt
git commit -m "feat(home): add login log repository reporting"
```

## Task 2: Add a Dedicated Home Login Log Info Provider

**Files:**
- Create: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/reporting/HomeLoginLogInfoProvider.kt`
- Create: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProvider.kt`
- Create: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/di/HomeReportingModule.kt`
- Create: `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProviderTest.kt`

- [ ] **Step 1: Write the failing provider tests**

Create `/Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProviderTest.kt`:

```kotlin
package com.ytone.longcare.features.home.reporting

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultHomeLoginLogInfoProviderTest {

    @Test
    fun `build returns wifi payload with operator`() {
        val context = mockk<Context>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>()
        val telephonyManager = mockk<TelephonyManager>()
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { context.getSystemService(Context.TELEPHONY_SERVICE) } returns telephonyManager
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false
        every { telephonyManager.networkOperatorName } returns "Carrier"

        val provider = DefaultHomeLoginLogInfoProvider(context)

        val result = provider.build()

        assertEquals("Android", result.phoneSystem)
        assertEquals("WIFI", result.networkType)
        assertEquals("Carrier", result.networkOperator)
    }

    @Test
    fun `build falls back gracefully when services unavailable`() {
        val context = mockk<Context>(relaxed = true)

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns null
        every { context.getSystemService(Context.TELEPHONY_SERVICE) } returns null

        val provider = DefaultHomeLoginLogInfoProvider(context)

        val result = provider.build()

        assertEquals("Android", result.phoneSystem)
        assertEquals("NONE", result.networkType)
        assertEquals("", result.networkOperator)
    }
}
```

- [ ] **Step 2: Run the provider tests and verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.home.reporting.DefaultHomeLoginLogInfoProviderTest"
```

Expected:
- FAIL because the provider files do not exist yet

- [ ] **Step 3: Implement the provider and Hilt binding**

Create `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/reporting/HomeLoginLogInfoProvider.kt`:

```kotlin
package com.ytone.longcare.features.home.reporting

import com.ytone.longcare.model.LoginLogParamModel

interface HomeLoginLogInfoProvider {
    fun build(): LoginLogParamModel
}
```

Create `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProvider.kt`:

```kotlin
package com.ytone.longcare.features.home.reporting

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import com.ytone.longcare.model.LoginLogParamModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultHomeLoginLogInfoProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HomeLoginLogInfoProvider {

    override fun build(): LoginLogParamModel {
        return LoginLogParamModel(
            phoneSystem = "Android",
            phoneVersion = Build.VERSION.RELEASE.orEmpty(),
            networkType = resolveNetworkType(),
            networkOperator = resolveNetworkOperator(),
        )
    }

    private fun resolveNetworkType(): String {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "NONE"
        val network = connectivityManager.activeNetwork ?: return "NONE"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "UNKNOWN"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "UNKNOWN"
        }
    }

    private fun resolveNetworkOperator(): String {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return ""
        return telephonyManager.networkOperatorName.orEmpty()
    }
}
```

Create `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/di/HomeReportingModule.kt`:

```kotlin
package com.ytone.longcare.features.home.di

import com.ytone.longcare.features.home.reporting.DefaultHomeLoginLogInfoProvider
import com.ytone.longcare.features.home.reporting.HomeLoginLogInfoProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeReportingModule {

    @Binds
    @Singleton
    abstract fun bindHomeLoginLogInfoProvider(
        impl: DefaultHomeLoginLogInfoProvider,
    ): HomeLoginLogInfoProvider
}
```

- [ ] **Step 4: Run the provider tests again and verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.home.reporting.DefaultHomeLoginLogInfoProviderTest"
```

Expected:
- PASS with the provider tests green

- [ ] **Step 5: Commit the provider layer**

```bash
git add \
  /Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/reporting/HomeLoginLogInfoProvider.kt \
  /Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProvider.kt \
  /Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/di/HomeReportingModule.kt \
  /Users/wajie/StudioProjects/longcare/app/src/test/kotlin/com/ytone/longcare/features/home/reporting/DefaultHomeLoginLogInfoProviderTest.kt
git commit -m "feat(home): add login log info provider"
```

## Task 3: Orchestrate Home Entry Reporting in HomeSharedViewModel

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/feature/home/src/main/kotlin/com/ytone/longcare/features/home/vm/HomeSharedViewModel.kt`
- Create: `/Users/wajie/StudioProjects/longcare/feature/home/src/test/kotlin/com/ytone/longcare/features/home/vm/HomeSharedViewModelTest.kt`

- [ ] **Step 1: Write the failing HomeSharedViewModel tests**

Create `/Users/wajie/StudioProjects/longcare/feature/home/src/test/kotlin/com/ytone/longcare/features/home/vm/HomeSharedViewModelTest.kt`:

```kotlin
package com.ytone.longcare.features.home.vm

import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.home.reporting.HomeLoginLogInfoProvider
import com.ytone.longcare.model.LoginLogParamModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomeSharedViewModelTest {

    @Test
    fun `reportHomeEntry sends one log payload`() = runTest(StandardTestDispatcher()) {
        val sessionRepository = mockk<UserSessionRepository>()
        val loginRepository = mockk<LoginRepository>()
        val infoProvider = mockk<HomeLoginLogInfoProvider>()
        val payload = LoginLogParamModel(phoneSystem = "Android", phoneVersion = "16", networkType = "WIFI", networkOperator = "Carrier")

        every { sessionRepository.sessionState } returns MutableStateFlow(SessionState.LoggedOut)
        every { infoProvider.build() } returns payload
        coEvery { loginRepository.recordLoginLog(payload) } returns ApiResult.Success(Unit)

        val viewModel = HomeSharedViewModel(sessionRepository, loginRepository, infoProvider)

        viewModel.reportHomeEntry()
        advanceUntilIdle()

        coVerify(exactly = 1) { loginRepository.recordLoginLog(payload) }
    }

    @Test
    fun `reportHomeEntry ignores concurrent duplicate call while request in flight`() = runTest(StandardTestDispatcher()) {
        val sessionRepository = mockk<UserSessionRepository>()
        val loginRepository = mockk<LoginRepository>()
        val infoProvider = mockk<HomeLoginLogInfoProvider>()
        val payload = LoginLogParamModel(phoneSystem = "Android")

        every { sessionRepository.sessionState } returns MutableStateFlow(SessionState.LoggedOut)
        every { infoProvider.build() } returns payload
        coEvery { loginRepository.recordLoginLog(payload) } coAnswers {
            kotlinx.coroutines.delay(1_000)
            ApiResult.Success(Unit)
        }

        val viewModel = HomeSharedViewModel(sessionRepository, loginRepository, infoProvider)

        viewModel.reportHomeEntry()
        viewModel.reportHomeEntry()
        advanceUntilIdle()

        coVerify(exactly = 1) { loginRepository.recordLoginLog(payload) }
    }

    @Test
    fun `reportHomeEntry swallows repository failure`() = runTest(StandardTestDispatcher()) {
        val sessionRepository = mockk<UserSessionRepository>()
        val loginRepository = mockk<LoginRepository>()
        val infoProvider = mockk<HomeLoginLogInfoProvider>()
        val payload = LoginLogParamModel(phoneSystem = "Android")

        every { sessionRepository.sessionState } returns MutableStateFlow(SessionState.LoggedOut)
        every { infoProvider.build() } returns payload
        coEvery { loginRepository.recordLoginLog(payload) } returns ApiResult.Failure(code = 500, message = "error")

        val viewModel = HomeSharedViewModel(sessionRepository, loginRepository, infoProvider)

        viewModel.reportHomeEntry()
        advanceUntilIdle()

        coVerify(exactly = 1) { loginRepository.recordLoginLog(payload) }
    }
}
```

- [ ] **Step 2: Run the HomeSharedViewModel tests and verify they fail**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.ytone.longcare.features.home.vm.HomeSharedViewModelTest"
```

Expected:
- FAIL because `HomeSharedViewModel` does not yet accept the new dependencies or expose `reportHomeEntry()`

- [ ] **Step 3: Implement view-model orchestration and in-flight guard**

Update `/Users/wajie/StudioProjects/longcare/feature/home/src/main/kotlin/com/ytone/longcare/features/home/vm/HomeSharedViewModel.kt`:

```kotlin
package com.ytone.longcare.features.home.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.home.reporting.HomeLoginLogInfoProvider
import com.ytone.longcare.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeSharedViewModel @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
    private val loginRepository: LoginRepository,
    private val homeLoginLogInfoProvider: HomeLoginLogInfoProvider,
) : ViewModel() {

    companion object {
        private const val TAG = "HomeSharedViewModel"
    }

    val userState: StateFlow<User?> = userSessionRepository.sessionState.map {
        when (it) {
            is SessionState.LoggedIn -> it.user
            else -> null
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
    )

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    private var reportHomeEntryJob: Job? = null

    fun updateSelectedTabIndex(index: Int) {
        _selectedTabIndex.value = index
    }

    fun reportHomeEntry() {
        if (reportHomeEntryJob?.isActive == true) {
            return
        }

        reportHomeEntryJob = viewModelScope.launch {
            val payload = homeLoginLogInfoProvider.build()
            val result = loginRepository.recordLoginLog(payload)
            if (result is com.ytone.longcare.common.network.ApiResult.Failure ||
                result is com.ytone.longcare.common.network.ApiResult.Exception
            ) {
                Log.w(TAG, "recordLoginLog failed: $result")
            }
        }
    }
}
```

- [ ] **Step 4: Run the HomeSharedViewModel tests again and verify they pass**

Run:

```bash
./gradlew :feature:home:testDebugUnitTest --tests "com.ytone.longcare.features.home.vm.HomeSharedViewModelTest"
```

Expected:
- PASS with `HomeSharedViewModelTest` green

- [ ] **Step 5: Commit the home reporting orchestration**

```bash
git add \
  /Users/wajie/StudioProjects/longcare/feature/home/src/main/kotlin/com/ytone/longcare/features/home/vm/HomeSharedViewModel.kt \
  /Users/wajie/StudioProjects/longcare/feature/home/src/test/kotlin/com/ytone/longcare/features/home/vm/HomeSharedViewModelTest.kt
git commit -m "feat(home): report login log on home entry"
```

## Task 4: Trigger Reporting From HomeScreen Entry

**Files:**
- Modify: `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt`

- [ ] **Step 1: Add a focused UI-entry test note before implementation**

No new test file is required here because the one-shot trigger boundary is already represented by the existing `LaunchedEffect(Unit)` pattern in `HomeScreen`.

The implementation must place the new call inside the existing one-shot effect, not inside the lifecycle resume collector.

- [ ] **Step 2: Update the HomeScreen one-shot entry effect**

Modify `/Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt` inside the existing `LaunchedEffect(Unit)` block:

```kotlin
    LaunchedEffect(Unit) {
        homeSharedViewModel.reportHomeEntry()

        val missingPermissions = buildRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }

        refreshCompatibilityGuides()
    }
```

Do not move this call into:

```kotlin
lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED)
```

because that would violate the approved “not on plain resume” rule.

- [ ] **Step 3: Run focused compile verification**

Run:

```bash
./gradlew :feature:home:compileDebugKotlin :app:compileDebugKotlin
```

Expected:
- PASS with home module and app compile green

- [ ] **Step 4: Commit the home entry trigger**

```bash
git add /Users/wajie/StudioProjects/longcare/app/src/main/kotlin/com/ytone/longcare/features/home/ui/HomeScreen.kt
git commit -m "feat(home): trigger login log on home entry"
```

## Task 5: Run End-to-End Verification

**Files:**
- No new files

- [ ] **Step 1: Run all focused unit and compile checks together**

Run:

```bash
./gradlew \
  :core:data:testDebugUnitTest --tests "com.ytone.longcare.data.repository.LoginRepositoryImplTest" \
  :app:testDebugUnitTest --tests "com.ytone.longcare.features.home.reporting.DefaultHomeLoginLogInfoProviderTest" \
  :feature:home:testDebugUnitTest --tests "com.ytone.longcare.features.home.vm.HomeSharedViewModelTest" \
  :feature:home:compileDebugKotlin \
  :app:compileDebugKotlin
```

Expected:
- all three focused test suites PASS
- home and app Kotlin compile PASS

- [ ] **Step 2: Check there are no extra resume-trigger hooks for reporting**

Run:

```bash
rg -n "reportHomeEntry\\(|recordLoginLog\\(" /Users/wajie/StudioProjects/longcare/app /Users/wajie/StudioProjects/longcare/feature /Users/wajie/StudioProjects/longcare/core
```

Expected:
- `HomeScreen.kt` contains the trigger in `LaunchedEffect(Unit)`
- `HomeSharedViewModel.kt` contains orchestration
- `LoginRepository.kt` and `LoginRepositoryImpl.kt` contain repository support
- no lifecycle resume collector invokes this reporting path

- [ ] **Step 3: Commit the verified full feature state**

```bash
git status --short
git add -A
git commit -m "test(home): verify login log reporting flow"
```

If there is nothing left to commit after the previous task commits, skip the extra commit and record that verification completed cleanly.

## Self-Review

- Spec coverage:
  - repository exposure is covered in Task 1
  - provider-based payload construction is covered in Task 2
  - `HomeSharedViewModel.reportHomeEntry()` orchestration and in-flight guard are covered in Task 3
  - `HomeScreen` entry trigger is covered in Task 4
  - silent failure and non-resume retrigger behavior are covered by Tasks 3 to 5

- Placeholder scan:
  - no `TODO`, `TBD`, or “similar to” placeholders remain
  - each task includes exact file paths, code blocks, and commands

- Type consistency:
  - `recordLoginLog(param: LoginLogParamModel)` is used consistently in repository contract and implementation
  - `HomeLoginLogInfoProvider.build()` consistently returns `LoginLogParamModel`
  - `HomeSharedViewModel.reportHomeEntry()` remains the single home entry orchestration API
