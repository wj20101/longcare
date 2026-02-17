plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
}

android {
    namespace = "com.ytone.longcare.feature.login"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation(libs.dagger.hilt.android)
    testImplementation(libs.junit)
}
