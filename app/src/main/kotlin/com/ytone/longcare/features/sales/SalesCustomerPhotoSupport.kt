package com.ytone.longcare.features.sales

import android.net.Uri

internal const val MAX_SALES_CUSTOMER_PHOTOS = 3

internal fun mergeSalesCustomerPhotoUris(
    existing: List<Uri>,
    added: List<Uri>,
): List<Uri> =
    (existing + added)
        .distinctBy(Uri::toString)
        .take(MAX_SALES_CUSTOMER_PHOTOS)
