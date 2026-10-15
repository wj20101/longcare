package com.ytone.longcare.features.location.core

import android.os.SystemClock
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.domain.location.LocationRuntimeReadiness
import com.ytone.longcare.domain.location.ServiceOrderLocationQuality
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.manager.SingleAmapLocationSource
import com.ytone.longcare.model.LocationResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select

@Singleton
class DefaultLocationFacade @Inject constructor(
    private val manager: ContinuousAmapLocationManager,
    private val singleSource: SingleAmapLocationSource,
    private val readiness: LocationRuntimeReadiness,
    private val userSessionRepository: UserSessionRepository,
) : LocationFacade {
    override suspend fun acquireCurrentLocation(): LocationAcquisition = acquire(singleSource::acquireCurrentLocation)

    override suspend fun acquireServiceOrderLocation(): LocationAcquisition = acquire(manager::acquireServiceOrderLocation)

    private suspend fun acquire(requestLocation: suspend () -> LocationAcquisition): LocationAcquisition = coroutineScope<LocationAcquisition> {
        readinessFailure()?.let { return@coroutineScope LocationAcquisition.Failure(it) }
        val identity = userSessionRepository.sessionState.value.identity()
        val request = async { requestLocation() }
        val accountChanged = async {
            userSessionRepository.sessionState.first { it.identity() != identity }
        }
        try {
            select<LocationAcquisition> {
                request.onAwait { result ->
                    if (userSessionRepository.sessionState.value.identity() != identity) {
                        throw CancellationException("Location account changed")
                    }
                    readinessFailure()?.let { LocationAcquisition.Failure(it) } ?: result
                }
                accountChanged.onAwait { throw CancellationException("Location account changed") }
            }
        } finally {
            request.cancel()
            accountChanged.cancel()
        }
    }

    override fun isUsable(location: LocationResult): Boolean =
        readinessFailure() == null && LocationQuality.rejection(
            location, System.currentTimeMillis(), SystemClock.elapsedRealtime(),
        ) == null

    override fun serviceOrderLocationFailure(location: LocationResult): LocationFailure? {
        readinessFailure()?.let { return it }
        val rejection = ServiceOrderLocationQuality.rejection(
            location, System.currentTimeMillis(), SystemClock.elapsedRealtime(),
        ) ?: return null
        return ServiceOrderLocationQuality.failure(location, rejection)
    }

    private fun readinessFailure(): LocationFailure? = when {
        !readiness.hasLocationPermission() -> LocationFailure.PERMISSION
        !readiness.isLocationServiceEnabled() -> LocationFailure.SERVICE_DISABLED
        else -> null
    }

    private fun SessionState.identity(): Triple<Int, Int, Int>? =
        user?.let { Triple(it.companyId, it.accountId, it.userId) }
}
