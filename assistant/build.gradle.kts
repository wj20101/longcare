plugins {
    id("longcare.android.application")
    id("longcare.kotlin.common")
    id("longcare.android.app.signing-txface")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ytone.longcare.assistant"
    compileSdk = rootProject.extra["appCompileSdkVersion"] as Int
    defaultConfig {
        applicationId = "com.ytone.longcare.assistant"
        minSdk = rootProject.extra["appMinSdkVersion"] as Int
        targetSdk = rootProject.extra["appTargetSdkVersion"] as Int
        versionCode = rootProject.extra["appVersionCode"] as Int
        versionName = "${rootProject.extra["appVersionName"]}-assistant"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(rootProject.extra["appJdkVersion"] as Int)
        targetCompatibility = JavaVersion.toVersion(rootProject.extra["appJdkVersion"] as Int)
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

// The assistant is an internal validation tool, never included in the public app release.
// No baseline-profile tooling receiver is needed in this internal shell.
configurations.configureEach {
    exclude(group = "androidx.profileinstaller", module = "profileinstaller")
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
        dependsOn(":app:verifyReleaseConfiguration")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":feature:login"))
    implementation(project(":feature:identification"))
    implementation(project(":feature:photoupload"))
    implementation(project(":integration:txface"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.bundles.coil)
    implementation(libs.okhttp.core)
    implementation(libs.face.detection)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.dagger.hilt.android)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    ksp(libs.dagger.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.uiautomator)
}
