plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinCompose)
}

val appCompileSdkVersion: Int by rootProject.extra
val appMinSdkVersion: Int by rootProject.extra

android {
    namespace = "com.ytone.longcare.core.ui"
    compileSdk = appCompileSdkVersion

    defaultConfig {
        minSdk = appMinSdkVersion
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
}
