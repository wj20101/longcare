package com.ytone.longcare.features.serviceorders.ui

import com.ytone.longcare.core.ui.order.ServiceOrderItem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.features.serviceorders.api.ServiceOrdersListActions
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.handleOrderNavigation
import com.ytone.longcare.theme.bgGradientBrush
import androidx.compose.ui.res.stringResource
import com.ytone.longcare.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServiceOrdersListScreenLayout(
    title: String,
    emptyTitle: String,
    emptySubtitle: String,
    filteredOrders: List<TodayServiceOrderModel>,
    actions: ServiceOrdersListActions,
    modifier: Modifier = Modifier,
    profileTagsEnabled: Boolean = false,
) {
    Box(
        modifier = modifier
            .then(
                if (profileTagsEnabled) {
                    Modifier.testTag("profile_service_records_root")
                } else {
                    Modifier
                },
            )
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                ServiceOrdersListTopBar(
                    title = title,
                    onNavigateBack = actions.onNavigateBack,
                    profileTagsEnabled = profileTagsEnabled,
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (filteredOrders.isEmpty()) {
                    item {
                        ServiceOrdersEmptyState(
                            emptyTitle = emptyTitle,
                            emptySubtitle = emptySubtitle
                        )
                    }
                } else {
                    items(filteredOrders) { order ->
                        ServiceOrderItem(
                            order = order,
                            onClick = singleClick {
                                handleOrderNavigation(
                                    state = order.state,
                                    orderId = order.orderId,
                                    planId = 0,
                                    onNavigateToNursingExecution = actions.onNavigateToNursingExecution,
                                    onNavigateToService = actions.onNavigateToService,
                                    onNotStartedState = {}
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceOrdersListTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    profileTagsEnabled: Boolean,
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(
                onClick = singleClick { onNavigateBack() },
                modifier = if (profileTagsEnabled) {
                    Modifier.testTag("profile_service_records_back")
                } else {
                    Modifier
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White
        )
    )
}

@Composable
private fun ServiceOrdersEmptyState(
    emptyTitle: String,
    emptySubtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = emptyTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = emptySubtitle,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}
