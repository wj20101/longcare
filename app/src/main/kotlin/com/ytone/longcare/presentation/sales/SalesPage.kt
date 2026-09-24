package com.ytone.longcare.presentation.sales

@kotlinx.serialization.Serializable
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
