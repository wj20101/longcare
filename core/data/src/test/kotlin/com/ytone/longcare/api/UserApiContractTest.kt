package com.ytone.longcare.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ytone.longcare.model.CheckFaceParamModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.POST

class UserApiContractTest {

    @Test
    fun `checkFace uses documented POST path and request body`() {
        val method = LongCareApiService::class.java.declaredMethods.single { it.name == "checkFace" }

        assertEquals(
            "/V1/User/CheckFace",
            requireNotNull(method.getAnnotation(POST::class.java)).value,
        )
        assertTrue(
            method.parameterAnnotations.any { annotations ->
                annotations.any { it is Body }
            },
        )
    }

    @Test
    fun `checkFace request serializes documented field names and values`() {
        val faceImg = "ZmFjZQ=="
        val request = CheckFaceParamModel(orderId = 123, faceImg = faceImg)
        val moshi = Moshi.Builder().build()
        val json = moshi.adapter(CheckFaceParamModel::class.java).toJson(request)
        val mapType =
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val jsonObject = requireNotNull(
            moshi.adapter<Map<String, Any?>>(mapType).fromJson(json),
        )

        assertEquals(setOf("orderId", "faceImg"), jsonObject.keys)
        assertEquals(123.0, jsonObject["orderId"])
        assertEquals(faceImg, jsonObject["faceImg"])
    }
}
