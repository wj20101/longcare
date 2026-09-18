package com.ytone.longcare.platform.sales

import java.io.Serializable

/** Device upload has finished; opening the business H5 is a separate, consumable step. */
data class SalesEvaluationFormRequest(
    val customerId: Int,
    val recordId: String,
    val url: String? = null,
    val errorMessage: String? = null,
    val consumed: Boolean = false,
) : Serializable
