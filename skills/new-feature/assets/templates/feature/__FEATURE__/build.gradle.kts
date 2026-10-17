plugins {
    // Library + Compose + Hilt + Navigation 3 + every :core:* module (see build-logic).
    alias(libs.plugins.tezca.android.feature)
}

dependencies {
    // tezca-android:feature-deps (extra libraries this feature needs; never another :feature module)
}
