package com.ytone.longcare.features.nfcsignin.model


data class EndOderInfo(
    val projectIdList: List<Int> = emptyList(),
    val beginImgList: List<String> = emptyList(),
    val endImgList: List<String> = emptyList()
)
