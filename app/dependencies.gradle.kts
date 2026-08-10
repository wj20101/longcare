import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.kotlin.dsl.getByType

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun lib(alias: String): Provider<MinimalExternalModuleDependency> =
    libsCatalog.findLibrary(alias).orElseThrow {
        IllegalStateException("Missing library alias in version catalog: $alias")
    }

fun projectDependency(path: String): Any =
    dependencies.project(path)

fun DependencyHandler.addAll(configuration: String, dependencies: Iterable<Any>) {
    dependencies.forEach { add(configuration, it) }
}

dependencies {
    add("baselineProfile", projectDependency(":baselineprofile"))

    addAll(
        "implementation",
        listOf(
            projectDependency(":core:common"),
            projectDependency(":core:data"),
            projectDependency(":core:domain"),
            projectDependency(":core:model"),
            projectDependency(":core:ui"),
            projectDependency(":feature:login"),
            projectDependency(":feature:home"),
            projectDependency(":feature:identification"),
            projectDependency(":feature:location"),
            projectDependency(":feature:photoupload"),
            projectDependency(":feature:servicecountdown")
        )
    )

    add("implementation", platform(lib("compose-bom")))

    addAll(
        "implementation",
        listOf(
            lib("androidx-core-ktx"),
            lib("androidx-appcompat"),
            lib("androidx-appcompat-resources"),
            lib("androidx-lifecycle-runtime-ktx"),
            lib("androidx-activity-compose"),
            lib("compose-ui"),
            lib("compose-ui-graphics"),
            lib("compose-material-icons-extended"),
            lib("compose-material3"),
            lib("compose-material3-adaptive-navigation-suite"),
            lib("compose-ui-tooling-preview"),
            lib("startup-runtime"),
            lib("androidx-profileinstaller"),
            lib("androidx-navigation-compose"),
            lib("dagger-hilt-android"),
            lib("hilt-work"),
            lib("hilt-navigation-compose"),
            lib("retrofit-core"),
            lib("okhttp-core"),
            lib("okhttp-logging-interceptor"),
            lib("okio-core"),
            lib("moshi-kotlin"),
            lib("gson"),
            lib("protobuf-lite"),
            lib("retrofit-converter-moshi"),
            lib("kotlinx-serialization-json"),
            lib("androidx-datastore-preferences"),
            lib("work-runtime-ktx"),
            lib("window"),
            lib("coil-core"),
            lib("coil-compose"),
            lib("coil-network-okhttp"),
            lib("coil-gif"),
            lib("coil-svg"),
            lib("coil-video"),
            lib("androidx-camera-core"),
            lib("androidx-camera-camera2"),
            lib("androidx-camera-lifecycle"),
            lib("androidx-camera-view"),
            lib("face-detection"),
            lib("androidx-lifecycle-viewmodel-compose"),
            lib("androidx-lifecycle-runtime-compose"),
            lib("androidx-constraintlayout"),
            lib("kotlinx-datetime"),
            lib("amap-location"),
            lib("crashreport")
        )
    )

    addAll(
        "ksp",
        listOf(
            lib("dagger-hilt-compiler"),
            lib("hilt-compiler"),
            lib("moshi-kotlin-codegen")
        )
    )

    addAll(
        "testImplementation",
        listOf(
            lib("junit"),
            lib("mockk"),
            lib("robolectric"),
            lib("androidx-work-testing"),
            lib("androidx-test-core"),
            lib("kotlinx-coroutines-test"),
            lib("truth")
        )
    )

    add("androidTestImplementation", platform(lib("compose-bom")))
    addAll(
        "androidTestImplementation",
        listOf(
            lib("androidx-test-ext-junit"),
            lib("androidx-test-espresso-core"),
            lib("androidx-compose-ui-test-junit4")
        )
    )

    add("debugImplementation", lib("compose-ui-tooling"))
    add("debugImplementation", lib("androidx-compose-ui-test-manifest"))

    add(
        "implementation",
        files("libs/qlzsdk-1.3.0.2-protobufLiteRelease-ui.aar")
    )
}
