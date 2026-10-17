plugins {
    alias(libs.plugins.tezca.android.library)
    alias(libs.plugins.tezca.android.compose)
}

dependencies {
    implementation(projects.core.common)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.core)
}
