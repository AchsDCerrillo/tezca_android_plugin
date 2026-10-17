# Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `SDK location not found` | no `local.properties` / `ANDROID_HOME` | `echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties` (macOS) |
| `Could not find com.squareup.okhttp3:mockwebserver3:.` (no version) | an OkHttp artifact declared without `version.ref` | every OkHttp library in the catalog uses `version.ref = "okhttp"` |
| `There are test sources present … did not discover any tests` | Hilt's `kspTest` generates test sources in modules without tests | already handled: `failOnNoDiscoveredTests = false` in `configureAndroid` |
| Robolectric `Android SDK 36 requires Java 21` | tests ran on JDK 17 | the convention plugin sets a Java 21 test launcher; check the foojay plugin is in `settings.gradle.kts` (or install a JDK 21) |
| Robolectric `Failed to interact with raw FileDescriptor internals` | JDK 21 module encapsulation | `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` (already in `configureAndroid`) |
| Robolectric `DefaultSdkProvider … UnsupportedOperationException` for SDK 37 | library modules defaulted to compileSdk for tests | `testOptions.targetSdk` is set from the catalog in `tezca.android.library` |
| `Unresolved reference 'Test'` from `kotlin.test` | `kotlin-test` without its JUnit binding | the catalog's `kotlin-test` points to `kotlin-test-junit` |
| `Cannot infer type for type parameter 'T'` on `ApplicationProvider.getApplicationContext()` | generic | `getApplicationContext<Context>()` |
| `Supplied consumer proguard configuration does not exist` | `consumer-rules.pro` missing | the library plugin only registers it when the file exists |
| `uses-sdk:minSdkVersion 26 cannot be smaller than version 29 declared in library [<sdk>]` | the KMP SDK has a higher minSdk | `--min-sdk` ≥ the SDK's, or edit `android-minSdk` in the catalog |
| `Could not find com.google.firebase:firebase-auth:.` (kmp-sdk) | the SDK uses GitLive Firebase, whose Android artifacts leave Firebase versions to the consumer's BOM | add `firebase-bom` to the catalog and `api(platform(libs.firebase.bom))` in `:core:sdk` |
| `401 Unauthorized` resolving the SDK from GitHub Packages | missing/expired token | `GPR_USER` + `GPR_API_KEY` (token with `read:packages`) in env or `gpr.user`/`gpr.key` in `~/.gradle/gradle.properties` |
| `Configuration cache state could not be cached … KspAATask` | a dependency failed to resolve (the real error is printed below it) | fix the resolution error shown after `> Could not resolve all files` |
| Hilt `[Dagger/MissingBinding]` | interface without `@Binds`, or a feature module not on `:app`'s classpath | add the binding; check `implementation(projects.feature.x)` in `:app` |
| `Multiple DataStores active for the same file` | a second DataStore was created | inject the single `DataStore<Preferences>` from `:core:datastore` |
| `The argument '-Xannotation-default-target=param-property' is redundant` | Kotlin ≥ 2.4 default | remove the flag (templates don't set it) |
| `Default FirebaseApp is not initialized in this process` when a screen opens (kmp-sdk) | the SDK builds Firebase-backed services (Crashlytics, Remote Config) when a use case is resolved | add `google-services.json` and apply the google-services plugin in `:app`; report to the SDK owners if Firebase should be optional |
| Features aren't linted (`MissingTranslation` not reported) | lint only checks `:app` by default | `lint { checkDependencies = true }` in `app/build.gradle.kts` (in the template) |
