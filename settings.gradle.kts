pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()

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
