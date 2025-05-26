package com.ytone.longcare.features.home.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.R
import com.ytone.longcare.features.home.viewmodel.HomeViewModel
import com.ytone.longcare.features.maindashboard.ui.AppBottomNavigation
import com.ytone.longcare.features.maindashboard.ui.BottomNavItemData
import com.ytone.longcare.features.maindashboard.ui.MainDashboardScreen
import com.ytone.longcare.features.maindashboard.ui.MainDashboardTopBar
import com.ytone.longcare.features.nursing.ui.NursingScreen
import com.ytone.longcare.features.profile.ui.ProfileScreen
import com.ytone.longcare.theme.LongCareTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController, // Added NavController for navigation
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    var selectedBottomNavItem by remember { mutableIntStateOf(0) }
    val bottomNavItems = listOf(
        BottomNavItemData("首页", R.drawable.app_logo_small, R.drawable.app_logo_small),
        BottomNavItemData("护理工作", R.drawable.app_logo_small, R.drawable.app_logo_small),
        BottomNavItemData("我的", R.drawable.app_logo_small, R.drawable.app_logo_small)
    )

    Scaffold(
        topBar = { MainDashboardTopBar() }, // Uses ConstraintLayout
        bottomBar = {
            AppBottomNavigation(
                items = bottomNavItems,
                selectedItemIndex = selectedBottomNavItem,
                onItemSelected = { selectedBottomNavItem = it }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedBottomNavItem) {
                0 -> MainDashboardScreen(navController = navController)
                1 -> NursingScreen(navController = navController)
                2 -> ProfileScreen(navController = navController)
            }
        }
    }
}

// --- Preview ---
@Preview(showBackground = true, backgroundColor = 0xFFF0F5FF)
@Composable
fun HomeScreenPreview() {
    LongCareTheme {
        HomeScreen(navController = rememberNavController())
    }
}
