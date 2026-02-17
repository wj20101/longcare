package com.ytone.longcare.domain.location

interface AmapApiKeyProvider {
    suspend fun getAmapApiKey(): String?
}
