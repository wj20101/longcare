plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ytone.longcare.core.common"
}

fun projectDependency(path: String) = dependencies.project(path)

dependencies {
    implementation(projectDependency(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.crashreport)
    implementation(libs.dagger.hilt.android)
    implementation(libs.moshi.kotlin)
    implementation(libs.retrofit.core)
    implementation(libs.window)
    implementation(libs.javax.inject)
    ksp(libs.dagger.hilt.compiler)
    testImplementation(libs.junit)
}
