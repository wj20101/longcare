plugins {
    alias(libs.plugins.androidLibrary)
}

val appCompileSdkVersion: Int by rootProject.extra
val appMinSdkVersion: Int by rootProject.extra

android {
    namespace = "com.ytone.longcare.feature.identification"
    compileSdk = appCompileSdkVersion

    defaultConfig {
        minSdk = appMinSdkVersion
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
}
