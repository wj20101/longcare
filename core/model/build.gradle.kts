plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
    alias(libs.plugins.kotlinSerialization)
    id("kotlin-parcelize")
}

val appCompileSdkVersion: Int by rootProject.extra
val appMinSdkVersion: Int by rootProject.extra
val appJdkVersion: Int by rootProject.extra

android {
    namespace = "com.ytone.longcare.core.model"
    compileSdk = appCompileSdkVersion

    defaultConfig {
        minSdk = appMinSdkVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(appJdkVersion)
        targetCompatibility = JavaVersion.toVersion(appJdkVersion)
    }
}

kotlin {
    jvmToolchain(appJdkVersion)
}

dependencies {
    implementation(libs.moshi.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.room.runtime)
}
