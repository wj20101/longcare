plugins {
    alias(libs.plugins.androidLibrary)
}

val appCompileSdkVersion: Int by rootProject.extra
val appMinSdkVersion: Int by rootProject.extra

android {
    namespace = "com.ytone.longcare.core.common"
    compileSdk = appCompileSdkVersion

    defaultConfig {
        minSdk = appMinSdkVersion
    }
}

dependencies {
    implementation(project(":core:model"))
}
