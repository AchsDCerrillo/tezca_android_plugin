plugins {
    alias(libs.plugins.tezca.android.library)
    alias(libs.plugins.tezca.android.hilt)
}

dependencies {
    implementation(projects.core.common)
    api(libs.androidx.datastore.preferences)
}
