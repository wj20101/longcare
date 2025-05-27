package com.ytone.longcare.features.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Person // Placeholder for 个人信息
import androidx.compose.material.icons.outlined.Settings // Placeholder for 设置
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.R
import com.ytone.longcare.features.profile.viewmodel.ProfileViewModel
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.ui.CardWhite
import com.ytone.longcare.ui.DividerColor
import com.ytone.longcare.ui.IconInfoBlue
import com.ytone.longcare.ui.IconProfileOrange
import com.ytone.longcare.ui.LightBlueBackground
import com.ytone.longcare.ui.LightGrayText
import com.ytone.longcare.ui.LogoutRed
import com.ytone.longcare.ui.PrimaryBlue
import com.ytone.longcare.ui.TextOnPrimary
import com.ytone.longcare.ui.TextPrimary
import com.ytone.longcare.ui.TextSecondary

@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel() // Assuming Hilt ViewModel
) {
    Scaffold(
        topBar = {
            ProfileTopAppBar(
                title = "我的", onBackClick = { navController.popBackStack() })
        }, containerColor = LightBlueBackground // Sets the background for the area under TopAppBar
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Blue header section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaryBlue) // Blue background for this section
                    .padding(
                        start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp
                    ) // Bottom padding creates space before MenuItemsCard
            ) {
                ProfileHeader(
                    avatarRes = R.drawable.app_logo_small, // Replace with actual resource
                    name = "张默默", role = "护理员"
                )
                Spacer(modifier = Modifier.height(24.dp))
                StatsInfoCard(
                    stats = listOf(
                        StatData("2321", "已服务工时"),
                        StatData("122", "服务次数"),
                        StatData("223", "未服务工时")
                    )
                )
            }

            // Menu Items on LightBlueBackground
            ProfileMenuItems(
                modifier = Modifier.padding(horizontal = 16.dp)
                // The top of this card aligns with where the blue background ended due to parent Column's padding.
                // If an overlap effect where this card slightly enters the blue area is desired,
                // you could use .offset(y = (-YY).dp) here and adjust parent's bottom padding.
                // From the image, it seems there's a clean separation.
            )

            Spacer(modifier = Modifier.height(32.dp))

            LogoutButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp), // Wider horizontal padding for logout button
                onClick = { /* TODO: Handle logout */ })

            Spacer(Modifier.weight(1f)) // Pushes content up if screen is tall
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTopAppBar(title: String, onBackClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onPrimary),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth() // Center title
            )
        }, navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }, actions = {
            // Invisible spacer to balance the navigation icon and center the title correctly
            Spacer(modifier = Modifier.width(48.dp)) // Width of IconButton
        }, colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
fun ProfileHeader(
    modifier: Modifier = Modifier, avatarRes: Int, name: String, role: String
) {
    Row(
        modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = avatarRes),
            contentDescription = "用户头像",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = name, style = MaterialTheme.typography.titleLarge.copy(
                    color = TextOnPrimary, fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = role,
                style = MaterialTheme.typography.bodyMedium.copy(color = TextOnPrimary.copy(alpha = 0.8f))
            )
        }
    }
}

data class StatData(val value: String, val label: String)

@Composable
fun StatsInfoCard(modifier: Modifier = Modifier, stats: List<StatData>) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large, // Rounded corners
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // Slight elevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround // Distributes items evenly
        ) {
            stats.forEach { stat ->
                StatItem(value = stat.value, label = stat.label, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatItem(modifier: Modifier = Modifier, value: String, label: String) {
    Column(
        modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value, style = MaterialTheme.typography.labelMedium, color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label, style = MaterialTheme.typography.labelMedium, color = TextSecondary
        )
    }
}

data class ProfileMenuItem(
    val id: String,
    val title: String,
    val icon: ImageVector, // Or Painter if using drawable resources
    val iconTint: Color,
    val action: () -> Unit
)

@Composable
fun ProfileMenuItems(modifier: Modifier = Modifier) {
    val menuItems = listOf(
        ProfileMenuItem("info_report", "信息上报", Icons.Outlined.Person, IconInfoBlue, {}),
        ProfileMenuItem("personal_info", "个人信息", Icons.Outlined.Person, IconProfileOrange, {}),
        ProfileMenuItem("settings", "设置", Icons.Outlined.Settings, IconInfoBlue, {})
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            menuItems.forEachIndexed { index, item ->
                MenuItemRow(
                    icon = item.icon,
                    iconTint = item.iconTint,
                    text = item.title,
                    onClick = item.action
                )
                if (index < menuItems.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = DividerColor
                    )
                }
            }
        }
    }
}

@Composable
fun MenuItemRow(
    icon: ImageVector, iconTint: Color, text: String, onClick: () -> Unit
) {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        val (iconRef, textRef, arrowRef) = createRefs()

        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = iconTint,
            modifier = Modifier
                .size(24.dp)
                .constrainAs(iconRef) {
                    start.linkTo(parent.start)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    centerVerticallyTo(parent)
                })

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.constrainAs(textRef) {
                start.linkTo(iconRef.end, margin = 16.dp)
                end.linkTo(arrowRef.start, margin = 8.dp)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                centerVerticallyTo(parent)
                width = Dimension.fillToConstraints // Important for text to occupy available space
            })

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "详情",
            tint = LightGrayText,
            modifier = Modifier.constrainAs(arrowRef) {
                end.linkTo(parent.end)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                centerVerticallyTo(parent)
            })
    }
}

@Composable
fun LogoutButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button( // Using regular Button and styling it to look like OutlinedButton with specific colors
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(50), // Fully rounded
        colors = ButtonDefaults.buttonColors(
            containerColor = CardWhite, // White background
            contentColor = LogoutRed    // Red text
        ),
        border = BorderStroke(1.dp, LogoutRed) // Red border
    ) {
        Text(
            text = "退出登录",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}


@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    LongCareTheme {
        ProfileScreen(navController = rememberNavController())
    }
}