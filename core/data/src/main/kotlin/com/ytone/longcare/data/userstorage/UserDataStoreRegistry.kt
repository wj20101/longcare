package com.ytone.longcare.data.userstorage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.model.UserScopeKey
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

private val metadataFormatKey = intPreferencesKey("user_namespace_format")
private val metadataNamespaceKey = stringPreferencesKey("user_namespace_id")
private val metadataCompanyKey = intPreferencesKey("user_namespace_company_id")
private val metadataAccountKey = intPreferencesKey("user_namespace_account_id")
private val metadataUserKey = intPreferencesKey("user_namespace_user_id")

@Singleton
class UserDataStoreRegistry @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val stores = mutableMapOf<String, DataStore<Preferences>>()

    fun getOrCreate(file: File): DataStore<Preferences> {
        val canonicalFile = file.canonicalFile
        canonicalFile.parentFile?.let { parent ->
            check(parent.exists() || parent.mkdirs()) { "Unable to create DataStore directory" }
        }
        return synchronized(stores) {
            stores.getOrPut(canonicalFile.path) {
                PreferenceDataStoreFactory.create(scope = applicationScope) { canonicalFile }
            }
        }
    }

    suspend fun verifyOrInitializeOwnership(
        dataStore: DataStore<Preferences>,
        scopeKey: UserScopeKey,
    ) {
        val preferences = dataStore.data.first()
        val values = listOf(
            preferences[metadataFormatKey],
            preferences[metadataNamespaceKey],
            preferences[metadataCompanyKey],
            preferences[metadataAccountKey],
            preferences[metadataUserKey],
        )
        val initializedCount = values.count { it != null }
        if (initializedCount == 0) {
            dataStore.edit { mutable ->
                mutable[metadataFormatKey] = UserNamespaceMetadata.CURRENT_FORMAT_VERSION
                mutable[metadataNamespaceKey] = scopeKey.namespaceId().value
                mutable[metadataCompanyKey] = scopeKey.companyId
                mutable[metadataAccountKey] = scopeKey.accountId
                mutable[metadataUserKey] = scopeKey.userId
            }
            return
        }
        if (
            initializedCount != values.size ||
            preferences[metadataFormatKey] != UserNamespaceMetadata.CURRENT_FORMAT_VERSION ||
            preferences[metadataNamespaceKey] != scopeKey.namespaceId().value ||
            preferences[metadataCompanyKey] != scopeKey.companyId ||
            preferences[metadataAccountKey] != scopeKey.accountId ||
            preferences[metadataUserKey] != scopeKey.userId
        ) {
            throw NamespaceOwnershipException("DataStore metadata does not match requested scope")
        }
    }

    internal fun cachedInstanceCount(): Int = synchronized(stores) { stores.size }
}
