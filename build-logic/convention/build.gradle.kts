plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.ytone.longcare.buildlogic"
version = "1.0.0"

dependencies {
    compileOnly("com.android.tools.build:gradle:9.0.1")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
}

gradlePlugin {
    plugins {
        create("androidApplicationConvention") {
            id = "longcare.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        create("androidLibraryConvention") {
            id = "longcare.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        create("kotlinCommonConvention") {
            id = "longcare.kotlin.common"
            implementationClass = "KotlinCommonConventionPlugin"
        }
    }
}
