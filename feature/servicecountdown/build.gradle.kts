plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
}

android {
    namespace = "com.ytone.longcare.feature.servicecountdown"
}

dependencies {
    implementation(project(":core:model"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(libs.junit)
}
