plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
}

val appCompileSdkVersion: Int by rootProject.extra
val appMinSdkVersion: Int by rootProject.extra
val appJdkVersion: Int by rootProject.extra

android {
    namespace = "com.ytone.longcare.core.data"
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
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(libs.bundles.hilt)
    testImplementation(libs.junit)
}
