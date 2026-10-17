import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "__PKG__.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// compileOnly: the plugin versions are put on the classpath by the root build.gradle.kts
// (`alias(...) apply false`), so build-logic never pins a second copy of them.
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
//@if native
    compileOnly(libs.room3.gradlePlugin)
//@endif
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "tezca.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "tezca.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "tezca.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "tezca.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
//@if native
        register("androidRoom") {
            id = "tezca.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
//@endif
        register("androidFeature") {
            id = "tezca.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
    }
}
