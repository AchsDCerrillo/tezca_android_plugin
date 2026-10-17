plugins {
    alias(libs.plugins.tezca.android.library)
    alias(libs.plugins.tezca.android.hilt)
    alias(libs.plugins.tezca.android.room)
}

dependencies {
    implementation(projects.core.common)
    // Features build their own Room databases through DatabaseFactory, so they see Room's API.
    api(libs.androidx.room3.runtime)
}
