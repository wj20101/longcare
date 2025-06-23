package com.ytone.longcare.features.nfcsignin.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class EndOderInfo(
    @Json(name = "projectIdList") val projectIdList: List<Int> = emptyList(),
    @Json(name = "beginImgList") val beginImgList: List<String> = emptyList(),
    @Json(name = "endImgList") val endImgList: List<String> = emptyList()
)