package com.ytone.longcare.features.home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.R // Assuming your logo is in drawable
import com.ytone.longcare.features.home.viewmodel.HomeViewModel
import com.ytone.longcare.theme.LongCareTheme
import java.text.SimpleDateFormat
import java.util.*

// Data class for grid items
data class HomeGridItem(val title: String, val icon: ImageVector)

@Composable
fun HomeScreen(
    navController: NavController, // Added NavController for navigation
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val currentUserMMKV by homeViewModel.userMMKV.collectAsState() // Observe user-specific MMKV via ViewModel
    var userSpecificData by remember { mutableStateOf("N/A") }

    // Effect to load initial user preference
    LaunchedEffect(currentUserMMKV) {
        userSpecificData = homeViewModel.getUserPreference("user_preference", "Not Set")
    }

    val gridItems = listOf(
        HomeGridItem("上班打卡", Icons.Filled.Notifications),
        HomeGridItem("下班打卡", Icons.Filled.Notifications),
        HomeGridItem("开始服务", Icons.Filled.Notifications),
        HomeGridItem("结束服务", Icons.Filled.Notifications),
        HomeGridItem("服务记录", Icons.Filled.Notifications),
        HomeGridItem("排班计划", Icons.Filled.Notifications),
        HomeGridItem("异常上报", Icons.Filled.Notifications),
        HomeGridItem("消息通知", Icons.Filled.Notifications)
    )

    Scaffold(
        topBar = {
            HomeTopAppBar(onNotificationClick = { /* TODO: Handle notification click */ })
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            GreetingSection()
            Spacer(modifier = Modifier.height(24.dp))
            HomeGrid(items = gridItems, onItemClick = { /* TODO: Handle grid item click */ })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(onNotificationClick: () -> Unit) {
    TopAppBar(
        title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground), // Replace with your actual logo
                contentDescription = "App Logo", modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "颐康通",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }, actions = {
        IconButton(onClick = onNotificationClick) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "Notifications",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }, colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) // Semi-transparent
    )
    )
}

@Composable
fun GreetingSection() {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "上午好"
        in 12..17 -> "下午好"
        else -> "晚上好"
    }
    val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA)
    val currentDate = dateFormat.format(Date())

    Column {
        Text(
            text = "$greeting, 护理员",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = currentDate, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HomeGrid(items: List<HomeGridItem>, onItemClick: (HomeGridItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items) { item ->
            GridItemCard(item = item, onClick = { onItemClick(item) })
        }
    }
}

@Composable
fun GridItemCard(item: HomeGridItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .aspectRatio(1f) // Makes the card square
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.title,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}


@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    LongCareTheme {
        HomeScreen(navController = rememberNavController())
    }
}
