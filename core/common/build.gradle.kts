plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
}

android {
    namespace = "com.ytone.longcare.core.common"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.dagger.hilt.android)
    implementation("javax.inject:javax.inject:1")
}
