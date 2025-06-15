package com.ytone.longcare.features.maindashboard.viewmodel

import androidx.lifecycle.ViewModel
import com.ytone.longcare.domain.repository.UserSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainDashboardViewModel @Inject constructor(val userSessionRepository: UserSessionRepository) :
    ViewModel() {

}