package com.ytone.longcare.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ytone.longcare.domain.model.User
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
    companion object{
        private const val STOP_TIMEOUT = 5000L
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStoreInstances = ConcurrentHashMap<Int, DataStore<Preferences>>()

    init {
        // 在初始化时启动一个协程来观察用户ID的变化
        coroutineScope.launch {
            userSessionRepository.currentUser
                // 关键修改：为 null 指定类型，帮助编译器正确推断
                .runningFold(null as User? to null as User?) { acc, current ->
                    acc.second to current
                }
                .collect { (previousUser, currentUser) ->
                    if (previousUser != null && currentUser == null) {
                        onUserLogout(previousUser)
                    }
                }
        }
    }

    val userDataStore: StateFlow<DataStore<Preferences>?> = userSessionRepository.currentUser
        .map { user -> user?.let { getOrCreateDataStoreForUser(it.userId) } }
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

    /**
     * 当用户登出时，只从内存缓存中移除 DataStore 实例的引用。
     * 这个方法现在是 private 的，由内部的 init 块调用。
     */
    private fun onUserLogout(user: User) {
        dataStoreInstances.remove(user.userId)
    }
}
