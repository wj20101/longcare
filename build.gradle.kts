plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.dagger.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.androidx.benchmark) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baseline.profile) apply false
}

apply(from = "$rootDir/constants.gradle.kts")
