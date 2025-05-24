package com.ytone.longcare.data.repository

import com.ytone.longcare.domain.repository.SampleRepository
import javax.inject.Inject

class SampleRepositoryImpl @Inject constructor() : SampleRepository {
    override suspend fun getSampleData(): String {
        // Implementation: fetch from local DB or remote API
        return "Sample Data from Data Layer"
    }
}
