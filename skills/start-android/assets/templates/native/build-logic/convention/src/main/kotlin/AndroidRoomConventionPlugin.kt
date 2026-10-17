import androidx.room3.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Room 3 with KSP and exported schemas (commit `schemas/`: migrations are tested against them). */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("com.google.devtools.ksp")
            apply("androidx.room3")
        }
        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }
        dependencies {
            add("implementation", libs.lib("androidx-room3-runtime"))
            add("implementation", libs.lib("androidx-sqlite-framework"))
            add("ksp", libs.lib("androidx-room3-compiler"))
            add("testImplementation", libs.lib("androidx-room3-testing"))
        }
    }
}
