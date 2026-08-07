package com.ytone.longcare.features.sales

import android.net.Uri
import com.ytone.longcare.model.WatermarkData

internal const val MAX_SALES_CUSTOMER_PHOTOS = 3

internal fun mergeSalesCustomerPhotoUris(
    existing: List<Uri>,
    added: List<Uri>,
): List<Uri> =
    (existing + added)
        .distinctBy(Uri::toString)
        .take(MAX_SALES_CUSTOMER_PHOTOS)

internal fun createSalesCustomerWatermarkData(
    title: String,
    advisorName: String,
    unknownAdvisorName: String,
): WatermarkData =
    WatermarkData(
        title = title,
        insuredPerson = "",
        caregiver = advisorName.trim().ifBlank { unknownAdvisorName },
        address = "",
    )
