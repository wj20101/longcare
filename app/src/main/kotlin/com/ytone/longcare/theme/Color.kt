package com.ytone.longcare.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val bgGradientBrush = Brush.verticalGradient(colors = listOf(Color(0xFF468AFF), Color(0xFFF6F9FF)))

val PrimaryBlue = Color(0xFF4A80F0)
val LightBlueBackground = Color(0xFFF0F5FF) // 主背景的浅蓝色调
val CardWhite = Color.White
val TextPrimary = Color(0xFF333333)
val TextSecondary = Color(0xFF757575)
val TextOnPrimary = Color.White
val AccentOrange = Color(0xFFFF8C00) // "居家养老" 等强调色
val BadgeYellow = Color(0xFFFFD700)
val LightGrayText = Color(0xFFB0B0B0)
val ServiceItemHourColor = Color(0xFF6CACFF) // "工时: 8" 的颜色

// Icon specific colors (from design, approximate)
val HeartIconBlue = Color(0xFF4A80F0)
val HeartIconGreen = Color(0xFF6DD4A1) // 心形图标的绿色部分 (需要自定义绘制或SVG)
val ChatIconBlue = Color(0xFF4A80F0)
val ChatIconGreen = Color(0xFF6DD4A1) // 对话图标的绿色部分
val ClockIconBlue = Color(0xFF4A80F0)
val ClockIconGreen = Color(0xFF6DD4A1) // 时钟图标的绿色部分
val BookmarkIconBlue = Color(0xFF4A80F0)
val BookmarkIconGreen = Color(0xFF6DD4A1) // 书签图标的绿色部分

val BottomNavSelectedColor = PrimaryBlue
val BottomNavUnselectedColor = Color(0xFF8A8A8F)

val LogoutRed = Color(0xFFE53935) // A guess for logout button, adjust from design
val IconInfoBlue = Color(0xFF2962FF) // For "信息上报" and "设置" icons
val IconProfileOrange = Color(0xFFF57C00) // For "个人信息" icon
val DividerColor = Color(0xFFEEEEEE)

val TextColorPrimary = Color(0xFF333333)
val TextColorSecondary = Color(0xFF757575)
val TextColorHint = Color(0xFFBABABA)
val LinkColor = PrimaryBlue
val InputFieldBackground = Color.White
val InputFieldBorderColor = Color(0xFFE8E8E8)

val StatusRed = Color(0xFFD32F2F)
val StatusGreen = Color(0xFF388E3C)
val DatePillSelectedBackground = Color.White
val DatePillSelectedText = PrimaryBlue
val DatePillUnselectedText = Color.White.copy(alpha = 0.85f)

val BottomNavBackground = Color.White // 底部导航栏背景色
val BottomNavSelectedText = Color(0xFF0D7EFF) // 选中项文字颜色 (设计图中的蓝色)
val BottomNavUnselectedText = Color(0xFF8A8A8F) // 未选中项文字颜色 (设计图中的灰色)

// 指示器渐变色
val IndicatorGradientStart = Color(0xFF00A2FF) // 渐变起始色 (蓝色)
val IndicatorGradientEnd = Color(0xFF00FFC2)   // 渐变结束色 (青绿色)