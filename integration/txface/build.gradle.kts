plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
    id("longcare.tencent-face")
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
}
android {
    namespace = "com.ytone.longcare.integration.txface"
    defaultConfig { consumerProguardFiles("consumer-rules.pro") }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
