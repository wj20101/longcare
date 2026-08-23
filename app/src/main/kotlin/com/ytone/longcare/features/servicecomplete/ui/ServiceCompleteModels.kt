package com.ytone.longcare.features.servicecomplete.ui

import android.content.Context
import com.ytone.longcare.R

data class ServiceSummary(
    val clientName: String,
    val clientAge: Int,
    val clientIdNumber: String,
    val clientAddress: String,
    val serviceContent: String,
    val duration: String
)

fun formatServiceDuration(context: Context, totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> context.getString(
            R.string.duration_hours_minutes,
            hours,
            minutes,
        )
        hours > 0 -> context.getString(R.string.duration_hours, hours)
        else -> context.getString(R.string.common_duration_minutes, minutes)
    }
}
