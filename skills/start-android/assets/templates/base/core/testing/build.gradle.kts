plugins {
    alias(libs.plugins.tezca.android.library)
}

dependencies {
    api(projects.core.common)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
//@if retrofit
    api(libs.okhttp.mockwebserver)
//@endif
//@if ktor
    api(libs.ktor.client.mock)
//@endif
}
