plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
}

android {
    namespace = "com.ytone.longcare.core.common"
}

dependencies {
    implementation(project(":core:model"))
}
