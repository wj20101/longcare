plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ytone.longcare.feature.identification"
}

fun projectDependency(path: String) = dependencies.project(path)

dependencies {
    implementation(projectDependency(":core:common"))
    implementation(projectDependency(":core:domain"))
    implementation(projectDependency(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp.core)
    implementation(libs.crashreport)
    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
