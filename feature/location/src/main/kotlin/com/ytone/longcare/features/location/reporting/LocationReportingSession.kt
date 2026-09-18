package com.ytone.longcare.features.location.reporting

import kotlinx.coroutines.Job

/** Identity and synchronous cancellation gate shared by business coordination and the Service. */
internal class LocationReportingSession(val orderId: Long, val owner: String) {
    private val lock = Any()
    private var valid = true
    private var enabled = false
    private var uploadJob: Job? = null
    var monitorJob: Job? = null
    // The following fields are accessed only under LocationReportingManager's lifecycle lock.
    var trackingRequested = false
    var foregroundRequested = false
    var revision = 0L

    fun canUpload(): Boolean = synchronized(lock) { valid && enabled }

    fun setEnabled(value: Boolean) = synchronized(lock) {
        enabled = valid && value
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
        enabled = false
        uploadJob?.cancel()
        uploadJob = null
        monitorJob?.cancel()
    }
}
