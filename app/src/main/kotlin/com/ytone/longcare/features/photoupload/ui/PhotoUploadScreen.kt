package com.ytone.longcare.features.photoupload.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil3.compose.rememberAsyncImagePainter
import com.ytone.longcare.R
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.ui.screen.TagCategory

// --- 数据模型 ---
data class PhotoItem(
    val id: String,
    val imageUrl: String, // 本地 URI 或网络 URL
    @DrawableRes val placeholderRes: Int? = null // 如果是本地占位图资源
)

enum class PhotoCategory(val title: String, val tagCategory: TagCategory) {
    BEFORE_CARE("护理前照片", tagCategory = TagCategory.DEFAULT),
    DURING_CARE("护理中照片", tagCategory = TagCategory.ORANGE),
    AFTER_CARE("护理后照片", tagCategory = TagCategory.BLUE)
}

// --- 主屏幕入口 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoUploadScreen(navController: NavController, orderId: Long) {

    // 模拟图片数据状态
    var beforeCarePhotos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var duringCarePhotos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var afterCarePhotos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }

    // 模拟添加图片逻辑
    fun addPhoto(category: PhotoCategory) {
        val newItem = PhotoItem(
            id = System.currentTimeMillis().toString(),
            imageUrl = "", // 初始为空或占位符
        )
        when (category) {
            PhotoCategory.BEFORE_CARE -> beforeCarePhotos = beforeCarePhotos + newItem
            PhotoCategory.DURING_CARE -> duringCarePhotos = duringCarePhotos + newItem
            PhotoCategory.AFTER_CARE -> afterCarePhotos = afterCarePhotos + newItem
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.photo_upload_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { /* TODO: 返回操作 */ }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            },
            containerColor = Color.Transparent,
            bottomBar = { // 将按钮放在 bottomBar 中使其固定在底部
                Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    ConfirmAndNextButton(
                        text = stringResource(R.string.photo_upload_confirm_and_next),
                        onClick = { /* TODO: 下一步逻辑 */ }
                    )
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues) // 应用来自Scaffold的padding (包括了底部按钮的空间)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp) // 为列表底部额外增加一些边距
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.photo_upload_description),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    PhotoUploadSection(
                        category = PhotoCategory.BEFORE_CARE,
                        photos = beforeCarePhotos,
                        onAddPhoto = { addPhoto(PhotoCategory.BEFORE_CARE) }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                item {
                    PhotoUploadSection(
                        category = PhotoCategory.DURING_CARE,
                        photos = duringCarePhotos,
                        onAddPhoto = { addPhoto(PhotoCategory.DURING_CARE) }
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                item {
                    PhotoUploadSection(
                        category = PhotoCategory.AFTER_CARE,
                        photos = afterCarePhotos,
                        onAddPhoto = { addPhoto(PhotoCategory.AFTER_CARE) }
                    )
                    Spacer(modifier = Modifier.height(20.dp)) // 额外的底部间距
                }
            }
        }
    }
}

@Composable
fun PhotoUploadSection(
    category: PhotoCategory,
    photos: List<PhotoItem>,
    onAddPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {

    Box(modifier = modifier) {
        // 图片列表背景卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(86.dp),
                modifier = Modifier
                    .padding(
                        start = 20.dp,
                        top = 20.dp,
                        end = 20.dp,
                        bottom = 18.dp
                    )
                    .heightIn(max = 300.dp), // 限制最大高度避免无限约束
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 添加照片按钮作为第一个item
                item {
                    AddPhotoButton(onClick = onAddPhoto)
                }
                // 后续的图片items
                items(photos) { photo ->
                    UploadedImageItem(photo = photo, onClick = { /* TODO */ })
                }
            }
        }

        ServiceHoursTag(tagText = category.title, tagCategory = category.tagCategory)

    }
}

@Composable
fun AddPhotoButton(onClick: () -> Unit) {
    val lineColor = Color(0xFF2C87FE)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(1.dp, color = lineColor, shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.photo_upload_add_photo_description),
            tint = lineColor,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.photo_upload_add_photo),
            fontSize = 12.sp,
            color = lineColor
        )
    }
}

@Composable
fun UploadedImageItem(photo: PhotoItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.LightGray) // 图片加载时的占位背景
            .clickable(onClick = onClick)
    ) {
        Image(
            // 实际应使用 Coil 等库加载 photo.imageUrl
            painter = rememberAsyncImagePainter(
                photo.imageUrl,
                placeholder = if (photo.placeholderRes != null) painterResource(photo.placeholderRes) else null
            ),
            contentDescription = stringResource(
                R.string.photo_upload_uploaded_image_description,
                photo.id
            ),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun ConfirmAndNextButton(text: String, onClick: () -> Unit) {
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF5CA0FF), Color(0xFF2A8CFF))
    )
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(brush = buttonGradient, shape = CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Preview(showBackground = true, device = "id:pixel_6")
@Composable
fun PhotoUploadSectionPreview() {
    val mockList = listOf(
        PhotoItem(
            id = "123",
            imageUrl = "https://img0.baidu.com/it/u=2895902758,4240700774&fm=253&fmt=auto&app=120&f=JPEG?w=800&h=800"
        ),
        PhotoItem(
            id = "123",
            imageUrl = "https://img0.baidu.com/it/u=2895902758,4240700774&fm=253&fmt=auto&app=120&f=JPEG?w=800&h=800"
        ),
        PhotoItem(
            id = "123",
            imageUrl = "https://img0.baidu.com/it/u=2895902758,4240700774&fm=253&fmt=auto&app=120&f=JPEG?w=800&h=800"
        ),
    )
    PhotoUploadSection(category = PhotoCategory.AFTER_CARE, photos = mockList, onAddPhoto = {})
}

// --- 预览 ---
@Preview(showBackground = true, device = "id:pixel_6")
@Composable
fun PhotoUploadScreenPreview() {
    MaterialTheme {
        PhotoUploadScreen(navController = rememberNavController(), orderId = 1L)
    }
}