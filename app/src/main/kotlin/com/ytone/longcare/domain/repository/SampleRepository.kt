package com.ytone.longcare.domain.repository

interface SampleRepository {
    // Define methods for data operations
    suspend fun getSampleData(): String
}
