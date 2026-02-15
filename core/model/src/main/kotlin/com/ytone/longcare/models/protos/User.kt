package com.ytone.longcare.models.protos

import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 用户信息
 */
@Serializable
data class User(
    val companyId: Int = 0,
    val accountId: Int = 0,
    val userId: Int = 0,
    val userName: String = "",
    val headUrl: String = "",
    val userIdentity: Int = 0,
    val identityCardNumber: String = "",
    val gender: Int = 0,
    val token: String = ""
) {
    fun encode(): ByteArray = ADAPTER.encode(this)

    companion object {
        val ADAPTER: Adapter = Adapter
    }

    object Adapter {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun decode(bytes: ByteArray): User =
            try {
                json.decodeFromString(bytes.decodeToString())
            } catch (e: Exception) {
                throw IOException("Failed to decode user data", e)
            }

        fun encode(value: User): ByteArray =
            try {
                json.encodeToString(value).encodeToByteArray()
            } catch (e: Exception) {
                throw IOException("Failed to encode user data", e)
            }
    }
}
