package com.ytone.longcare.features.location.manager

import com.ytone.longcare.domain.location.*

import com.ytone.longcare.model.LocationResult
import org.junit.Assert.*
import org.junit.Test

class ServiceOrderLocationQualityTest {
    private val sample = LocationResult(31.2, 121.4, "network", 8f, "GCJ02", 5, 1,
        locationTime = 100_000, receivedAt = 100_000, receivedElapsedRealtime = 1_000)
    private fun rejection(accuracy: Float) = ServiceOrderLocationQuality.rejection(sample.copy(accuracy = accuracy), 100_000, 1_000)

    @Test fun `100 and 200 meter boundaries accept 200 and reject anything above`() {
        listOf(8f, 100f, 150f, 200f).forEach { assertNull(rejection(it)) }
        listOf(200.01f, 500f, 2_000f, 10_000f).forEach {
            assertEquals(LocationQuality.Rejection.ACCURACY, rejection(it))
        }
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertEquals(LocationQuality.Rejection.ACCURACY, rejection(it))
        }
    }

    @Test fun `quality bands distinguish preferred acceptable low and very low`() {
        val policy = ServiceOrderLocationQuality
        assertEquals(ServiceOrderLocationQuality.AccuracyBand.PREFERRED, policy.accuracyBand(100f))
        assertEquals(ServiceOrderLocationQuality.AccuracyBand.ACCEPTABLE, policy.accuracyBand(200f))
        assertEquals(ServiceOrderLocationQuality.AccuracyBand.LOW, policy.accuracyBand(500f))
        assertEquals(ServiceOrderLocationQuality.AccuracyBand.VERY_LOW, policy.accuracyBand(500.1f))
        assertEquals(ServiceOrderLocationQuality.AccuracyBand.UNKNOWN, policy.accuracyBand(Float.NaN))
    }

    @Test fun `service submission has no fixed 15 second age limit while general consumer still does`() {
        assertNull(ServiceOrderLocationQuality.rejection(sample, 160_000, 61_000))
        assertEquals(LocationQuality.Rejection.STALE, LocationQuality.rejection(sample, 160_000, 61_000))
        assertNull(ServiceOrderLocationQuality.rejection(sample, 160_000, 61_000, maxAgeMs = 60_000))
        assertEquals(LocationQuality.Rejection.STALE,
            ServiceOrderLocationQuality.rejection(sample, 160_001, 61_001, maxAgeMs = 60_000))
    }

    @Test fun `removing age limit never bypasses mock source error or coordinate frame checks`() {
        val invalid = listOf(sample.copy(isMock = true), sample.copy(coordType = "WGS84"),
            sample.copy(errorCode = 4), sample.copy(locationType = 4), sample.copy(latitude = Double.NaN),
            sample.copy(trustedLevel = 3), sample.copy(locationTime = 100_001))
        invalid.forEach { assertNotNull(ServiceOrderLocationQuality.rejection(it, 160_000, 61_000)) }
        assertEquals(LocationFailure.PRECISION, ServiceOrderLocationQuality.failure(sample.copy(accuracy = 300f), LocationQuality.Rejection.ACCURACY))
        assertEquals(LocationFailure.QUALITY, ServiceOrderLocationQuality.failure(sample.copy(accuracy = Float.NaN), LocationQuality.Rejection.ACCURACY))
    }

    @Test fun `reporting still rejects pre session and out of order samples`() {
        assertEquals(LocationQuality.Rejection.ORDER,
            ServiceOrderLocationQuality.rejection(sample, 100_000, 1_000, notBefore = 100_001))
        assertEquals(LocationQuality.Rejection.ORDER,
            ServiceOrderLocationQuality.rejection(sample, 100_000, 1_000, previousSampleTime = 100_000))
    }

    @Test fun `normal trust fresh online source is usable while cache offline and low trust stay rejected`() {
        val normal = sample.copy(trustedLevel = 2)
        assertNull(ServiceOrderLocationQuality.rejection(normal, 100_000, 1_000))
        assertEquals(LocationQuality.Rejection.TRUST, LocationQuality.rejection(normal, 100_000, 1_000))
        listOf(3, 4, 8, 9, 11).forEach {
            assertEquals(LocationQuality.Rejection.SOURCE,
                ServiceOrderLocationQuality.rejection(normal.copy(locationType = it), 100_000, 1_000))
        }
        assertEquals(LocationQuality.Rejection.ORDER,
            ServiceOrderLocationQuality.rejection(normal.copy(locationTime = 96_999), 100_000, 1_000, notBefore = 97_000))
        assertEquals(LocationQuality.Rejection.TRUST,
            ServiceOrderLocationQuality.rejection(sample.copy(trustedLevel = 3), 100_000, 1_000))
    }
}
