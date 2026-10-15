package com.ytone.longcare.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ytone.longcare.di.AppDataStore
import com.ytone.longcare.domain.location.LocationAccount
import com.ytone.longcare.domain.location.LocationRecoveryRecord
import com.ytone.longcare.domain.location.LocationRecoveryStore
import com.ytone.longcare.model.User
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class DataStoreLocationRecoveryStore @Inject constructor(
    @param:AppDataStore private val store: DataStore<Preferences>,
) : LocationRecoveryStore {
    override suspend fun read(): LocationRecoveryRecord? {
        val values = store.data.first()
        val owner = values[SESSION]?.takeIf { it.isNotBlank() } ?: return null
        val order = values[ORDER]?.takeIf { it > 0 } ?: return null
        val company = values[COMPANY] ?: return null
        val account = values[ACCOUNT] ?: return null
        val user = values[USER] ?: return null
        return LocationRecoveryRecord(LocationAccount(company, account, user), order, owner)
    }

    override suspend fun save(record: LocationRecoveryRecord) {
        require(record.orderId > 0 && record.sessionId.isNotBlank())
        store.edit { values ->
            // Match the durable account in the SAME transaction as the write, including logout races.
            val user = values[APP_USER_KEY]?.let {
                runCatching { User.ADAPTER.decode(it) }.getOrNull()
            }
            if (user == null || LocationAccount(user.companyId, user.accountId, user.userId) != record.account) {
                return@edit
            }
            values[COMPANY] = record.account.companyId
            values[ACCOUNT] = record.account.accountId
            values[USER] = record.account.userId
            values[ORDER] = record.orderId
            values[SESSION] = record.sessionId
        }
    }

    override suspend fun clear(sessionId: String?) {
        store.edit { if (sessionId == null || it[SESSION] == sessionId) clearLocationRecovery(it) }
    }

    companion object {
        private val COMPANY = intPreferencesKey("location_recovery_company")
        private val ACCOUNT = intPreferencesKey("location_recovery_account")
        private val USER = intPreferencesKey("location_recovery_user")
        private val ORDER = longPreferencesKey("location_recovery_order")
        private val SESSION = stringPreferencesKey("location_recovery_session")

        internal fun clearLocationRecovery(values: androidx.datastore.preferences.core.MutablePreferences) {
            values.remove(COMPANY); values.remove(ACCOUNT); values.remove(USER)
            values.remove(ORDER); values.remove(SESSION)
        }
    }
}
