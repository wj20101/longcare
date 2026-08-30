package com.ytone.longcare.performance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.lifecycleScope
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.data.session.SessionSecretProvider
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.userstorage.UserStorageLeaseAccess
import com.ytone.longcare.model.SessionLoginPayload
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Signature-protected state setup for Baseline Profile and Macrobenchmark variants only.
 *
 * This source is deliberately absent from debug and production Release variants. All state
 * transitions use the same privacy/session boundaries as production code.
 */
@AndroidEntryPoint
class ProfileScenarioSetupActivity : ComponentActivity() {
    @Inject
    lateinit var privacyConsentManager: PrivacyConsentManager

    @Inject
    lateinit var userSessionRepository: UserSessionRepository

    @Inject
    lateinit var sessionSecretProvider: SessionSecretProvider

    @Inject
    lateinit var userStorageLeaseAccess: UserStorageLeaseAccess

    private var status: SetupStatus by mutableStateOf(SetupStatus.Running)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val currentStatus = status
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                        .testTag(SETUP_ROOT_TAG),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = currentStatus.message,
                        modifier = Modifier.testTag(currentStatus.resultTag),
                    )
                }
            }
        }

        val request = ProfileSetupScenario.fromWireId(intent.getStringExtra(EXTRA_SCENARIO_ID))
        if (request == null) {
            status = SetupStatus.Failed("missing-or-unknown-scenario")
            return
        }
        if (request == ProfileSetupScenario.FIRST_RUN_PRIVACY) {
            status = SetupStatus.Failed("first-run-must-use-clean-package-data")
            return
        }

        lifecycleScope.launch {
            status = try {
                prepare(request)
                SetupStatus.Complete(request.wireId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                SetupStatus.Failed(error.message ?: error::class.java.simpleName)
            }
        }
    }

    private suspend fun prepare(scenario: ProfileSetupScenario) {
        privacyConsentManager.markConsented()
        userSessionRepository.logout()

        when (scenario) {
            ProfileSetupScenario.LOGGED_OUT -> verifyLoggedOut()
            ProfileSetupScenario.CARE_HOME,
            ProfileSetupScenario.CARE_SERVICE_RECORDS,
            -> loginAndVerify(CARE_PROFILE_PAYLOAD)
            ProfileSetupScenario.SALES_HOME,
            ProfileSetupScenario.SALES_CUSTOMERS,
            -> loginAndVerify(SALES_PROFILE_PAYLOAD)
            ProfileSetupScenario.FIRST_RUN_PRIVACY -> error("first-run setup is not callable")
        }
    }

    private fun verifyLoggedOut() {
        check(privacyConsentManager.isPrivacyConsented) { "privacy consent was not persisted" }
        check(userSessionRepository.sessionState.value === SessionState.LoggedOut) {
            "public session state is not LoggedOut"
        }
        check(userStorageLeaseAccess.currentLeaseOrNull() == null) {
            "logged-out setup retained a user namespace"
        }
        check(sessionSecretProvider.requestAuthSnapshot() == null) {
            "logged-out setup retained session secrets"
        }
    }

    private suspend fun loginAndVerify(payload: SessionLoginPayload) {
        userSessionRepository.login(payload)
        val state = userSessionRepository.sessionState.value as? SessionState.LoggedIn
            ?: error("public session state is not LoggedIn")
        check(state.user.scopeKey == payload.scopeKey) { "public session scope mismatch" }
        check(state.user.userIdentity == payload.userIdentity) { "public session role mismatch" }

        val lease = userStorageLeaseAccess.requireCurrentLease()
        check(lease.scopeKey == payload.scopeKey) { "user namespace scope mismatch" }
        val auth = sessionSecretProvider.requestAuthSnapshot()
            ?: error("encrypted session auth snapshot is unavailable")
        check(auth.scopeKey == lease.scopeKey) { "session namespace mismatch" }
        check(auth.sessionEpoch == lease.sessionEpoch) { "session epoch mismatch" }
        check(auth.userIdentity == payload.userIdentity) { "secret session role mismatch" }
        check(auth.token == payload.token) { "secret session token mismatch" }
        check(sessionSecretProvider.activeSessionFingerprint()?.startsWith(
            "${payload.scopeKey.namespaceId().value}:",
        ) == true) { "active session fingerprint mismatch" }

        verifySessionEnvelopeIsEncrypted(payload.token)
    }

    private suspend fun verifySessionEnvelopeIsEncrypted(token: String) = withContext(Dispatchers.IO) {
        val sessionFile = File(noBackupFilesDir, "session/longcare_session_v1.preferences_pb")
        check(sessionFile.isFile && sessionFile.length() > 0L) { "encrypted session file is missing" }
        check(!sessionFile.readBytes().containsSubsequence(token.toByteArray())) {
            "session token leaked as plaintext"
        }
    }

    private sealed interface SetupStatus {
        val message: String
        val resultTag: String

        data object Running : SetupStatus {
            override val message: String = "preparing"
            override val resultTag: String = SETUP_RUNNING_TAG
        }

        data class Complete(private val scenarioId: String) : SetupStatus {
            override val message: String = "complete:$scenarioId"
            override val resultTag: String = SETUP_COMPLETE_TAG
        }

        data class Failed(private val reason: String) : SetupStatus {
            override val message: String = "failed:$reason"
            override val resultTag: String = SETUP_FAILED_TAG
        }
    }

    private enum class ProfileSetupScenario(val wireId: String) {
        FIRST_RUN_PRIVACY("first_run_privacy"),
        LOGGED_OUT("logged_out"),
        CARE_HOME("care_home"),
        SALES_HOME("sales_home"),
        CARE_SERVICE_RECORDS("care_service_records"),
        SALES_CUSTOMERS("sales_customers");

        companion object {
            fun fromWireId(wireId: String?): ProfileSetupScenario? =
                entries.singleOrNull { it.wireId == wireId }
        }
    }

    companion object {
        const val EXTRA_SCENARIO_ID = "profile_scenario_id"
        const val SETUP_ROOT_TAG = "profile_setup_root"
        const val SETUP_RUNNING_TAG = "profile_setup_running"
        const val SETUP_COMPLETE_TAG = "profile_setup_complete"
        const val SETUP_FAILED_TAG = "profile_setup_failed"

        private const val PROFILE_FIXTURE_TOKEN_CARE = "longcare-profile-fixture-care-token"
        private const val PROFILE_FIXTURE_TOKEN_SALES = "longcare-profile-fixture-sales-token"

        private val CARE_PROFILE_PAYLOAD = SessionLoginPayload(
            companyId = 99001,
            accountId = 99011,
            userId = 99021,
            userName = "Profile Care User",
            headUrl = "",
            userIdentity = 1,
            identityCardNumber = "000000000000000001",
            gender = 0,
            token = PROFILE_FIXTURE_TOKEN_CARE,
        )

        private val SALES_PROFILE_PAYLOAD = SessionLoginPayload(
            companyId = 99002,
            accountId = 99012,
            userId = 99022,
            userName = "Profile Sales User",
            headUrl = "",
            userIdentity = 2,
            identityCardNumber = "000000000000000002",
            gender = 0,
            token = PROFILE_FIXTURE_TOKEN_SALES,
        )
    }
}

private fun ByteArray.containsSubsequence(candidate: ByteArray): Boolean {
    if (candidate.isEmpty() || candidate.size > size) return false
    return indices
        .take(size - candidate.size + 1)
        .any { offset -> candidate.indices.all { index -> this[offset + index] == candidate[index] } }
}
