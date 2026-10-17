# Versions

## Pinned matrix (verified together on 2026-10-17)
The templates use these versions. Three projects were rendered and verified with them on JDK 17 + Gradle 9.6.0: native + Retrofit and native + Ktor (`assembleDevDebug`, all unit tests, `lintDevDebug`), and kmp-sdk against a real SDK (`assembleDevDebug`, unit tests).

| Catalog key | Version | Artifact / plugin | Notes |
|---|---|---|---|
| (Gradle wrapper) | 9.6.0 | | AGP 9.x needs Gradle 9.x |
| agp | 9.4.1 | com.android.application / library | built-in Kotlin: no `org.jetbrains.kotlin.android` plugin |
| kotlin | 2.4.21 | compose + serialization compiler plugins | |
| ksp | 2.3.12 | com.google.devtools.ksp | KSP2, independent of the Kotlin patch version |
| android-compileSdk / targetSdk / minSdk | 37 / 36 / 26 | | targetSdk 36 also drives Robolectric (needs JDK 21 for 36) |
| compose-bom | 2026.09.00 | androidx.compose:compose-bom | Material 3 from the BOM |
| hilt | 2.60.1 | com.google.dagger:hilt-android, plugin com.google.dagger.hilt.android | ≥ 2.59 for AGP 9 |
| androidx-hilt | 1.4.0 | androidx.hilt:hilt-lifecycle-viewmodel-compose | `hiltViewModel()` incl. assisted `creationCallback` |
| androidx-navigation3 | 1.2.0 | androidx.navigation3:navigation3-runtime / -ui | |
| androidx-lifecycle | 2.11.0 | lifecycle-runtime-compose, -viewmodel-compose, -viewmodel-navigation3 | |
| androidx-activity / core | 1.13.0 / 1.19.1 | | |
| androidx-room3 | 3.0.3 | androidx.room3:room3-runtime / -compiler / -testing, plugin androidx.room3 | package `androidx.room3.*` |
| androidx-sqlite | 2.7.1 | androidx.sqlite:sqlite-framework | `AndroidSQLiteDriver` |
| androidx-datastore | 1.2.1 | androidx.datastore:datastore-preferences | |
| retrofit / okhttp | 3.0.0 / 5.5.0 | retrofit, converter-kotlinx-serialization, okhttp, logging-interceptor, mockwebserver3 | |
| ktor | 3.6.0 | ktor-client-* (okhttp engine), ktor-client-mock | |
| kotlinx-coroutines / serialization | 1.11.0 / 1.11.0 | | |
| robolectric | 4.17 | org.robolectric:robolectric | |
| turbine / junit | 1.2.1 / 4.13.2 | | |
| googleServices / firebaseAppDistribution | 4.5.0 / 5.3.0 | | applied only once Firebase is configured |

## Looking up newer releases
Prefer stable releases; skip `-alpha`, `-beta`, `-rc`, `-dev`, `-eap`, `-SNAPSHOT`.
```bash
mc() { curl -s "https://repo1.maven.org/maven2/$1/maven-metadata.xml" | grep -o '<version>[^<]*' | sed 's/<version>//' | grep -viE 'alpha|beta|rc|dev|eap|snapshot|-M[0-9]' | tail -1; }
gm() { curl -s "https://dl.google.com/android/maven2/$1/maven-metadata.xml" | grep -o '<version>[^<]*' | sed 's/<version>//' | grep -viE 'alpha|beta|rc' | tail -1; }
mc org/jetbrains/kotlin/kotlin-stdlib; mc com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin
mc com/google/dagger/hilt-android; mc com/squareup/retrofit2/retrofit; mc com/squareup/okhttp3/okhttp
mc io/ktor/ktor-client-core; mc org/jetbrains/kotlinx/kotlinx-coroutines-core; mc org/jetbrains/kotlinx/kotlinx-serialization-json
mc org/robolectric/robolectric; mc app/cash/turbine/turbine
gm com/android/tools/build/gradle; gm androidx/compose/compose-bom; gm androidx/navigation3/navigation3-runtime
gm androidx/lifecycle/lifecycle-runtime-compose; gm androidx/hilt/hilt-lifecycle-viewmodel-compose
gm androidx/room3/room3-runtime; gm androidx/sqlite/sqlite-framework; gm androidx/datastore/datastore-preferences
gm androidx/activity/activity-compose; gm androidx/core/core-ktx
curl -s https://services.gradle.org/versions/current | grep '"version"'
```

## Compatibility rules to check before bumping
- **AGP ↔ Gradle ↔ compileSdk.** Each AGP release states its minimum Gradle and maximum tested compileSdk. Update `gradle/wrapper/gradle-wrapper.properties` with AGP.
- **AGP 9 built-in Kotlin.** Don't add `org.jetbrains.kotlin.android`; KSP and Hilt must support built-in Kotlin (KSP 2.3+, Hilt 2.59+).
- **Compose compiler** is the Kotlin plugin `org.jetbrains.kotlin.plugin.compose`, always at the project's Kotlin version.
- **Room 3 ↔ KSP2.** Room 3 needs KSP2. Room 2 (`androidx.room`) and Room 3 (`androidx.room3`) are different packages; don't mix them.
- **Robolectric ↔ targetSdk ↔ JDK.** A new targetSdk needs a Robolectric release that supports it; SDK 36 needs a JDK 21 test launcher (configured in `ProjectExtensions.kt`).
- **Lifecycle family.** Keep every `androidx.lifecycle:*` at one version (one catalog ref).

After changing versions, re-run the verification from SKILL.md step 7.
