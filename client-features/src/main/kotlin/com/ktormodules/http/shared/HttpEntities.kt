package com.ktormodules.http.shared

import io.ktor.client.call.receive
import io.ktor.client.response.HttpResponse
import io.ktor.http.isSuccess


suspend inline fun <reified T> HttpResponse.toCallResult(): CallResult<T> {
    return this.toCallResult { CallResult.Value(this.call.receive<T>()) }
}

suspend inline fun <reified T> HttpResponse.toCallResult(onSuccess: HttpResponse.() -> CallResult<T>): CallResult<T> {
    return if (this.status.isSuccess()) {
        onSuccess(this)
    } else {
        val contentTypeHeader = this.headers["Content-Type"]
        val statusCode = this.status

        CallResult.Error(statusCode.value, this.call.receive(), contentTypeHeader)
    }
}


sealed class CallResult<out V> {
    data class Error(val statusCode: Int, val data: String, val contentType: String?) : CallResult<Nothing>()
    data class Value<out V>(val value: V) : CallResult<V>()

    /**
     * Flat maps the Value type to another value, using the provide function. Will only execute if the current
     * CallResult is [Value]. If the current CallResult is [Error] it remains as is.
     */
    fun <T> flatMap(transform: (V) -> CallResult<T>): CallResult<T> {
        return when (this) {
            is Value<V> -> transform(this.value)
            is Error -> this
        }
    }
}
