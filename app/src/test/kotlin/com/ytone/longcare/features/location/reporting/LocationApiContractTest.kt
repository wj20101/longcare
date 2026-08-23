package com.ytone.longcare.features.location.reporting

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.data.repository.LocationRepositoryImpl
import com.ytone.longcare.model.AddPositionParamModel
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.POST

/** Passing contract tests: fixes must not invent fields that are absent from the server document. */
class LocationApiContractTest {

    @Test
    fun `add position endpoint and JSON names match the published server contract`() {
        val apiMethod = LongCareApiService::class.java.declaredMethods
            .single { it.name == "addPosition" }
        val post = requireNotNull(apiMethod.getAnnotation(POST::class.java))
        assertEquals("/V1/Service/AddPostion", post.value)

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val json = moshi.adapter(AddPositionParamModel::class.java).toJson(
            AddPositionParamModel(
                orderId = 42L,
                longitude = "121.47370",
                latitude = "31.23041",
            )
        )

        assertTrue(json.contains("\"orderid\":42"))
        assertTrue(json.contains("\"longitude\":\"121.47370\""))
        assertTrue(json.contains("\"latitude\":\"31.23041\""))
        assertFalse(json.contains("\"orderId\""))
    }

    @Test
    fun `repository preserves longitude and latitude ordering`() = runTest {
        val api = mockk<LongCareApiService>()
        val captured = slot<AddPositionParamModel>()
        coEvery { api.addPosition(capture(captured)) } returns ApiResult.Success(Unit)

        val repository = LocationRepositoryImpl(api)
        repository.addPosition(
            orderId = 42L,
            latitude = 31.23041,
            longitude = 121.47370,
        )

        assertEquals(42L, captured.captured.orderId)
        assertEquals("121.4737", captured.captured.longitude)
        assertEquals("31.23041", captured.captured.latitude)
    }
}
