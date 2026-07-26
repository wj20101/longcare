package com.ytone.longcare.features.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.model.UserLatentCheckState
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.model.UserLatentListModel
import kotlinx.coroutines.delay

internal const val CUSTOMER_SEARCH_DEBOUNCE_MILLIS = 300L

@Composable
internal fun SalesCustomerListScreen(
    customers: List<UserLatentListModel>,
    isLoading: Boolean,
    initialKeyword: String,
    initialCheckState: Int,
    onBack: () -> Unit,
    onSearch: (String, Int) -> Unit,
    onCustomerClick: (Int) -> Unit,
) {
    var keyword by remember(initialKeyword) { mutableStateOf(initialKeyword) }
    var selectedState by remember(initialCheckState) {
        mutableIntStateOf(initialCheckState)
    }
    val focusManager = LocalFocusManager.current
    val loadingIndicatorState =
        rememberContentLoadingIndicatorState(isLoading = isLoading)
    val tabs =
        listOf(
            UserLatentCheckState.NOT_SUBMITTED to "未审核",
            UserLatentCheckState.PENDING_REVIEW to "待审核",
            UserLatentCheckState.APPROVED to "通过",
            UserLatentCheckState.REJECTED to "未通过",
        )

    LaunchedEffect(keyword, selectedState) {
        delay(CUSTOMER_SEARCH_DEBOUNCE_MILLIS)
        onSearch(keyword, selectedState)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = "我的客户",
            onBack = onBack,
        )
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .height(52.dp),
            placeholder = {
                Text(
                    text = "请输入关键词搜索",
                    color = Color(0xFF757A82),
                    fontSize = 16.sp,
                )
            },
            textStyle =
                TextStyle(
                    color = SalesTextPrimary,
                    fontSize = 16.sp,
                ),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = SalesTextPrimary,
                    unfocusedTextColor = SalesTextPrimary,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = SalesBlue,
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(
                    onSearch = { focusManager.clearFocus() },
                ),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            tabs.forEach { (state, label) ->
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable {
                                if (selectedState != state) {
                                    selectedState = state
                                }
                            }
                            .padding(horizontal = 5.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        color =
                            if (selectedState == state) {
                                Color.White
                            } else {
                                Color.White.copy(alpha = 0.55f)
                            },
                        fontSize = 15.sp,
                        fontWeight =
                            if (selectedState == state) {
                                FontWeight.Medium
                            } else {
                                FontWeight.Normal
                            },
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(5.dp))
                    if (selectedState == state) {
                        Spacer(
                            modifier =
                                Modifier
                                    .width(22.dp)
                                    .height(3.dp)
                                    .salesWhiteCard(radius = 2)
                        )
                    } else {
                        Spacer(Modifier.height(3.dp))
                    }
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 18.dp,
                        end = 18.dp,
                        top = 8.dp,
                        bottom = 20.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (customers.isEmpty() && !isLoading) {
                    item {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .salesWhiteCard()
                                    .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "暂无符合条件的客户",
                                color = SalesTextPrimary,
                                fontSize = 16.sp,
                            )
                            Text(
                                text = "可切换审核状态或修改搜索关键词",
                                color = SalesTextSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                } else {
                    items(
                        items = customers,
                        key = UserLatentListModel::id,
                    ) { customer ->
                        SalesCustomerListCard(
                            customer = customer,
                            onClick = { onCustomerClick(customer.id) },
                        )
                    }
                }
            }
            if (loadingIndicatorState.isVisible) {
                LinearProgressIndicator(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                    color = SalesBlue,
                    trackColor = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun SalesCustomerListCard(
    customer: UserLatentListModel,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .salesWhiteCard()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = customer.userName.ifBlank { "未命名客户" },
                color = SalesTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = customer.liveAddress.ifBlank { "地址待补充" },
                color = SalesTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = customer.checkState.toSalesCheckStateLabel(),
            color = SalesTextSecondary,
            fontSize = 13.sp,
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowRight,
            contentDescription = "查看详情",
            tint = Color(0xFFB8C9DF),
        )
    }
}

@Composable
internal fun SalesCustomerDetailScreen(
    customer: UserLatentDetailModel?,
    onBack: () -> Unit,
    onEvaluate: (Int) -> Unit,
    onOpenReport: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = "客户详情",
            onBack = onBack,
        )
        if (customer == null) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                        .height(180.dp)
                        .salesWhiteCard(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "正在加载客户信息…",
                    color = SalesTextPrimary,
                    fontSize = 16.sp,
                )
            }
            return
        }

        val hasEvaluation = customer.hasCompletedEvaluation()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 2.dp,
                    bottom = 18.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column {
                    SalesSectionTab(
                        text = "客户信息",
                        colors =
                            listOf(
                                Color(0xFF26F57A),
                                Color(0xFF53D8B4),
                            ),
                    )
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .salesWhiteCard()
                                .padding(
                                    start = 20.dp,
                                    end = 16.dp,
                                    top = 23.dp,
                                    bottom = 20.dp,
                                ),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        SalesInfoRow("姓名：", customer.userName)
                        SalesInfoRow(
                            "年龄：",
                            identityCardAge(customer.identityCardNumber)
                                ?.toString()
                                .orEmpty(),
                        )
                        SalesInfoRow("身份证号：", customer.identityCardNumber)
                        SalesInfoRow("联系人：", customer.guardianName)
                        SalesInfoRow("手机号码：", customer.guardianPhone)
                        SalesInfoRow("关系：", customer.guardianRelation)
                        SalesInfoRow("地址：", customer.liveAddress)
                    }
                }
            }
            item {
                Column {
                    SalesSectionTab(
                        text = "其他信息",
                        colors =
                            listOf(
                                Color(0xFFFFF05B),
                                Color(0xFFFFAD91),
                            ),
                    )
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .salesWhiteCard()
                                .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SalesInfoRow(
                            label = "申报状态：",
                            value = "已申报",
                            valueColor = Color(0xFF2696EB),
                        )
                        SalesInfoRow(
                            label = "评估：",
                            value =
                                if (hasEvaluation) {
                                    buildString {
                                        append("已评估")
                                        if (customer.pgResult.isNotBlank()) {
                                            append("，评估结果：\n")
                                            append(customer.pgResult)
                                        }
                                    }
                                } else {
                                    "未评估"
                                },
                        )
                    }
                }
            }
            item {
                SalesOutlinedActionButton(
                    text = "返回",
                    onClick = onBack,
                )
            }
            if (!hasEvaluation) {
                item {
                    SalesPrimaryButton(
                        text = "立即评估",
                        onClick = { onEvaluate(customer.id) },
                    )
                }
            } else if (customer.pgUrl.isNotBlank()) {
                item {
                    SalesPrimaryButton(
                        text = "查看评估报告",
                        onClick = onOpenReport,
                    )
                }
            }
        }
    }
}

/**
 * `pgUrl` is created before an evaluation and points to the form entry, so it
 * must not be used as proof that the customer has already been evaluated.
 */
internal fun UserLatentDetailModel.hasCompletedEvaluation(): Boolean =
    pgId > 0 || pgResult.isNotBlank()
