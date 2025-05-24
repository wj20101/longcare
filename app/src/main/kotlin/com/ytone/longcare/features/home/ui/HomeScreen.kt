package com.ytone.longcare.features.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ytone.longcare.features.home.viewmodel.HomeViewModel
import com.ytone.longcare.features.login.viewmodel.LoginViewModel // For logout
import com.ytone.longcare.navigation.AppDestinations

@Composable
fun HomeScreen(
    navController: NavController, // Added NavController for navigation
    homeViewModel: HomeViewModel = hiltViewModel(),
    loginViewModel: LoginViewModel = hiltViewModel() // For logout action
    // userStorageManager is now accessed via homeViewModel
) {
    val sampleData by homeViewModel.sampleData.collectAsState()
    val currentUserMMKV by homeViewModel.userMMKV.collectAsState() // Observe user-specific MMKV via ViewModel
    var userSpecificData by remember { mutableStateOf("N/A") }

    // Effect to load initial user preference
    LaunchedEffect(currentUserMMKV) {
        userSpecificData = homeViewModel.getUserPreference("user_preference", "Not Set")
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (sampleData.isEmpty()) {
            Button(onClick = {
                // Example: Save some user-specific data via ViewModel
                homeViewModel.saveUserPreference("user_preference", "dark_mode")
                // Example: Read user-specific data via ViewModel
                userSpecificData = homeViewModel.getUserPreference("user_preference", "Not Set")
            }) {
                Text("Test User Storage (Save/Load 'user_preference')")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { homeViewModel.loadSampleData() }) {
                Text("Load Sample Data")
            }
        } else {
            Text("Home Screen - Feature: home\nData: $sampleData\nCurrent User MMKV: ${currentUserMMKV?.mmapID() ?: "None"}\nUser Specific Data: $userSpecificData")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                // Example: Save some user-specific data via ViewModel
                homeViewModel.saveUserPreference("user_preference", "dark_mode")
                // Example: Read user-specific data via ViewModel
                userSpecificData = homeViewModel.getUserPreference("user_preference", "Not Set")
            }) {
                Text("Test User Storage (Save/Load 'user_preference')")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
            }) {
                Text("Test User Storage (Save/Load 'user_preference')")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                // Example: Save some user-specific data via ViewModel
                homeViewModel.saveUserPreference("user_preference", "dark_mode")
                // Example: Read user-specific data via ViewModel
                userSpecificData = homeViewModel.getUserPreference("user_preference", "Not Set")
            }) {
                Text("Test User Storage (Save/Load 'user_preference')")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                loginViewModel.logout() // Call logout on LoginViewModel
                // Navigate back to login screen
                navController.navigate(AppDestinations.LOGIN_ROUTE) {
                    popUpTo(AppDestinations.HOME_ROUTE) { inclusive = true } // Clear back stack to home
                    launchSingleTop = true // Avoid multiple copies of login screen
                }
            }) {
                Text("Logout")
            }
        }
    }
}
