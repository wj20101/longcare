package com.ytone.longcare.baselineprofile

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector

enum class ProfileClassification {
    STARTUP,
    BASELINE_ONLY,
}

data class ProfileJourney(
    val entryTag: String,
    val destinationTags: List<String>,
    val backTag: String,
    val returnTags: List<String>,
)

enum class ProfileScenario(
    val wireId: String,
    val classification: ProfileClassification,
    val expectedSetupState: String,
    val startupTags: List<String>,
    val journey: ProfileJourney? = null,
) {
    FIRST_RUN_PRIVACY(
        wireId = "first_run_privacy",
        classification = ProfileClassification.STARTUP,
        expectedSetupState = "clean-install",
        startupTags = listOf(
            "profile_privacy_root",
            "profile_privacy_title",
            "profile_privacy_reject",
            "profile_privacy_accept",
        ),
    ),
    LOGGED_OUT(
        wireId = "logged_out",
        classification = ProfileClassification.STARTUP,
        expectedSetupState = "privacy-consented-logged-out",
        startupTags = listOf(
            "profile_login_root",
            "login_phone_input",
            "login_verification_code_input",
            "login_submit_button",
        ),
    ),
    CARE_HOME(
        wireId = "care_home",
        classification = ProfileClassification.STARTUP,
        expectedSetupState = "care-session",
        startupTags = listOf(
            "profile_care_home_root",
            "home_top_user_name",
            "dashboard_records_card",
        ),
    ),
    SALES_HOME(
        wireId = "sales_home",
        classification = ProfileClassification.STARTUP,
        expectedSetupState = "sales-session",
        startupTags = listOf(
            "profile_sales_home_root",
            "profile_sales_customers_entry",
        ),
    ),
    CARE_SERVICE_RECORDS(
        wireId = "care_service_records",
        classification = ProfileClassification.BASELINE_ONLY,
        expectedSetupState = "care-session",
        startupTags = CARE_HOME.startupTags,
        journey = ProfileJourney(
            entryTag = "dashboard_records_card",
            destinationTags = listOf(
                "profile_service_records_root",
                "profile_service_records_back",
            ),
            backTag = "profile_service_records_back",
            returnTags = listOf("profile_care_home_root", "dashboard_records_card"),
        ),
    ),
    SALES_CUSTOMERS(
        wireId = "sales_customers",
        classification = ProfileClassification.BASELINE_ONLY,
        expectedSetupState = "sales-session",
        startupTags = SALES_HOME.startupTags,
        journey = ProfileJourney(
            entryTag = "profile_sales_customers_entry",
            destinationTags = listOf(
                "profile_sales_customers_root",
                "profile_sales_customers_back",
            ),
            backTag = "profile_sales_customers_back",
            returnTags = listOf(
                "profile_sales_home_root",
                "profile_sales_customers_entry",
            ),
        ),
    );

    val isStartup: Boolean get() = classification == ProfileClassification.STARTUP

    fun selector(tag: String): BySelector = By.res(tag)
}
