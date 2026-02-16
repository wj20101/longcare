import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class KotlinCommonConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val appJdkVersion = target.intProperty("appJdkVersion")

        target.tasks.withType(KotlinCompile::class.java).configureEach {
            compilerOptions.jvmTarget.set(
                org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(appJdkVersion.toString())
            )
        }
    }
}

private fun Project.intProperty(name: String): Int =
    (rootProject.extensions.extraProperties[name] as Number).toInt()
