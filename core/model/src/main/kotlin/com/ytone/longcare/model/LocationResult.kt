package com.ytone.longcare.model

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
    val accuracy: Float = 0f
)
