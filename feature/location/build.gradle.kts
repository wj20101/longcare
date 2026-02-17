plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ytone.longcare.feature.location"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.dagger.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.amap.location)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    ksp(libs.dagger.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
