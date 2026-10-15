package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.domain.location.LocationAccount
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** Identity and synchronous cancellation gate shared by business coordination and the Service. */
internal class LocationReportingSession(
    val orderId: Long,
    val owner: String,
    val account: LocationAccount,
) {
    private val lock = Any()
    private var valid = true
    private val ready = MutableStateFlow(false)
    private var uploadJob: Job? = null
    var monitorJob: Job? = null
    // The following fields are accessed only under LocationReportingManager's lifecycle lock.
    var trackingRequested = false
    var revision = 0L
    var preparingStart = false

    fun canUpload(): Boolean = synchronized(lock) { valid && ready.value }
    fun isValid(): Boolean = synchronized(lock) { valid }
    suspend fun awaitReady() { ready.first { it } }

    fun setEnabled(value: Boolean) = synchronized(lock) {
        ready.value = valid && value
    }

    fun attach(job: Job) = synchronized(lock) {
        if (valid) {
            uploadJob?.cancel()
            uploadJob = job
        } else {
            job.cancel()
        }
    }

    fun invalidate() = synchronized(lock) {
        valid = false
        ready.value = false
        uploadJob?.cancel()
        uploadJob = null
        monitorJob?.cancel()
    }
}
