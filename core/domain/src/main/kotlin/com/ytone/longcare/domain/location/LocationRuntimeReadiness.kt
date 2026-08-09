package com.ytone.longcare.domain.location

/** Platform-neutral readiness checks used before requesting a business location. */
interface LocationRuntimeReadiness {
    fun hasLocationPermission(): Boolean

    fun isLocationServiceEnabled(): Boolean
}
