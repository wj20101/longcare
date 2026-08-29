package com.ytone.longcare.data.cos.repository

import com.ytone.longcare.model.CosConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CosConfigCacheUserScopeTest {

    @Test
    fun `same folder type keeps distinct credentials per session epoch`() {
        val cache = CosConfigCache(refreshThresholdSeconds = 0)
        val a = config("token-a")
        val b = config("token-b")

        assertTrue(cache.update("namespace:epoch-a", 13, a))
        assertTrue(cache.update("namespace:epoch-b", 13, b))

        assertEquals("token-a", cache.getConfig("namespace:epoch-a", 13)?.sessionToken)
        assertEquals("token-b", cache.getConfig("namespace:epoch-b", 13)?.sessionToken)
    }

    @Test
    fun `cleanup revision rejects a late credential callback`() {
        val cache = CosConfigCache(refreshThresholdSeconds = 0)
        val oldRevision = cache.currentRevision()

        cache.clear()

        assertFalse(cache.update("namespace:old-epoch", 13, config("old"), oldRevision))
        assertNull(cache.getConfig("namespace:old-epoch", 13))
    }

    private fun config(token: String) = CosConfig(
        region = "ap-test",
        bucket = "bucket",
        sessionToken = token,
        expiredTime = Long.MAX_VALUE,
        tmpSecretId = "id-$token",
        tmpSecretKey = "key-$token",
        startTime = 0,
        expiration = "",
        fileKeyPre = "folder/",
    )
}
