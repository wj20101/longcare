package com.ytone.longcare.common.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceUtilsAppInstanceIdTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(DeviceRuntimeState.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("device_instance_id_store", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `privacy consent is required before GUID storage is touched`() {
        val state = context.getSharedPreferences(
            DeviceRuntimeState.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val manager = PrivacyConsentManager(context)
        val deviceUtils = DeviceUtils(context, state, manager)

        assertThrows(IllegalStateException::class.java) { deviceUtils.getAppInstanceId() }
        assertFalse(state.contains(DeviceRuntimeState.APP_INSTANCE_GUID_KEY))
    }

    @Test
    fun `consent creates one stable private UUID without legacy or hardware fallback`() {
        val legacyId = "legacy-android-id"
        context.getSharedPreferences("device_instance_id_store", Context.MODE_PRIVATE)
            .edit().putString("generated_app_instance_id", legacyId).commit()
        val state = context.getSharedPreferences(
            DeviceRuntimeState.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        state.edit().putString("generated_app_instance_id", legacyId).commit()
        val manager = PrivacyConsentManager(context).also { it.markConsented() }

        val first = DeviceUtils(context, state, manager).getAppInstanceId()
        val second = DeviceUtils(context, state, PrivacyConsentManager(context)).getAppInstanceId()

        UUID.fromString(first)
        assertNotEquals(legacyId, first)
        assertEquals(first, second)
        assertEquals(first, state.getString(DeviceRuntimeState.APP_INSTANCE_GUID_KEY, null))
    }
}
