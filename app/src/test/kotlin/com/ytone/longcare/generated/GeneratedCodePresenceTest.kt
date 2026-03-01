package com.ytone.longcare.generated

import org.junit.Assert.fail
import org.junit.Test

/**
 * Guards against missing KSP-generated classes (Hilt/Moshi) that can cause runtime crashes.
 */
class GeneratedCodePresenceTest {

    @Test
    fun criticalGeneratedClasses_exist() {
        val expectedClasses = listOf(
            // Moshi generated adapters
            "com.ytone.longcare.model.ThirdKeyReturnModelJsonAdapter",
            "com.ytone.longcare.features.service.storage.PendingOrderJsonAdapter",

            // Hilt generated entry points / modules
            "com.ytone.longcare.Hilt_MainActivity",
            "com.ytone.longcare.features.home.vm.HomeSharedViewModel_HiltModules",
            "com.ytone.longcare.features.login.vm.LoginViewModel_HiltModules",
            "com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel_HiltModules"
        )

        val missing = buildList {
            expectedClasses.forEach { className ->
                try {
                    Class.forName(className)
                } catch (t: Throwable) {
                    add("$className -> ${t::class.java.simpleName}: ${t.message}")
                }
            }
        }

        if (missing.isNotEmpty()) {
            fail(
                "Missing generated classes:\n" + missing.joinToString("\n")
            )
        }
    }
}
