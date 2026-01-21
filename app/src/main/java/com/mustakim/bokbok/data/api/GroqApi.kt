package com.mustakim.bokbok.data.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface GroqApi {
    @POST("openai/v1/chat/completions")
    @Streaming
    suspend fun chatCompletionStream(
        @Body request: GroqChatRequest
    ): Response<ResponseBody>

    @Multipart
    @POST("openai/v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Part file: MultipartBody.Part,
        @Part("model") model: okhttp3.RequestBody = "whisper-large-v3".toRequestBody("text/plain".toMediaType()),
        @Part("response_format") responseFormat: okhttp3.RequestBody = "json".toRequestBody("text/plain".toMediaType()),
        @Part("language") language: okhttp3.RequestBody? = null
    ): Response<GroqTranscriptionResponse>
}

// Data Classes for Request/Response

data class GroqChatRequest(
    val messages: List<GroqMessage>,
    val model: String = "llama-3.3-70b-versatile",
    val tools: List<GroqTool>? = null,
    val tool_choice: Any? = null, // "auto", "none", or specific tool
    val stream: Boolean = true,
    val temperature: Double? = 0.7,
    val max_tokens: Int? = null
)

data class GroqMessage(
    val role: String, // "system", "user", "assistant", "tool"
    val content: Any?, // String or List<GroqContentPart>
    val name: String? = null, // Required for role="tool"
    val tool_calls: List<GroqToolCall>? = null,
    val tool_call_id: String? = null // Required for role="tool"
)

data class GroqContentPart(
    val type: String, // "text" or "image_url"
    val text: String? = null,
    val image_url: GroqImageUrl? = null
)

data class GroqImageUrl(
    val url: String // data:image/jpeg;base64,{base64_image}
)

data class GroqTool(
    val type: String = "function",
    val function: GroqFunction
)

data class GroqFunction(
    val name: String,
    val description: String,
    val parameters: Map<String, Any> // JSON Schema
)

// Response structures

data class GroqChatResponse(
    val id: String,
    val choices: List<GroqChoice>,
    val usage: GroqUsage? = null
)

data class GroqChoice(
    val index: Int,
    val message: GroqMessage,
    val finish_reason: String?
)

data class GroqUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

// Tool Call structures within a message
data class GroqToolCall(
    val index: Int? = null,
    val id: String? = null,
    val type: String? = "function",
    val function: GroqToolFunction
)

data class GroqToolFunction(
    val name: String? = null,
    val arguments: String? = null // JSON string
)

// Streaming Chunk Structure (Partial response)
data class GroqStreamChunk(
    val id: String,
    val choices: List<GroqStreamChoice>
)

data class GroqStreamChoice(
    val index: Int,
    val delta: GroqDelta,
    val finish_reason: String?
)

data class GroqDelta(
    val role: String? = null,
    val content: String? = null,
    val tool_calls: List<GroqToolCall>? = null
)

data class GroqTranscriptionResponse(
    val text: String
)
