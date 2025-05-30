package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.ytone.longcare.R
import com.ytone.longcare.features.maindashboard.viewmodel.MainDashboardViewModel
import com.ytone.longcare.features.nursing.ui.NursingScreen
import com.ytone.longcare.features.profile.ui.ProfileScreen
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.ui.AccentOrange
import com.ytone.longcare.ui.BadgeYellow
import com.ytone.longcare.ui.BookmarkIconBlue
import com.ytone.longcare.ui.BottomNavSelectedColor
import com.ytone.longcare.ui.BottomNavUnselectedColor
import com.ytone.longcare.ui.ChatIconBlue
import com.ytone.longcare.ui.ClockIconBlue
import com.ytone.longcare.ui.HeartIconBlue
import com.ytone.longcare.ui.LightGrayText
import com.ytone.longcare.ui.ServiceItemHourColor
import com.ytone.longcare.ui.TextPrimary

@Composable
fun MainDashboardScreen(
    navController: NavController, // Added NavController for navigation
    mainDashboardViewModel: MainDashboardViewModel = hiltViewModel()
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), // Added horizontal padding for consistency
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { PromotionBanner() } // Uses ConstraintLayout
        item { QuickAccessGrid() } // Grid itself is Row/Column, cards use ConstraintLayout
        item { ServicePlanSectionTitle() }
        items(getDummyServicePlans()) { plan ->
            ServicePlanItem(plan) // Uses ConstraintLayout
        }
    }
}

@Composable
fun MainDashboardTopBar() {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val (logo, appNameText, userInfoColumn, avatarImage) = createRefs()

        Image(
            painter = painterResource(id = R.drawable.app_logo_small),
            contentDescription = "长护盾 Logo",
            modifier = Modifier
                .size(32.dp)
                .constrainAs(logo) {
                    start.linkTo(parent.start)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                }
        )

        Text(
            text = "长护盾",
            style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onPrimary),
            modifier = Modifier.constrainAs(appNameText) {
                start.linkTo(logo.end, margin = 8.dp)
                top.linkTo(logo.top)
                bottom.linkTo(logo.bottom)
            }
        )

        Image(
            painter = painterResource(id = R.drawable.app_logo_small),
            contentDescription = "用户头像",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .constrainAs(avatarImage) {
                    end.linkTo(parent.end)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                }
        )

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.constrainAs(userInfoColumn) {
                end.linkTo(avatarImage.start, margin = 12.dp)
                top.linkTo(avatarImage.top)
                bottom.linkTo(avatarImage.bottom)
                // Optional: If you want the user info to fill space if appNameText is short
                // start.linkTo(appNameText.end, margin = 8.dp, goneMargin = 8.dp)
                // width = Dimension.fillToConstraints
            }
        ) {
            Text(
                text = "张默默",
                style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onPrimary)
            )
            Text(
                text = "护理员",
                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
            )
        }
    }
}

@Composable
fun PromotionBanner() {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
    ) {
        // Guideline to help position the image relative to the card content
        val imageStartGuideline = createGuidelineFromStart(0.58f) // Image will start from 58% of width

        val (cardContent, bannerImage) = createRefs()

        Card(
            modifier = Modifier
                .constrainAs(cardContent) {
                    start.linkTo(parent.start)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    end.linkTo(imageStartGuideline, margin = 16.dp) // Card ends before the image fully starts, allowing visual overlap by image
                    width = Dimension.fillToConstraints
                    height = Dimension.fillToConstraints
                },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 16.dp), // Adjusted end padding
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "援通管家",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = TextPrimary
                            )
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(AccentOrange.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "居家养老",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = AccentOrange
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "为每个家庭提供个性化养老解决方案",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp)
                    )
                }
                Button(
                    onClick = { /*TODO*/ },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("查看详细方案", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Image(
            painter = painterResource(id = R.drawable.app_logo_small),
            contentDescription = "居家养老服务图",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .constrainAs(bannerImage) {
                    start.linkTo(imageStartGuideline) // Image starts at the guideline
                    end.linkTo(parent.end)
                    top.linkTo(parent.top, margin = (170.dp * 0.015f)) // Small top/bottom margin for visual appeal
                    bottom.linkTo(parent.bottom, margin = (170.dp * 0.015f))
                    width = Dimension.fillToConstraints // Image fills from guideline to parent end
                    height = Dimension.fillToConstraints // Image height fills constrained space
                }
                .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp, topStart = 8.dp, bottomStart = 8.dp))
        )
    }
}


// --- Quick Access Section (QuickAccessCard uses ConstraintLayout) ---
data class QuickAccessItemData(
    val title: String,
    val subtitle: String,
    val iconPainter: Painter,
    val iconBackgroundColor: Color,
    val badgeText: String? = null
)

@Composable
fun QuickAccessGrid() {
    val items = listOf(
        QuickAccessItemData("待护理计划", "你有12个护理待执行", painterResource(id = R.drawable.app_logo_small), HeartIconBlue.copy(alpha = 0.1f), "12"),
        QuickAccessItemData("已服务记录", "查看过往服务记录", painterResource(id = R.drawable.app_logo_small), ChatIconBlue.copy(alpha = 0.1f)),
        QuickAccessItemData("工时: 22322", "服务工时统计", painterResource(id = R.drawable.app_logo_small), ClockIconBlue.copy(alpha = 0.1f)),
        QuickAccessItemData("待学习", "服务标准学习", painterResource(id = R.drawable.app_logo_small), BookmarkIconBlue.copy(alpha = 0.1f), "12")
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAccessCard(item = items[0], modifier = Modifier.weight(1f))
            QuickAccessCard(item = items[1], modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickAccessCard(item = items[2], modifier = Modifier.weight(1f))
            QuickAccessCard(item = items[3], modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAccessCard(item: QuickAccessItemData, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(100.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        ConstraintLayout(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp) // Padding inside card, outside CL
                .fillMaxSize()
        ) {
            val (iconRef, titleRowRef, subtitleRef) = createRefs()
            val centerVerticalGuideline = createGuidelineFromTop(0.5f) // Guideline for vertical centering of text

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(item.iconBackgroundColor)
                    .constrainAs(iconRef) {
                        start.linkTo(parent.start)
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        centerVerticallyTo(parent) // Alternative to top/bottom linking
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = item.iconPainter,
                    contentDescription = item.title,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(22.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.constrainAs(titleRowRef) {
                    start.linkTo(iconRef.end, margin = 10.dp)
                    end.linkTo(parent.end)
                    bottom.linkTo(centerVerticalGuideline, margin = 1.dp) // Title part above subtitle
                    width = Dimension.fillToConstraints
                }
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                item.badgeText?.let {
                    Spacer(modifier = Modifier.width(4.dp))
                    Badge(
                        containerColor = BadgeYellow,
                        contentColor = TextPrimary
                    ) { Text(it, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            }

            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                maxLines = 1,
                modifier = Modifier.constrainAs(subtitleRef) {
                    start.linkTo(titleRowRef.start) // Align with title's start
                    end.linkTo(parent.end)
                    top.linkTo(centerVerticalGuideline, margin = 1.dp) // Subtitle part below title
                    width = Dimension.fillToConstraints
                }
            )
        }
    }
}


// --- Service Plan Section (ServicePlanItem uses ConstraintLayout) ---
@Composable
fun ServicePlanSectionTitle() {
    Text(
        text = "待服务计划",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp) // Added top padding for spacing
    )
}

data class ServicePlan(
    val id: String, val name: String, val hours: String,
    val serviceType: String, val address: String
)

fun getDummyServicePlans(): List<ServicePlan> = List(3) {
    ServicePlan("id_$it", "孙天成", "8", "助浴服务", "杭州市西湖区328弄24号")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicePlanItem(plan: ServicePlan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = { /* TODO: Navigate */ }
    ) {
        ConstraintLayout(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .fillMaxWidth()
        ) {
            val (contentColumnRef, arrowIconRef) = createRefs()

            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "查看详情",
                tint = LightGrayText,
                modifier = Modifier.constrainAs(arrowIconRef) {
                    end.linkTo(parent.end)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    centerVerticallyTo(parent)
                }
            )

            Column(
                modifier = Modifier.constrainAs(contentColumnRef) {
                    start.linkTo(parent.start)
                    end.linkTo(arrowIconRef.start, margin = 8.dp)
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    width = Dimension.fillToConstraints // Fill available space
                }
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(plan.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "工时: ${plan.hours}",
                        style = MaterialTheme.typography.labelSmall.copy(color = ServiceItemHourColor, fontSize = 13.sp),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(plan.serviceType, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "地址: ${plan.address}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    maxLines = 1
                )
            }
        }
    }
}

// --- Preview ---
@Preview(showBackground = true, backgroundColor = 0xFFF0F5FF)
@Composable
fun MainDashboardScreenPreview() {
    LongCareTheme {
        MainDashboardScreen(navController = rememberNavController())
    }
}
