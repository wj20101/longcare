plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
}

val appCompileSdkVersion: Int by rootProject.extra
val appMinSdkVersion: Int by rootProject.extra

android {
    namespace = "com.ytone.longcare.feature.home"
    compileSdk = appCompileSdkVersion

    defaultConfig {
        minSdk = appMinSdkVersion
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.bundles.hilt)
    testImplementation(libs.junit)
}
