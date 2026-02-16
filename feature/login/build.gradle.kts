plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
}

android {
    namespace = "com.ytone.longcare.feature.login"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.bundles.hilt)
    testImplementation(libs.junit)
}
