package com.ytone.longcare.features.nursing.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ytone.longcare.common.utils.TimeUtils
import com.ytone.longcare.core.ui.message.UiMessageSnackbarEffect
import com.ytone.longcare.features.nursing.api.NursingActions
import com.ytone.longcare.features.nursing.vm.NursingViewModel
import com.ytone.longcare.model.handleOrderNavigation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

private val nursingDateSaver = Saver<LocalDate, String>(
    save = { it.toString() },
    restore = { LocalDate.parse(it) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NursingScreen(
    actions: NursingActions,
    viewModel: NursingViewModel = hiltViewModel(),
    isTabSelected: Boolean = true,
    currentDate: () -> LocalDate = { TimeUtils.getCurrentLocalDate() },
) {
    val readCurrentDate by rememberUpdatedState(currentDate)
    var today by rememberSaveable(stateSaver = nursingDateSaver) {
        mutableStateOf(readCurrentDate())
    }
    var selectedDate by rememberSaveable(stateSaver = nursingDateSaver) {
        mutableStateOf(today)
    }
    // Cache only for this page and this local date, including the relative day labels.
    val dateList = remember(today) { TimeUtils.getCurrentMonthDateList(today = today) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(lifecycleOwner, isTabSelected) {
        if (!isTabSelected) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (currentCoroutineContext().isActive) {
                val newToday = readCurrentDate()
                if (newToday != today) {
                    // Follow today, but keep an explicitly selected date in the current month.
                    if (selectedDate == today || selectedDate.year != newToday.year ||
                        selectedDate.month != newToday.month
                    ) {
                        selectedDate = newToday
                    }
                    today = newToday
                }
                // Checking one date does not rebuild the month or request orders.
                delay(60_000L)
            }
        }
    }

    val displayedToday = today
    val requestDate = selectedDate
    LaunchedEffect(viewModel, lifecycleOwner, isTabSelected, displayedToday, requestDate) {
        if (!isTabSelected) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // On resume, wait for the date check rather than briefly requesting a stale day.
            if (displayedToday == readCurrentDate()) viewModel.getOrderList(requestDate.toString())
        }
    }

    val orderState by viewModel.orderListState.collectAsStateWithLifecycle()
    val uiMessages by viewModel.uiMessages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    UiMessageSnackbarEffect(
        messages = uiMessages,
        snackbarHostState = snackbarHostState,
        onConsumed = viewModel::consumeUiMessage,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { NursingTopBar() },
        containerColor = Color.Transparent,
    ) { paddingValues ->
        // A new day can also change the month length; reset pager indices together with its data.
        key(displayedToday) {
            val pagerState = rememberPagerState(
                initialPage = dateList.indexOfFirst { it.date == selectedDate }.coerceAtLeast(0),
                pageCount = { dateList.size },
            )
            // Avoid issuing a request for every intermediate page during a tab animation.
            LaunchedEffect(pagerState.settledPage) {
                // A restored pager from the previous day must not undo the date refresh.
                if (today == displayedToday) {
                    selectedDate = dateList[pagerState.settledPage].date
                }
            }
            Column(modifier = Modifier.padding(top = paddingValues.calculateTopPadding())) {
                NursingDateTabsRow(
                    dateList = dateList,
                    pagerState = pagerState,
                    selectedTabContentColor = Color(0xFF4A86E8),
                    unselectedTabContentColor = Color.White,
                    onTabClick = { index ->
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
                )
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val pageDate = dateList[page].date
                    val hasMatchingOrders = pageDate == selectedDate &&
                        orderState.daytime == pageDate.toString()
                    PlanList(
                        plans = if (hasMatchingOrders) orderState.orders else emptyList(),
                        isLoading = !hasMatchingOrders || orderState.isLoading,
                    ) { order ->
                        handleOrderNavigation(
                            state = order.state,
                            orderId = order.orderId,
                            planId = order.planId,
                            onNavigateToNursingExecution = actions.onNavigateToNursingExecution,
                            onNavigateToService = actions.onNavigateToService,
                            onNotStartedState = {},
                        )
                    }
                }
            }
        }
    }
}
