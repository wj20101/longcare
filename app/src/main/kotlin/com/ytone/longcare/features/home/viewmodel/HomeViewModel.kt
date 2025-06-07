package com.ytone.longcare.features.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.data.storage.UserSpecificStorageManager
import com.ytone.longcare.domain.usecase.GetSampleDataUseCase
import com.tencent.mmkv.MMKV
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getSampleDataUseCase: GetSampleDataUseCase,
    private val userSpecificStorageManager: UserSpecificStorageManager
) : ViewModel() {

    private val _sampleData = MutableStateFlow("")
    val sampleData: StateFlow<String> = _sampleData

    val userMMKV: StateFlow<MMKV?> = userSpecificStorageManager.userMMKV

    fun loadSampleData() {
        viewModelScope.launch {
            _sampleData.value = getSampleDataUseCase()
        }
    }

    fun saveUserPreference(key: String, value: String) {
        userSpecificStorageManager.putString(key, value)
    }

    fun getUserPreference(key: String, defaultValue: String): String {
        return userSpecificStorageManager.getString(key, defaultValue) ?: defaultValue
    }
}
