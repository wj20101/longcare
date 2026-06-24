plugins {
    id("longcare.android.library")
    id("longcare.kotlin.common")
}

android {
    namespace = "com.ytone.longcare.core.domain"
}

fun projectDependency(path: String) = dependencies.project(path)

dependencies {
    implementation(projectDependency(":core:common"))
    implementation(projectDependency(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
}
