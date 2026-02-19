package com.ytone.longcare.features.service

import android.content.Context
import androidx.core.content.edit

internal class ServiceTimeNotificationRecordDelegate(
    private val context: Context,
    private val prefsName: String,
    private val keyLastProcessedPrefix: String,
    private val deduplicateWindowMillis: Long,
) {
    fun isNotificationAlreadyProcessed(orderId: Long): Boolean {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val lastProcessed = prefs.getLong(keyLastProcessedPrefix + orderId, 0)
        val currentTime = System.currentTimeMillis()
        return currentTime - lastProcessed < deduplicateWindowMillis
    }

    fun markNotificationAsProcessed(orderId: Long) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit {
            putLong(keyLastProcessedPrefix + orderId, System.currentTimeMillis())
        }
    }

    fun clearNotificationProcessedMark(orderId: Long) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit { remove(keyLastProcessedPrefix + orderId) }
    }
}
