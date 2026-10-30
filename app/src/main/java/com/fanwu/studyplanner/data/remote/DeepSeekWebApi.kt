package com.fanwu.studyplanner.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/** 与对话共用 DeepSeek 凭据；凭据只放在请求头，不随网页转发。 */
interface DeepSeekWebApi {
    @POST("v1/tools/search")
    suspend fun search(@Header("Authorization") authorization: String, @Body request: WebSearchRequest): WebSearchResponse

    @POST("v1/tools/fetch")
    suspend fun fetch(@Header("Authorization") authorization: String, @Body request: WebFetchRequest): WebFetchResponse
}

@Serializable
data class WebSearchRequest(val query: String, val count: Int = 5, @SerialName("need_content") val needContent: Boolean = false)

@Serializable
data class WebSearchResponse(val results: List<WebSearchEntry>)

@Serializable
data class WebSearchEntry(val title: String, val url: String, val snippet: String = "", val summary: String = "", val date: String? = null)

@Serializable
data class WebFetchRequest(val url: String)

@Serializable
data class WebFetchResponse(val title: String, val content: String)
