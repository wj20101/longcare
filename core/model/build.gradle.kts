plugins {
    alias(libs.plugins.androidLibrary)
}

val appCompileSdkVersion: Int by rootProject.extra
val appMinSdkVersion: Int by rootProject.extra

android {
    namespace = "com.ytone.longcare.core.model"
    compileSdk = appCompileSdkVersion

    defaultConfig {
        minSdk = appMinSdkVersion
    }
}
