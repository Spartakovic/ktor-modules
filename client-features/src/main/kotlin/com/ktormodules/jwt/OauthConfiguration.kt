package com.ktormodules.jwt

data class OauthConfiguration(
        val oauhtUrl: String,
        val oauthClientId: String,
        val oauthClientSecret: String,
        val oauthGrantType: String,
        val oauthScope: String
)