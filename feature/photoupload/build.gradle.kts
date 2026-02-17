plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ytone.longcare.feature.photoupload"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)

    implementation(libs.bundles.coil)
    implementation(libs.crashreport)

    testImplementation(libs.junit)
}
