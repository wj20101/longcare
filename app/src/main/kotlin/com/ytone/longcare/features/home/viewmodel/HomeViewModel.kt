package com.ytone.longcare.features.home.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.domain.usecase.GetSampleDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getSampleDataUseCase: GetSampleDataUseCase,
) : ViewModel() {

    private val _sampleData = MutableStateFlow("")
    val sampleData: StateFlow<String> = _sampleData

    fun loadSampleData() {
        viewModelScope.launch {
            _sampleData.value = getSampleDataUseCase()
        }
    }
}
