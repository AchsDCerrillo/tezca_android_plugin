package __PKG__.core.common.config

/** Build-time configuration, provided by `:app` (the only module that reads BuildConfig). */
data class AppConfig(
    val baseUrl: String,
    val isDebug: Boolean,
    val flavor: String,
)
