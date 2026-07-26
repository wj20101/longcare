package com.ytone.longcare.features.sales

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ytone.longcare.R
import com.ytone.longcare.model.LocationResult

@Composable
internal fun SalesRegistrationScreen(
    draft: SalesCustomerDraft,
    photoUris: List<Uri>,
    location: LocationResult?,
    onDraftChange: (SalesCustomerDraft) -> Unit,
    onPhotosSelected: (List<Uri>) -> Unit,
    onRemovePhoto: (Uri) -> Unit,
    onRequestLocation: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onValidationError: (String) -> Unit,
) {
    val photoPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetMultipleContents(),
            onResult = { selected ->
                onPhotosSelected((photoUris + selected).distinct().take(3))
            },
        )
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = "信息登记",
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 8.dp,
                    bottom = 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                SalesRegistrationField(
                    value = draft.userName,
                    placeholder = "请输入老人姓名",
                    onValueChange = {
                        onDraftChange(draft.copy(userName = it))
                    },
                )
            }
            item {
                SalesRegistrationField(
                    value = draft.identityCardNumber,
                    placeholder = "请输入老人身份证号码",
                    onValueChange = {
                        onDraftChange(
                            draft.copy(
                                identityCardNumber =
                                    it.uppercase().filter { char ->
                                        char.isDigit() || char == 'X'
                                    }.take(18)
                            )
                        )
                    },
                    keyboardType = KeyboardType.Ascii,
                )
            }
            item {
                SalesRegistrationField(
                    value = draft.guardianName,
                    placeholder = "请输入联系人",
                    onValueChange = {
                        onDraftChange(draft.copy(guardianName = it))
                    },
                )
            }
            item {
                SalesRegistrationField(
                    value = draft.guardianPhone,
                    placeholder = "请输入联系人手机号码",
                    onValueChange = {
                        onDraftChange(
                            draft.copy(
                                guardianPhone = it.filter(Char::isDigit).take(11)
                            )
                        )
                    },
                    keyboardType = KeyboardType.Phone,
                )
            }
            item {
                SalesRegistrationField(
                    value = draft.guardianRelation,
                    placeholder = "请输入与老人关系",
                    onValueChange = {
                        onDraftChange(draft.copy(guardianRelation = it))
                    },
                )
            }
            item {
                SalesRegistrationField(
                    value = draft.liveAddress,
                    placeholder = "请输入居住地址",
                    onValueChange = {
                        onDraftChange(draft.copy(liveAddress = it))
                    },
                    singleLine = false,
                    minHeight = 82,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SalesPrimaryButton(
                        text =
                            if (location == null) {
                                "获取定位"
                            } else {
                                "重新定位"
                            },
                        onClick = onRequestLocation,
                        modifier = Modifier.fillMaxWidth(0.46f),
                    )
                    if (location != null) {
                        Text(
                            text =
                                "已定位：${"%.6f".format(location.latitude)}, " +
                                    "%.6f".format(location.longitude),
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            item {
                Text(
                    text = "现场照片（最多3张）",
                    color = Color.White,
                    fontSize = 15.sp,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    photoUris.forEach { uri ->
                        SalesPhotoThumbnail(
                            uri = uri,
                            onRemove = { onRemovePhoto(uri) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (photoUris.size < 3) {
                        SalesPhotoAddButton(
                            onClick = { photoPicker.launch("image/*") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat((3 - photoUris.size - 1).coerceAtLeast(0)) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                SalesPrimaryButton(
                    text = "提交",
                    onClick = {
                        val error = draft.validationMessage()
                        if (error == null) {
                            onContinue()
                        } else {
                            onValidationError(error)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SalesRegistrationField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minHeight: Int = 50,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(minHeight.dp),
        placeholder = {
            Text(
                text = placeholder,
                color = Color(0xFFA0A3A7),
                fontSize = 15.sp,
            )
        },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = SalesBlue,
            ),
    )
}

@Composable
private fun SalesPhotoThumbnail(
    uri: Uri,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "现场照片",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "移除照片",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SalesPhotoAddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.96f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "添加现场照片",
            tint = SalesBlue,
            modifier = Modifier.size(35.dp),
        )
    }
}

@Composable
internal fun SalesInformationConfirmationScreen(
    draft: SalesCustomerDraft,
    photoUris: List<Uri>,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = "信息确认",
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 10.dp,
                    bottom = 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .salesWhiteCard()
                            .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    SalesInfoRow("姓名：", draft.userName)
                    SalesInfoRow(
                        "年龄：",
                        identityCardAge(draft.identityCardNumber)
                            ?.toString()
                            .orEmpty(),
                    )
                    SalesInfoRow("身份证号：", draft.identityCardNumber)
                    SalesInfoRow("联系人：", draft.guardianName)
                    SalesInfoRow("手机号码：", draft.guardianPhone)
                    SalesInfoRow("关系：", draft.guardianRelation)
                    SalesInfoRow("地址：", draft.liveAddress)
                    if (photoUris.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            photoUris.forEach { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "现场照片",
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1.2f)
                                            .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            repeat((3 - photoUris.size).coerceAtLeast(0)) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item {
                SalesPrimaryButton(
                    text = "确定提交",
                    onClick = onSubmit,
                )
            }
        }
    }
}

@Composable
internal fun SalesSubmitSuccessScreen(
    onBack: () -> Unit,
    onEvaluation: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = "信息提交结果",
            onBack = onBack,
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(34.dp),
        ) {
            SalesSuccessPanel(title = "信息提交成功")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SalesOutlinedActionButton(
                    text = "确认并返回",
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
                SalesPrimaryButton(
                    text = "进行评估",
                    onClick = onEvaluation,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun SalesEvaluationChoiceScreen(
    onBack: () -> Unit,
    onAutomaticEvaluation: () -> Unit,
    onFormEvaluation: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = "信息提交结果",
            onBack = onBack,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SalesEvaluationChoiceCard(
                title = "设备自动评估",
                subtitle = "手握住设备即可评估完成",
                onClick = onAutomaticEvaluation,
                modifier = Modifier.weight(1f),
            ) {
                Image(
                    painter = painterResource(R.drawable.sales_qlz_device_design),
                    contentDescription = "健康评估设备",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            SalesEvaluationChoiceCard(
                title = "表单评估",
                subtitle = "问卷调研形式评估",
                onClick = onFormEvaluation,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Assignment,
                    contentDescription = null,
                    tint = Color(0xFFFFC47B),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                )
            }
        }
    }
}

@Composable
private fun SalesEvaluationChoiceCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .height(128.dp)
                .salesWhiteCard()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 10.dp, top = 14.dp),
    ) {
        Text(
            text = title,
            color = SalesTextPrimary,
            fontSize = 17.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            color = SalesTextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            illustration()
        }
    }
}
