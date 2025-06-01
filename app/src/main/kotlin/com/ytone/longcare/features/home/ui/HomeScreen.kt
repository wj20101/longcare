package com.ytone.longcare.features.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.features.home.viewmodel.HomeViewModel
import com.ytone.longcare.features.maindashboard.ui.MainDashboardScreen
import com.ytone.longcare.features.nursing.ui.NursingScreen
import com.ytone.longcare.features.profile.ui.ProfileScreen
import com.ytone.longcare.theme.LongCareTheme
import kotlinx.coroutines.launch


@Composable
fun HomeScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val bottomNavItems = listOf(
        CustomBottomNavigationItem("首页"),
        CustomBottomNavigationItem("护理工作"),
        CustomBottomNavigationItem("我的")
    )
    val pagerState = rememberPagerState(pageCount = { bottomNavItems.size })
    val coroutineScope = rememberCoroutineScope()

    val gradientBrush =
        Brush.verticalGradient(colors = listOf(Color(0xFF468AFF), Color(0xFFF6F9FF)))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = gradientBrush)
    ){
        Scaffold(
            bottomBar = {
                AppBottomNavigation(
                    items = bottomNavItems,
                    selectedItemIndex = pagerState.currentPage,
                    onItemSelected = {
                        coroutineScope.launch { pagerState.scrollToPage(it) }
                    }
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())
            ) { page ->
                when (page) {
                    0 -> MainDashboardScreen(navController = navController)
                    1 -> NursingScreen(navController = navController)
                    2 -> ProfileScreen(navController = navController)
                }
            }
        }
    }
}

@Preview
@Composable
fun HomeScreenPreview() {
    LongCareTheme {
        HomeScreen(navController = rememberNavController())
    }
}
