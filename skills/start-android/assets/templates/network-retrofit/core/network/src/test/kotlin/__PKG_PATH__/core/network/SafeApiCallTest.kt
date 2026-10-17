package __PKG__.core.network

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.POST
import __PKG__.core.common.result.AppError
import __PKG__.core.common.result.DataResult

class SafeApiCallTest {

    @Serializable
    data class PingDto(val message: String)

    interface PingApi {
        @GET("ping")
        suspend fun ping(): Response<PingDto>

        @POST("ping")
        suspend fun create(): Response<Unit>
    }

    private val server = MockWebServer()
    private lateinit var api: PingApi

    @BeforeTest
    fun setUp() {
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().addInterceptor(RetryInterceptor(baseDelayMs = 1)).build())
            .addConverterFactory(NetworkJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
    }

    @AfterTest
    fun tearDown() = server.close()

    @Test
    fun `2xx body becomes success`() = runTest {
        server.enqueue(MockResponse(body = """{"message":"pong","extra":1}"""))
        assertEquals(DataResult.Success(PingDto("pong")), safeApiCall { api.ping() })
    }

    @Test
    fun `204 without body becomes success for Unit`() = runTest {
        server.enqueue(MockResponse(code = 204))
        assertEquals(DataResult.Success(Unit), safeApiCall { api.create() })
    }

    @Test
    fun `status codes map to AppError`() = runTest {
        server.enqueue(MockResponse(code = 401))
        assertEquals(DataResult.Failure(AppError.Network.Unauthorized), safeApiCall { api.ping() })
        server.enqueue(MockResponse(code = 404))
        assertEquals(DataResult.Failure(AppError.Network.NotFound), safeApiCall { api.ping() })
    }

    @Test
    fun `GET is retried on 5xx, POST is not`() = runTest {
        server.enqueue(MockResponse(code = 503))
        server.enqueue(MockResponse(body = """{"message":"pong"}"""))
        assertEquals(DataResult.Success(PingDto("pong")), safeApiCall { api.ping() })

        server.enqueue(MockResponse(code = 503))
        val result = safeApiCall { api.create() }
        assertEquals(503, ((result as DataResult.Failure).error as AppError.Network.Server).code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `malformed body becomes serialization error`() = runTest {
        server.enqueue(MockResponse(body = """{"unexpected":true}"""))
        assertEquals(DataResult.Failure(AppError.Network.Serialization), safeApiCall { api.ping() })
    }
}
