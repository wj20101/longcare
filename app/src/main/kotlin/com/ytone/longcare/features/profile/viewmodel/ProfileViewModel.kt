package com.ytone.longcare.features.profile.viewmodel

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.data.storage.UserSpecificDataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val dataStoreManager: UserSpecificDataStoreManager
) : ViewModel() {

    companion object{
        private const val STOP_TIMEOUT = 5000L
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val userName: StateFlow<String?> = dataStoreManager.userDataStore.flatMapLatest { dataStore ->
        dataStore?.data?.map { preferences ->
            preferences[stringPreferencesKey("user_name")]
        } ?: flowOf(null) // Emit null if DataStore is null (user logged out)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    fun updateUserName(newName: String) {
        viewModelScope.launch {
            dataStoreManager.userDataStore.value?.edit { settings ->
                settings[stringPreferencesKey("user_name")] = newName
            }
        }
    }
}