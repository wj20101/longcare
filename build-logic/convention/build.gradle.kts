import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.ytone.longcare.buildlogic"
version = "1.0.0"

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    compileOnly(libs.findLibrary("android-gradle-plugin").get())
    compileOnly(libs.findLibrary("kotlin-gradle-plugin").get())
    testImplementation(kotlin("test"))
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
        create("androidAppSigningTxFaceConvention") {
            id = "longcare.android.app.signing-txface"
            implementationClass = "AndroidAppSigningTxFaceConventionPlugin"
        }
    }
}
