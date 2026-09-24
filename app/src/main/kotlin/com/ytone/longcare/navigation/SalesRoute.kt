package com.ytone.longcare.navigation

import androidx.annotation.Keep
import com.ytone.longcare.features.sales.SalesCustomerDraft
import com.ytone.longcare.presentation.sales.SalesPage
import kotlinx.serialization.Serializable

/** Only the current page's inputs; completed forms and SDK sessions are not retained. */
@Keep
@Serializable
internal data class SalesRoute(
    val page: SalesPage,
    val customerId: Int = 0,
    val pgUrl: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val recordId: String? = null,
    val draft: SalesCustomerDraft? = null,
    val photos: List<String> = emptyList(),
    val reminder: com.ytone.longcare.model.ToDoResultModel? = null,
) : AppRoute

internal fun AppNavigator.openCustomerDetailsFromH5(customerId: Int) {
    if (!canHandleCallback() || customerId <= 0) return
    val web = (backStack.lastOrNull() as? AppNavEntry)?.route as? WebViewRoute ?: return
    if (!web.canOpenCustomerDetails) return
    val previous = backStack.getOrNull(backStack.lastIndex - 1) as? AppNavEntry
    val source = previous?.route as? SalesRoute
    if (source?.page == SalesPage.CUSTOMER_DETAIL && source.customerId == customerId) {
        popBackStack()
    } else {
        replaceTop(SalesRoute(SalesPage.CUSTOMER_DETAIL, customerId))
    }
}

internal fun AppNavigator.closeSalesH5(route: WebViewRoute) {
    if (!canHandleCallback()) return
    if (route.isEvaluation && route.evaluationCustomerId > 0) {
        replaceTop(SalesRoute(SalesPage.EVALUATION_COMPLETE, route.evaluationCustomerId))
    } else {
        popBackStack()
    }
}
