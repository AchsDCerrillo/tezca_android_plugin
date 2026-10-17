package __PKG__.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import __PKG__.core.common.config.AppConfig
import __PKG__.core.common.result.AppError
import __PKG__.core.common.result.DataResult

class SafeCallTest {

    @Serializable
    data class PingDto(val message: String)

    private val config = AppConfig(baseUrl = "https://api.test/", isDebug = false, flavor = "dev")
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `2xx body becomes success`() = runTest {
        val client = HttpClientFactory.create(MockEngine { respond("""{"message":"pong","extra":1}""", headers = json) }, config, null)
        assertEquals(DataResult.Success(PingDto("pong")), safeCall<PingDto> { client.get("ping") })
    }

    @Test
    fun `status codes map to AppError`() = runTest {
        val client = HttpClientFactory.create(MockEngine { respond("", HttpStatusCode.NotFound) }, config, null, maxRetries = 0)
        assertEquals(DataResult.Failure(AppError.Network.NotFound), safeCall<PingDto> { client.get("ping") })
    }

    @Test
    fun `GET is retried on 5xx, POST is not`() = runTest {
        var calls = 0
        val client = HttpClientFactory.create(
            MockEngine {
                calls++
                if (calls == 1) respond("", HttpStatusCode.ServiceUnavailable) else respond("""{"message":"pong"}""", headers = json)
            },
            config,
            null,
        )
        assertEquals(DataResult.Success(PingDto("pong")), safeCall<PingDto> { client.get("ping") })

        calls = 0
        val failing = HttpClientFactory.create(MockEngine { calls++; respond("", HttpStatusCode.ServiceUnavailable) }, config, null)
        safeCall<Unit> { failing.post("ping") }
        assertEquals(1, calls)
    }
}
