plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ytone.longcare.core.common"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.dagger.hilt.android)
    implementation(libs.moshi.kotlin)
    implementation(libs.retrofit.core)
    implementation(libs.window)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.coroutines.get()}")
    implementation("javax.inject:javax.inject:1")
    ksp(libs.dagger.hilt.compiler)
}
