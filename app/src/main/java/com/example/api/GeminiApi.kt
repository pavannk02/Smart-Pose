package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini REST Data Classes ---

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<Content>,
    val tools: List<Tool>? = null,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class Tool(
    @Json(name = "googleSearch") val googleSearch: Map<String, String>? = null,
    @Json(name = "googleMaps") val googleMaps: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?,
    val groundingMetadata: GroundingMetadata? = null
)

@JsonClass(generateAdapter = true)
data class GroundingMetadata(
    val webSearchQueries: List<String>? = null,
    val searchEntryPoint: SearchEntryPoint? = null,
    val groundingChunks: List<GroundingChunk>? = null
)

@JsonClass(generateAdapter = true)
data class SearchEntryPoint(
    val renderedContent: String? = null
)

@JsonClass(generateAdapter = true)
data class GroundingChunk(
    val web: GroundingWeb? = null
)

@JsonClass(generateAdapter = true)
data class GroundingWeb(
    val uri: String? = null,
    val title: String? = null
)

// --- Client Predefined Pose Response Structure ---

@JsonClass(generateAdapter = true)
data class PoseResponse(
    val status: String,
    val analysis: PoseAnalysis,
    @Json(name = "recommended_pose") val recommendedPose: RecommendedPose,
    @Json(name = "place_info") val placeInfo: PlaceInfo? = null,
    val groundingQueries: List<String>? = null,
    val groundingSources: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class PlaceInfo(
    @Json(name = "place_name") val placeName: String? = null,
    @Json(name = "location_tips") val locationTips: String? = null,
    @Json(name = "best_photo_spot") val bestPhotoSpot: String? = null
)

@JsonClass(generateAdapter = true)
data class PoseAnalysis(
    @Json(name = "detected_environment") val detectedEnvironment: String,
    @Json(name = "detected_outfit") val detectedOutfit: String
)

@JsonClass(generateAdapter = true)
data class RecommendedPose(
    @Json(name = "pose_name") val poseName: String,
    @Json(name = "wireframe_url") val wireframeUrl: String,
    @Json(name = "photographer_tips") val photographerTips: String,
    // Style identifier to match custom vector wireframes
    @Json(name = "wireframe_style") val wireframeStyle: String? = "classic"
)

// --- Retrofit & Moshi Network Clients ---

interface GeminiApiService {
    // Upgraded to gemini-3.8-flash with Google Maps and Google Search Grounding capabilities
    @POST("v1beta/models/gemini-3.8-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val service: GeminiApiService by lazy {
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        retrofit.create(GeminiApiService::class.java)
    }
}
