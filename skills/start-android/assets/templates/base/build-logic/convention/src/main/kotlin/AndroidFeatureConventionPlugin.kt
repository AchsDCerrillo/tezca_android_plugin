import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * A `:feature:<name>` module: library + Compose + Hilt + kotlinx-serialization (Navigation 3 keys),
 * Navigation 3, lifecycle/Hilt ViewModel APIs, and every `:core:*` module that exists in this
 * project (`:core:network`, `:core:database`, `:core:datastore` in native mode; `:core:sdk` when the
 * app consumes a KMP SDK). Feature modules never depend on each other.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("tezca.android.library")
            apply("tezca.android.compose")
            apply("tezca.android.hilt")
            apply("org.jetbrains.kotlin.plugin.serialization")
        }
        dependencies {
            CORE_MODULES.filter { rootProject.findProject(it) != null }.forEach { path ->
                add("implementation", project(path))
            }
            rootProject.findProject(":core:testing")?.let { add("testImplementation", it) }
            // SDK use cases are usually final classes: tests stub them with MockK.
            if (rootProject.findProject(":core:sdk") != null) add("testImplementation", libs.lib("mockk"))

            add("implementation", libs.lib("kotlinx-serialization-json"))
            add("implementation", libs.lib("androidx-navigation3-runtime"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
        }
    }

    private companion object {
        val CORE_MODULES = listOf(
            ":core:common", ":core:ui", ":core:navigation",
            ":core:network", ":core:database", ":core:datastore", ":core:session", ":core:sdk",
        )
    }
}
