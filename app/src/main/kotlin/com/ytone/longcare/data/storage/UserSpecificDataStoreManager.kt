package com.ytone.longcare.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ytone.longcare.domain.repository.UserSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSpecificDataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userSessionRepository: UserSessionRepository
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStoreInstances = ConcurrentHashMap<String, DataStore<Preferences>>()

    init {
        // 在初始化时启动一个协程来观察用户ID的变化
        coroutineScope.launch {
            userSessionRepository.currentUserId
                // 关键修改：为 null 指定类型，帮助编译器正确推断
                .runningFold(null as String? to null as String?) { acc, current ->
                    acc.second to current
                }
                .collect { (previousUserId, currentUserId) ->
                    if (previousUserId != null && currentUserId == null) {
                        onUserLogout(previousUserId)
                    }
                }
        }
    }

    val userDataStore: StateFlow<DataStore<Preferences>?> = userSessionRepository.currentUserId
        .map { userId ->
            if (userId != null) {
                getOrCreateDataStoreForUser(userId)
            } else {
                null
            }
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private fun getOrCreateDataStoreForUser(userId: String): DataStore<Preferences> {
        return dataStoreInstances.getOrPut(userId) {
            preferencesDataStore(name = "user_${userId}_prefs").getValue(context, this::javaClass)
        }
    }

    /**
     * 当用户登出时，只从内存缓存中移除 DataStore 实例的引用。
     * 这个方法现在是 private 的，由内部的 init 块调用。
     */
    private fun onUserLogout(userId: String) {
        dataStoreInstances.remove(userId)
    }
}
