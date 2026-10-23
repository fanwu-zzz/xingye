package com.fanwu.studyplanner.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * DeepSeek API 客户端（OpenAI 兼容接口）
 */
object DeepSeekClient {

    private const val BASE_URL = "https://api.deepseek.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        // 必须编码默认值，否则 model / stream 等字段会被省略，接口返回 422
        encodeDefaults = true
    }

    val api: DeepSeekApi by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DeepSeekApi::class.java)
    }
}
