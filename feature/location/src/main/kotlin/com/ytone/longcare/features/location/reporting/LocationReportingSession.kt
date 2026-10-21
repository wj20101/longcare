package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.domain.location.LocationAccount
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

internal data class LocationReportingAuthorization(val enabled: Boolean, val version: Long)

/** Identity and synchronous cancellation gate shared by business coordination and the Service. */
internal class LocationReportingSession(
    val orderId: Long,
    val owner: String,
    val account: LocationAccount,
) {
    private val lock = Any()
    private var valid = true
    private val ready = MutableStateFlow(LocationReportingAuthorization(false, 0L))
    val readyState = ready.asStateFlow()
    private var uploadJob: Job? = null
    private val samplingChecks = Channel<Unit>(Channel.CONFLATED)
    var monitorJob: Job? = null
    // The following fields are accessed only under LocationReportingManager's lifecycle lock.
    var trackingRequested = false
    var revision = 0L
    var preparingStart = false

    fun canUpload(): Boolean = synchronized(lock) { valid && ready.value.enabled }
    fun isValid(): Boolean = synchronized(lock) { valid }
    suspend fun awaitReady() { ready.first { it.enabled } }
    // A visible return wakes the existing health loop; repeated resumes never queue extra work.
    fun requestSamplingCheck() { samplingChecks.trySend(Unit) }
    suspend fun awaitSamplingCheck() { samplingChecks.receive() }

    fun setEnabled(value: Boolean) = synchronized(lock) {
        val enabled = valid && value
        if (ready.value.enabled != enabled) ready.value = LocationReportingAuthorization(enabled, ready.value.version + 1L)
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
        setEnabled(false)
        uploadJob?.cancel()
        uploadJob = null
        monitorJob?.cancel()
    }
}
