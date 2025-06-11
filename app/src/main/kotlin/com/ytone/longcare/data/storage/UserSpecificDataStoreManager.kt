package com.ytone.longcare.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.ytone.longcare.domain.repository.UserSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSpecificDataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userSessionRepository: UserSessionRepository
) {

    private val dataStoreCache = ConcurrentHashMap<String, DataStore<Preferences>>()

    private val _userDataStore = MutableStateFlow<DataStore<Preferences>?>(null)
    val userDataStore: StateFlow<DataStore<Preferences>?> = _userDataStore.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        coroutineScope.launch {
            userSessionRepository.currentUserId.collectLatest { userId ->
                    if (userId != null) {
                        val fileName = "user_session_${userId}_prefs"
                        val dataStore = dataStoreCache.getOrPut(userId) {
                            PreferenceDataStoreFactory.create(
                                produceFile = {
                                    context.filesDir.resolve("datastore/$fileName.preferences_pb").absoluteFile
                                }
                            )
                        }
                        _userDataStore.value = dataStore
                    } else {
                        _userDataStore.value = null
                    }
                }
        }
    }

    fun onUserLogout(userId: String) {
        dataStoreCache.remove(userId)
    }
}
