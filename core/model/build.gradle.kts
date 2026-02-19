plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
    alias(libs.plugins.kotlinSerialization)
    id("kotlin-parcelize")
}

android {
    namespace = "com.ytone.longcare.core.model"
}

dependencies {
    implementation(libs.moshi.kotlin)
    implementation(libs.kotlinx.serialization.json)
}
