package com.ytone.longcare.presentation.sales

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Stable
internal class SalesNavigationState(
    initialPage: SalesPage = SalesPage.HOME,
    initialRootTab: Int = SALES_HOME_TAB,
    initialDetailReturnPage: SalesPage = SalesPage.HOME,
    initialEvaluationChoiceReturnPage: SalesPage = SalesPage.HOME,
    initialReminderIndex: Int = NO_REMINDER_SELECTED,
) {
    var currentPage by mutableStateOf(initialPage)
        private set
    var rootTab by mutableIntStateOf(initialRootTab)
        private set
    var detailReturnPage by mutableStateOf(initialDetailReturnPage)
        private set
    var evaluationChoiceReturnPage by mutableStateOf(initialEvaluationChoiceReturnPage)
        private set
    var reminderIndex by mutableIntStateOf(initialReminderIndex)
        private set

    val canHandleBack: Boolean get() = currentPage != SalesPage.HOME || rootTab != SALES_HOME_TAB

    fun navigate(page: SalesPage) { currentPage = page }
    fun selectRootTab(tab: Int) { rootTab = tab }
    fun goHome() { currentPage = SalesPage.HOME; rootTab = SALES_HOME_TAB }
    fun showCustomerDetail(returnPage: SalesPage) {
        detailReturnPage = returnPage
        currentPage = SalesPage.CUSTOMER_DETAIL
    }
    fun rememberEvaluationChoiceReturnPage(returnPage: SalesPage) {
        evaluationChoiceReturnPage = returnPage
    }
    fun selectReminder(index: Int) { reminderIndex = index; currentPage = SalesPage.REMINDER_DETAIL }
}

@Composable
internal fun rememberSalesNavigationState(): SalesNavigationState =
    rememberSaveable(saver = SalesNavigationStateSaver) { SalesNavigationState() }

private val SalesNavigationStateSaver = listSaver<SalesNavigationState, Any>(
    save = { state ->
        listOf(
            state.currentPage.name,
            state.rootTab,
            state.detailReturnPage.name,
            state.evaluationChoiceReturnPage.name,
            state.reminderIndex,
        )
    },
    restore = { values ->
        SalesNavigationState(
            initialPage = values[0].toString().toSalesPageOrDefault(SalesPage.HOME),
            initialRootTab = values[1] as Int,
            initialDetailReturnPage = values[2].toString().toSalesPageOrDefault(SalesPage.HOME),
            initialEvaluationChoiceReturnPage = values[3].toString().toSalesPageOrDefault(SalesPage.HOME),
            initialReminderIndex = values[4] as Int,
        )
    },
)

private fun String.toSalesPageOrDefault(default: SalesPage): SalesPage =
    runCatching { SalesPage.valueOf(this) }.getOrDefault(default)

internal enum class SalesPage {
    HOME,
    REMINDERS,
    REMINDER_DETAIL,
    CUSTOMERS,
    CUSTOMER_DETAIL,
    REGISTRATION,
    REGISTRATION_CONFIRM,
    SUBMIT_SUCCESS,
    EVALUATION_CHOICE,
    DEVICE_STATUS,
    EVALUATION_GUIDE,
    EVALUATION_COMPLETE,
}

internal fun evaluationBackTarget(
    currentPage: SalesPage,
    choiceReturnPage: SalesPage,
): SalesPage = when (currentPage) {
    SalesPage.EVALUATION_CHOICE -> choiceReturnPage.takeUnless {
        it == SalesPage.EVALUATION_CHOICE ||
            it == SalesPage.DEVICE_STATUS ||
            it == SalesPage.EVALUATION_GUIDE ||
            it == SalesPage.EVALUATION_COMPLETE
    } ?: SalesPage.HOME
    SalesPage.DEVICE_STATUS -> SalesPage.EVALUATION_CHOICE
    SalesPage.EVALUATION_GUIDE -> SalesPage.DEVICE_STATUS
    else -> currentPage
}

private const val SALES_HOME_TAB = 0
private const val NO_REMINDER_SELECTED = -1
