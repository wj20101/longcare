plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.ytone.longcare.buildlogic"
version = "1.0.0"

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
