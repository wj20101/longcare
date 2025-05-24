package com.ytone.longcare.domain.usecase

import com.ytone.longcare.domain.repository.SampleRepository
import javax.inject.Inject

class GetSampleDataUseCase @Inject constructor(private val sampleRepository: SampleRepository) {
    suspend operator fun invoke(): String = sampleRepository.getSampleData()
}
