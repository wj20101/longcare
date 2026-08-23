plugins {
    id("org.jetbrains.kotlin.jvm")
    id("longcare.kotlin.common")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.moshi.kotlin)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.moshi.kotlin.codegen)
}
