pluginManagement {
    includeBuild("build-logic")
    plugins {
        id("com.android.settings") version "9.3.2" apply false
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("com.android.settings")
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

android {
    compileSdk {
        version = release(37)
    }
    minSdk {
        version = release(24)
    }
    targetSdk {
        version = release(36)
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        val txFaceIncludeMavenLocal =
            providers
                .gradleProperty("TX_FACE_INCLUDE_MAVEN_LOCAL")
                .orElse(providers.environmentVariable("TX_FACE_INCLUDE_MAVEN_LOCAL"))
                .map { raw -> raw.equals("true", ignoreCase = true) }
                .orElse(false)
                .get()
        if (txFaceIncludeMavenLocal) {
            mavenLocal()
        }

        val txFaceRepoUrl =
            providers
                .gradleProperty("TX_FACE_MAVEN_REPO_URL")
                .orElse(providers.environmentVariable("TX_FACE_MAVEN_REPO_URL"))
                .orNull
                ?.trim()
                .orEmpty()
        if (txFaceRepoUrl.isNotEmpty()) {
            maven {
                name = "txFacePrivateRepo"
                url = uri(txFaceRepoUrl)
                val repoUser =
                    providers
                        .gradleProperty("TX_FACE_MAVEN_REPO_USERNAME")
                        .orElse(providers.environmentVariable("TX_FACE_MAVEN_REPO_USERNAME"))
                        .orNull
                val repoPassword =
                    providers
                        .gradleProperty("TX_FACE_MAVEN_REPO_PASSWORD")
                        .orElse(providers.environmentVariable("TX_FACE_MAVEN_REPO_PASSWORD"))
                        .orNull
                if (!repoUser.isNullOrBlank() || !repoPassword.isNullOrBlank()) {
                    credentials {
                        username = repoUser
                        password = repoPassword
                    }
                }
            }
        }
    }
}

rootProject.name = "longcare"
include(":app")
include(":baselineprofile")
include(":core:model")
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":core:common")
include(":feature:login")
include(":feature:home")
include(":feature:identification")
include(":feature:location")
include(":feature:photoupload")
include(":feature:servicecountdown")
