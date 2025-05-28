plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinKapt) apply false
    alias(libs.plugins.dagger.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.benchmark) apply false
    alias(libs.plugins.wire) apply false
    alias(libs.plugins.ktlint) apply false
}

apply(from = "$rootDir/constants.gradle.kts")