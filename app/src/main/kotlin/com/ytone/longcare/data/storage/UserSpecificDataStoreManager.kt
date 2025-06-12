package com.ytone.longcare.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ytone.longcare.di.ApplicationScope
import com.ytone.longcare.domain.repository.UserSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSpecificDataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val coroutineScope: CoroutineScope,
    private val userSessionRepository: UserSessionRepository
) {
    companion object {
        private const val STOP_TIMEOUT = 5000L
    }

    private val dataStoreInstances = ConcurrentHashMap<Int, DataStore<Preferences>>()

    val userDataStore: StateFlow<DataStore<Preferences>?> = userSessionRepository.sessionState
        .map { session -> session.user?.let { getOrCreateDataStoreForUser(it.userId) } }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT),
            initialValue = null
        )

    private fun getOrCreateDataStoreForUser(userId: Int): DataStore<Preferences> {
        return dataStoreInstances.getOrPut(userId) {
            preferencesDataStore(name = "user_${userId}_prefs").getValue(context, this::javaClass)
        }
    }
}
