plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.ytone.longcare.core.data"
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.deviceTests.values.forEach { deviceTest ->
            deviceTest.sources.assets?.addStaticSourceDirectory(
                rootProject.projectDir.resolve("app/schemas").absolutePath
            )
        }
    }
}

extensions.configure<androidx.room.gradle.RoomExtension>("room") {
    schemaDirectory("${rootProject.projectDir}/app/schemas")
}

fun projectDependency(path: String) = dependencies.project(path)

dependencies {
    implementation(projectDependency(":core:common"))
    implementation(projectDependency(":core:domain"))
    implementation(projectDependency(":core:model"))
    implementation(libs.dagger.hilt.android)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tencent.cos.android)
    ksp(libs.dagger.hilt.compiler)
    ksp(libs.moshi.kotlin.codegen)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
}
