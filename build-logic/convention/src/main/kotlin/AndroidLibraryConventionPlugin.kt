import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.android.library")

        val appCompileSdkVersion = target.intProperty("appCompileSdkVersion")
        val appMinSdkVersion = target.intProperty("appMinSdkVersion")
        val appJdkVersion = target.intProperty("appJdkVersion")

        target.extensions.configure<LibraryExtension> {
            compileSdk = appCompileSdkVersion

            defaultConfig {
                minSdk = appMinSdkVersion
            }

            compileOptions {
                sourceCompatibility = JavaVersion.toVersion(appJdkVersion)
                targetCompatibility = JavaVersion.toVersion(appJdkVersion)
            }
        }
    }
}

private fun Project.intProperty(name: String): Int =
    (rootProject.extensions.extraProperties[name] as Number).toInt()
