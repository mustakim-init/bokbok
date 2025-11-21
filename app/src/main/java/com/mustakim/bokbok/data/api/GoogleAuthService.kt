package com.mustakim.bokbok.data.api

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface GoogleAuthService {
    @FormUrlEncoded
    @POST("token")
    suspend fun getAccessToken(
        @Field("grant_type") grantType: String = "urn:ietf:params:oauth:grant-type:jwt-bearer",
        @Field("assertion") jwt: String
    ): GoogleAccessTokenResponse
}

data class GoogleAccessTokenResponse(
    val access_token: String,
    val expires_in: Int,
    val token_type: String
)
