
import com.ktormodules.jwt.AUTHORISATION_CLIENT
import com.ktormodules.jwt.JWTAuth
import com.ktormodules.jwt.OauthConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockHttpResponse
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.response.HttpResponse
import io.ktor.client.tests.utils.clientTest
import io.ktor.client.tests.utils.config
import io.ktor.client.tests.utils.test
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.experimental.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JWTAuthTest {

    @Test
    fun `JWT is added to the call`() = clientTest(
            MockEngine {
                assertEquals("Bearer token", headers[HttpHeaders.Authorization])
                MockEngine.RESPONSE_OK(call, this)
            }
    ) {
        config {
            install(JWTAuth) {
                oauthConfigs = OauthConfiguration(
                        authServer.oauthUrl,
                        "happypath",
                        "test",
                        "test",
                        "test"
                )
            }
        }
        test { client ->
            client.get<HttpResponse>()
        }
    }

    @Test
    fun `JWT is being refreshed when close to expiration`() {
        val authorizationClientSpy = spyk(AUTHORISATION_CLIENT)
        clientTest(
                MockEngine {
                    MockEngine.RESPONSE_OK(call, this)
                }
        ) {
            config {
                install(JWTAuth) {
                    oauthConfigs = OauthConfiguration(
                            authServer.oauthUrl,
                            "refresh",
                            "test",
                            "test",
                            "test"
                    )
                    authorizationClient = authorizationClientSpy
                }
            }
            test { client ->
                client.get<HttpResponse>()
            }
        }
        coVerify(atLeast = 3) {
            authorizationClientSpy.post<HttpResponse>(authServer.oauthUrl, allAny<HttpRequestBuilder.() -> Unit>())
        }
    }

    companion object {
        var authServer = MockIdentityService()

        @BeforeAll
        @JvmStatic
        fun before() {
            authServer.start()
        }

        @AfterAll
        @JvmStatic
        fun after() {
            authServer.stop()
        }
    }
}