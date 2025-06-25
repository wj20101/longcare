plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.baselineprofile)
}

val appCompileSdkVersion: Int by rootProject.extra
val appTargetSdkVersion: Int by rootProject.extra
val appMinSdkVersion: Int by rootProject.extra
val appJavaVersion: JavaVersion by rootProject.extra
val appKotlinJvmTarget: String by rootProject.extra

android {
    namespace = "com.ytone.longcare.baselineprofile"
    compileSdk = appCompileSdkVersion

    compileOptions {
        sourceCompatibility = appJavaVersion
        targetCompatibility = appJavaVersion
    }

    kotlinOptions {
        jvmTarget = appKotlinJvmTarget
    }

    defaultConfig {
        minSdk = appMinSdkVersion
        targetSdk = appTargetSdkVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

//    flavorDimensions += listOf("environment")
//    productFlavors {
//        create("dev") { dimension = "environment" }
//        create("prod") { dimension = "environment" }
//    }

}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId }
        )
    }
}