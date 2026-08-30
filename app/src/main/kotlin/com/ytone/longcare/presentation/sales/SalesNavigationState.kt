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
    initialSnapshot: SalesNavigationSnapshot = SalesNavigationSnapshot(),
) {
    var currentPage by mutableStateOf(initialSnapshot.currentPage)
        private set
    var rootTab by mutableIntStateOf(initialSnapshot.rootTab)
        private set
    var detailReturnPage by mutableStateOf(initialSnapshot.detailReturnPage)
        private set
    var evaluationChoiceReturnPage by mutableStateOf(initialSnapshot.evaluationChoiceReturnPage)
        private set
    var reminderIndex by mutableIntStateOf(initialSnapshot.reminderIndex)
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

    fun snapshot(): SalesNavigationSnapshot = SalesNavigationSnapshot(
        currentPage = currentPage,
        rootTab = rootTab,
        detailReturnPage = detailReturnPage,
        evaluationChoiceReturnPage = evaluationChoiceReturnPage,
        reminderIndex = reminderIndex,
    )

    fun apply(snapshot: SalesNavigationSnapshot) {
        currentPage = snapshot.currentPage
        rootTab = snapshot.rootTab
        detailReturnPage = snapshot.detailReturnPage
        evaluationChoiceReturnPage = snapshot.evaluationChoiceReturnPage
        reminderIndex = snapshot.reminderIndex
    }
}

internal data class SalesNavigationSnapshot(
    val currentPage: SalesPage = SalesPage.HOME,
    val rootTab: Int = SALES_HOME_TAB,
    val detailReturnPage: SalesPage = SalesPage.HOME,
    val evaluationChoiceReturnPage: SalesPage = SalesPage.HOME,
    val reminderIndex: Int = NO_REMINDER_SELECTED,
)

internal enum class SalesBackEffect {
    None,
    ClearRegistration,
}

internal data class SalesBackResult(
    val snapshot: SalesNavigationSnapshot,
    val effect: SalesBackEffect = SalesBackEffect.None,
)

@Composable
internal fun rememberSalesNavigationState(): SalesNavigationState =
    rememberSaveable(saver = SalesNavigationStateSaver) { SalesNavigationState() }

private val SalesNavigationStateSaver = listSaver<SalesNavigationState, Any>(
    save = { state -> state.snapshot().toSaveableValues() },
    restore = { values -> SalesNavigationState(restoreSalesNavigationSnapshot(values)) },
)

internal fun SalesNavigationSnapshot.toSaveableValues(): List<Any> = listOf(
    currentPage.name,
    rootTab,
    detailReturnPage.name,
    evaluationChoiceReturnPage.name,
    reminderIndex,
)

internal fun restoreSalesNavigationSnapshot(values: List<Any?>): SalesNavigationSnapshot =
    SalesNavigationSnapshot(
        currentPage = values.pageAt(CURRENT_PAGE_INDEX, SalesPage.HOME),
        rootTab = values.intAt(ROOT_TAB_INDEX)
            ?.takeIf { it in SAVABLE_ROOT_TABS }
            ?: SALES_HOME_TAB,
        detailReturnPage = values.pageAt(DETAIL_RETURN_PAGE_INDEX, SalesPage.HOME),
        evaluationChoiceReturnPage =
            values.pageAt(EVALUATION_RETURN_PAGE_INDEX, SalesPage.HOME),
        reminderIndex = values.intAt(REMINDER_INDEX)
            ?.takeIf { it >= NO_REMINDER_SELECTED }
            ?: NO_REMINDER_SELECTED,
    )

private fun List<Any?>.pageAt(index: Int, default: SalesPage): SalesPage =
    (getOrNull(index) as? String)
        ?.let { value -> runCatching { SalesPage.valueOf(value) }.getOrNull() }
        ?: default

private fun List<Any?>.intAt(index: Int): Int? = getOrNull(index) as? Int

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

internal fun reduceSalesBack(snapshot: SalesNavigationSnapshot): SalesBackResult = when (snapshot.currentPage) {
    SalesPage.HOME -> SalesBackResult(
        snapshot = if (snapshot.rootTab == SALES_HOME_TAB) {
            snapshot
        } else {
            snapshot.copy(rootTab = SALES_HOME_TAB)
        },
    )

    SalesPage.REMINDERS,
    SalesPage.CUSTOMERS,
    SalesPage.EVALUATION_COMPLETE,
    -> SalesBackResult(snapshot.goHome())

    SalesPage.REMINDER_DETAIL -> SalesBackResult(
        snapshot.copy(currentPage = SalesPage.REMINDERS),
    )

    SalesPage.CUSTOMER_DETAIL -> SalesBackResult(
        snapshot.copy(currentPage = snapshot.safeDetailReturnPage()),
    )

    SalesPage.REGISTRATION -> SalesBackResult(
        snapshot = snapshot.goHome(),
        effect = SalesBackEffect.ClearRegistration,
    )

    SalesPage.REGISTRATION_CONFIRM -> SalesBackResult(
        snapshot.copy(currentPage = SalesPage.REGISTRATION),
    )

    SalesPage.SUBMIT_SUCCESS -> SalesBackResult(
        snapshot = snapshot.goHome(),
        effect = SalesBackEffect.ClearRegistration,
    )

    SalesPage.EVALUATION_CHOICE,
    SalesPage.DEVICE_STATUS,
    SalesPage.EVALUATION_GUIDE,
    -> SalesBackResult(
        snapshot.copy(
            currentPage = evaluationBackTarget(
                currentPage = snapshot.currentPage,
                choiceReturnPage = snapshot.evaluationChoiceReturnPage,
            ),
        ),
    )
}

private fun SalesNavigationSnapshot.goHome(): SalesNavigationSnapshot = copy(
    currentPage = SalesPage.HOME,
    rootTab = SALES_HOME_TAB,
)

private fun SalesNavigationSnapshot.safeDetailReturnPage(): SalesPage =
    detailReturnPage.takeIf { it == SalesPage.HOME || it == SalesPage.CUSTOMERS }
        ?: SalesPage.HOME

private const val SALES_HOME_TAB = 0
private const val NO_REMINDER_SELECTED = -1
private const val CURRENT_PAGE_INDEX = 0
private const val ROOT_TAB_INDEX = 1
private const val DETAIL_RETURN_PAGE_INDEX = 2
private const val EVALUATION_RETURN_PAGE_INDEX = 3
private const val REMINDER_INDEX = 4
private val SAVABLE_ROOT_TABS = setOf(SALES_HOME_TAB, 2)
