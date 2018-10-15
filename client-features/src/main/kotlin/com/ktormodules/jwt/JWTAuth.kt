package com.ktormodules.jwt

import com.fasterxml.jackson.annotation.JsonProperty
import com.ktormodules.http.shared.CallResult
import com.ktormodules.http.shared.toCallResult
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.HttpClientFeature
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.response.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.parametersOf
import io.ktor.util.AttributeKey
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking


val AUTHORISATION_CLIENT = HttpClient(Apache) {
    install(JsonFeature) {
        serializer = JacksonSerializer()
    }
}

class JWTAuth(
        private val oauthConfigs: OauthConfiguration,
        private var client: HttpClient?
) {
    private lateinit var token: String
    private lateinit var tokenType: String
    private var expiryTime: Long = 0L
    private val BUFFER_SECONDS = 5L

    init {
        if (client == null) {
            client = AUTHORISATION_CLIENT
        }

        runBlocking {
            authorize()
        }.also {
            this.token = it.accessToken
            this.tokenType = it.tokenType
            this.expiryTime = it.expiresIn
        }

        launch {
            while (true) {
                delay(expiryTime - BUFFER_SECONDS)
                refreshToken()
            }
        }
    }


    data class OauthResponse(
            @JsonProperty("access_token") val accessToken: String,
            @JsonProperty("expires_in") val expiresIn: Long,
            @JsonProperty("token_type") val tokenType: String
    )

    private suspend fun authorize(): OauthResponse {
        val response = client!!.post<HttpResponse>(oauthConfigs.oauhtUrl) {

            body = FormDataContent(parametersOf(
                    "client_id" to listOf(oauthConfigs.oauthClientId),
                    "client_secret" to listOf(oauthConfigs.oauthClientSecret),
                    "grant_type" to listOf(oauthConfigs.oauthGrantType),
                    "scope" to listOf(oauthConfigs.oauthScope)
            ))
        }

        val call = response.toCallResult<OauthResponse>()
        return when (call) {
            is CallResult.Value<OauthResponse> ->  {
                println(call.value)
                call.value
            }
            is CallResult.Error -> throw RuntimeException("Failed to get token: $call")
        }
    }

    private suspend fun refreshToken() {
        authorize().also {
            this.token = it.accessToken
            this.expiryTime = it.expiresIn
            this.tokenType = it.tokenType
        }
    }

    class Configuration {
        var authorizationClient: HttpClient? = null
        lateinit var oauthConfigs: OauthConfiguration

        fun build(): JWTAuth = JWTAuth(oauthConfigs, authorizationClient)
    }

    companion object Feature : HttpClientFeature<Configuration, JWTAuth> {

        override val key: AttributeKey<JWTAuth> = AttributeKey("AuthJWTHeader")

        override fun prepare(block: Configuration.() -> Unit): JWTAuth = Configuration().apply(block).build()

        override fun install(feature: JWTAuth, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                if (context.headers.getAll(HttpHeaders.Authorization) != null) return@intercept
                val authToken = getAuthHeader(feature)
                context.headers.append(HttpHeaders.Authorization, authToken)
            }
        }

        private fun getAuthHeader(feature: JWTAuth): String {
            return "${feature.tokenType} ${feature.token}"
        }
    }
}