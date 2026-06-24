package com.ytone.longcare.model

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
    val accuracy: Float = 0f,
    val coordType: String = "",
    val locationType: Int = 0,
    val trustedLevel: Int = 0,
    val locationTime: Long = 0L
)
