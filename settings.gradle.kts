pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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
