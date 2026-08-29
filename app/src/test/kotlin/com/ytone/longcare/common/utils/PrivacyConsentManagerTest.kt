package com.ytone.longcare.common.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrivacyConsentManagerTest {

    private lateinit var manager: PrivacyConsentManager
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 每次测试前清除 SharedPreferences
        context.getSharedPreferences(DeviceRuntimeState.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        manager = PrivacyConsentManager(context)
    }

    @Test
    fun `default consent state is false`() {
        assertFalse(manager.isPrivacyConsented)
    }

    @Test
    fun `markConsented sets state to true`() {
        manager.markConsented()
        assertTrue(manager.isPrivacyConsented)
    }

    @Test
    fun `resetConsent reverts state to false`() {
        manager.markConsented()
        assertTrue(manager.isPrivacyConsented)

        manager.resetConsent()
        assertFalse(manager.isPrivacyConsented)
    }

    @Test
    fun `consent state persists across instances`() {
        manager.markConsented()

        val newManager = PrivacyConsentManager(context)
        assertTrue(newManager.isPrivacyConsented)
    }

    @Test
    fun `reset state persists across instances`() {
        manager.markConsented()
        manager.resetConsent()

        val newManager = PrivacyConsentManager(context)
        assertFalse(newManager.isPrivacyConsented)
    }

    @Test
    fun `repeated markConsented is idempotent`() {
        manager.markConsented()
        manager.markConsented()
        assertTrue(manager.isPrivacyConsented)

        manager.resetConsent()
        assertFalse(manager.isPrivacyConsented)
    }
}
