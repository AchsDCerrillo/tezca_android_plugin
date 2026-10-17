import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Every Android library module: namespace from the Gradle path, Java 17, coroutines and the JVM
 * test stack (JUnit4, coroutines-test, Turbine, Robolectric). Kotlin is compiled by AGP 9's
 * built-in Kotlin support, so no `org.jetbrains.kotlin.android` plugin is applied.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureAndroid(this)
            namespace = tezcaNamespace
            // Libraries have no targetSdk of their own; tests (Robolectric) and lint use the app's.
            testOptions.targetSdk = libs.versionOf("android-targetSdk").toInt()
            lint.targetSdk = libs.versionOf("android-targetSdk").toInt()
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            // R8 rules shipped to consumers of this module, if it has any.
            if (file("consumer-rules.pro").exists()) defaultConfig.consumerProguardFiles("consumer-rules.pro")
        }
        dependencies {
            add("implementation", libs.lib("kotlinx-coroutines-android"))
            add("testImplementation", libs.lib("junit"))
            add("testImplementation", libs.lib("kotlin-test"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            add("testImplementation", libs.lib("turbine"))
            add("testImplementation", libs.lib("robolectric"))
            add("testImplementation", libs.lib("androidx-test-ext-junit"))
        }
    }
}
