plugins {
    alias(libs.plugins.tezca.android.library)
    alias(libs.plugins.tezca.android.hilt)
}

dependencies {
    implementation(projects.core.common)
    // `api`: feature modules see the SDK's use cases, models and (if any) ViewModels.
    api(libs.sdk)
}
