package __PKG__.core.network

import kotlinx.serialization.json.Json

/** One Json configuration for every API: tolerant to new server fields, explicit about nulls. */
val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}
