package com.mustakim.bokbok.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface VirusTotalApi {

    @GET("files/{hash}")
    suspend fun getFileReport(
        @Header("x-apikey") apiKey: String,
        @Path("hash") hash: String
    ): Response<VirusTotalResponse>
}

data class VirusTotalResponse(
    @SerializedName("data") val data: VirusTotalData?
)

data class VirusTotalData(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("attributes") val attributes: VirusTotalAttributes
)

data class VirusTotalAttributes(
    @SerializedName("last_analysis_stats") val lastAnalysisStats: AnalysisStats,
    @SerializedName("last_analysis_results") val lastAnalysisResults: Map<String, EngineResult>?,
    @SerializedName("meaningful_name") val meaningfulName: String?,
    @SerializedName("type_description") val typeDescription: String?
)

data class EngineResult(
    @SerializedName("method") val method: String?,
    @SerializedName("engine_name") val engineName: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("result") val result: String?
)

data class AnalysisStats(
    @SerializedName("malicious") val malicious: Int,
    @SerializedName("suspicious") val suspicious: Int,
    @SerializedName("undetected") val undetected: Int,
    @SerializedName("harmless") val harmless: Int,
    @SerializedName("timeout") val timeout: Int,
    @SerializedName("confirmed-timeout") val confirmedTimeout: Int,
    @SerializedName("failure") val failure: Int,
    @SerializedName("type-unsupported") val typeUnsupported: Int
)
