plugins {
    id("org.jetbrains.kotlin.jvm")
    id("longcare.kotlin.common")
}

fun projectDependency(path: String) = dependencies.project(path)

dependencies {
    implementation(projectDependency(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
