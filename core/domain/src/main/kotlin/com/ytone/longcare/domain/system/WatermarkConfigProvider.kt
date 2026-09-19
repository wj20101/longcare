package com.ytone.longcare.domain.system

/** Watermark branding shared by production and the validation assistant. */
interface WatermarkConfigProvider {
    suspend fun getSyLogoImg(): String
}
