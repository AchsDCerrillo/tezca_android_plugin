import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalArgumentException("Library '$alias' missing from libs.versions.toml") }

internal fun VersionCatalog.versionOf(alias: String): String =
    findVersion(alias).orElseThrow { IllegalArgumentException("Version '$alias' missing from libs.versions.toml") }.requiredVersion

/**
 * Namespace / package of a module, derived from `tezca.basePackage` in gradle.properties and the
 * Gradle path: `com.example.app` + `:feature:user-profile` -> `com.example.app.feature.userprofile`.
 */
internal val Project.tezcaNamespace: String
    get() {
        val base = providers.gradleProperty("tezca.basePackage").orNull
            ?: error("Add tezca.basePackage=<your.base.package> to gradle.properties")
        val suffix = path.split(':')
            .filter { it.isNotBlank() }
            .joinToString(".") { it.replace("-", "").replace("_", "").lowercase() }
        return "$base.$suffix"
    }

/** SDK levels, Java 17 and Kotlin options shared by the app and every library module. */
internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.versionOf("android-compileSdk").toInt()
        defaultConfig.minSdk = libs.versionOf("android-minSdk").toInt()
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        // Robolectric needs merged Android resources for Room / DataStore / Compose host tests.
        testOptions.unitTests.isIncludeAndroidResources = true
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
    // Bytecode stays Java 17, but JVM unit tests run on Java 21: Robolectric needs it for SDK 36+.
    // Gradle provisions the JDK through the foojay resolver declared in settings.gradle.kts.
    val testLauncher = the<JavaToolchainService>().launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
    tasks.withType<Test>().configureEach {
        javaLauncher.set(testLauncher)
        // Robolectric reaches into JDK internals (shared memory / FileDescriptor) on SDK 36.
        jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
        // Hilt's kspTest generates test sources, so a module without tests yet (a new feature)
        // would otherwise fail with "no tests discovered".
        failOnNoDiscoveredTests.set(false)
    }
}
