import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.resolveResource
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.Jetty
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

class MockIdentityService(
        val host: String = "127.0.0.1",
        val port: Int = 8082) {

    val jkwsUrl = "http://$host:$port/oauth/.well-known/openid-configuration/jwks"
    val oauthUrl = "http://$host:$port/oauth/connect/token"

    fun getJwkAuthHeaderValue(scopes: Array<String>, clientId: String? = null) = "Bearer " + JWT.create()
            .withIssuer(issuer)
            .withKeyId(kid)
            .withArrayClaim("scope", scopes)
            .withClaim("client_id", clientId)
            .sign(jwkAlgorithm)

    fun start() {
        server.start(wait = false)
    }

    fun stop() {
        server.stop(1000, 1, TimeUnit.SECONDS)
    }

    private val server = embeddedServer(Jetty, applicationEngineEnvironment {
        module {
            routing {
                get("oauth/.well-known/openid-configuration/jwks") {
                    call.respondText(jwks.toPublicJWKSet().toString())
                }
                post("oauth/connect/token") {
                    val params = call.receiveParameters()
                    val clientId = params["client_id"]
                    if (clientId != null) {
                        val resolvedResource = call.resolveResource("/$clientId/token.json")
                        if (resolvedResource != null) {
                            call.respond(resolvedResource)
                        } else {
                            call.respond(HttpStatusCode.NotFound, "token not found for $clientId")
                        }
                    } else {
                        call.respond(HttpStatusCode.BadRequest, """{"error":"invalid_client"}""")
                    }
                }

            }
        }

        connector {
            port = this@MockIdentityService.port
            host = this@MockIdentityService.host
        }
    })

    private val keyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048, SecureRandom())
    }.generateKeyPair()

    private val jwkAlgorithm = Algorithm.RSA256(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey)
    private val issuer = "http://$host:$port/oauth"

    private val kid = "NkJCQzIyQzRBMEU4NjhGNUU4MzU4RkY0M0ZDQzkwOUQ0Q0VGNUMwQg"

    private val jwk = RSAKey.Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private as RSAPrivateKey)
            .keyID(kid)
            .build()

    private val jwks = JWKSet(jwk)
}