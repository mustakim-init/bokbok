package com.mustakim.bokbok.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.mustakim.bokbok.data.api.GroqApi
import com.mustakim.bokbok.data.api.GroqChatRequest
import com.mustakim.bokbok.data.api.GroqContentPart
import com.mustakim.bokbok.data.api.GroqFunction
import com.mustakim.bokbok.data.api.GroqImageUrl
import com.mustakim.bokbok.data.api.GroqMessage
import com.mustakim.bokbok.data.api.GroqStreamChunk
import com.mustakim.bokbok.data.api.GroqTool
import com.mustakim.bokbok.data.api.GroqToolCall
import com.mustakim.bokbok.data.api.GroqToolFunction
import com.mustakim.bokbok.data.local.AIConversationDao
import com.mustakim.bokbok.data.local.PreferencesManager
import com.mustakim.bokbok.data.model.AIMessage
import com.mustakim.bokbok.data.model.MessageRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class AIRepository @Inject constructor(
    private val dao: AIConversationDao,
    private val deviceMonitor: DeviceMonitorRepository,
    private val preferencesManager: PreferencesManager,
    private val groqApi: GroqApi,
    @ApplicationContext private val context: Context
) {
    // Client-side rate limiting
    @Volatile private var lastRequestTime: Long = 0L
    private val minRequestIntervalMs = 1500L // 2 seconds (30 RPM = 1 per 2s)

    private val gson = Gson()
    
    private val deviceTools = listOf(
        GroqTool(
            function = GroqFunction(
                name = "get_device_hardware_specs",
                description = "Get detailed hardware specifications, SoC info, RAM, and raw system metadata for identification.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>(),
                    "required" to emptyList<String>()
                )
            )
        ),
        GroqTool(
            function = GroqFunction(
                name = "get_battery_diagnostics",
                description = "Get battery health, capacity, power draw, and raw battery system properties.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>(),
                    "required" to emptyList<String>()
                )
            )
        ),
        GroqTool(
            function = GroqFunction(
                name = "get_storage_usage",
                description = "Get storage capacity and current usage breakdown (Apps, Media, System).",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>(),
                    "required" to emptyList<String>()
                )
            )
        ),
        GroqTool(
            function = GroqFunction(
                name = "get_user_personal_facts",
                description = "Retrieve stored personal facts about the user.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>(),
                    "required" to emptyList<String>()
                )
            )
        ),
        GroqTool(
            function = GroqFunction(
                name = "set_user_personal_fact",
                description = "Store or update a personal fact about the user.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "key" to mapOf("type" to "string", "description" to "Unique identifier for the fact."),
                        "value" to mapOf("type" to "string", "description" to "Information to store.")
                    ),
                    "required" to listOf("key", "value")
                )
            )
        ),
        GroqTool(
            function = GroqFunction(
                name = "save_discovered_hardware",
                description = "Save the AI-interpreted hardware identity (Model and SoC) to local storage after successful identification from raw metadata.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "model_name" to mapOf("type" to "string", "description" to "User-friendly model name (e.g., 'iQOO Z9 Turbo')."),
                        "soc_name" to mapOf("type" to "string", "description" to "User-friendly SoC name (e.g., 'Snapdragon 8s Gen 3').")
                    ),
                    "required" to listOf("model_name", "soc_name")
                )
            )
        ),
        // OPEN_SETTINGS_SECTION REMOVED (Restricted to Observer)
        // CHECK_SCREEN_CONTEXT is allowed
        GroqTool(
            function = GroqFunction(
                name = "get_screen_context",
                description = "Get a semantic map of the current screen. WARNING: Setting include_screenshot=true is high-cost; use ONLY for visual/graphical details. Use hardware tools instead for system/spec queries.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "include_screenshot" to mapOf(
                            "type" to "boolean", 
                            "description" to "Whether to include a visual screenshot. Set to true ONLY if visual details (like colors or icons) are needed. False returns only text hierarchy."
                        )
                    ),
                    "required" to emptyList<String>()
                )
            )
        ),
        // UI_SWIPE REMOVED
        // UI_INTERACT REMOVED
        GroqTool(
            function = GroqFunction(
                name = "clear_hardware_cache",
                description = "Clear the stored/cached hardware identity if it is incorrect or a fresh scan is needed.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>(),
                    "required" to emptyList<String>()
                )
            )
        )
    )

    private suspend fun handleFunctionCall(call: com.mustakim.bokbok.data.api.GroqToolCall): Map<String, Any> {
        val name = call.function?.name ?: return mapOf("error" to "Missing function name")
        val args = try {
            val json = call.function?.arguments ?: "{}"
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            com.google.gson.Gson().fromJson<Map<String, Any>>(json, type) ?: emptyMap()
        } catch (e: Exception) {
            return mapOf("error" to "Failed to parse arguments: ${e.message}")
        }

        return try {
            when (name) {
                "get_device_hardware_specs" -> {
                    val cpu = deviceMonitor.getCpuInfo()
                    val ram = deviceMonitor.getRamInfo()
                    val rawProps = deviceMonitor.getRawSystemProps()
                    val cached = preferencesManager.deviceIdentity.first()
                    
                    val marketName = rawProps["ro.vivo.market.name"] ?: rawProps["ro.product.model"] ?: android.os.Build.MODEL
                    
                    val report = """
                        --- HARDWARE DIAGNOSTIC REPORT ---
                        MARKET NAME: $marketName
                        MODEL ID: ${rawProps["ro.product.model"] ?: android.os.Build.MODEL}
                        MANUFACTURER: ${rawProps["ro.product.manufacturer"] ?: android.os.Build.MANUFACTURER}
                        SoC (PROJECT): ${rawProps["ro.vivo.product.platform"] ?: "Unknown"}
                        SoC (MARKETING): ${cpu.socName ?: "Unknown"}
                        CORES: ${cpu.coreCount} (${cpu.architecture})
                        RAM (PHYSICAL): ${ram.totalMb} MB
                        RAM (MARKETING PROP): ${rawProps["sys.vivo.project.ramsize"] ?: "N/A"} GB
                        RAM (USED): ${ram.usedMb} MB
                        ----------------------------------
                        Note: Trust MARKET NAME and SoC (MARKETING) for identification.
                    """.trimIndent()

                    mapOf<String, Any>(
                        "structured_report" to report,
                        "raw_system_metadata" to rawProps,
                        "cached_identity" to mapOf<String, String?>("model" to cached.first, "soc" to cached.second)
                    )
                }
                "get_battery_diagnostics" -> {
                    val battery = deviceMonitor.getBatteryInfo()
                    val rawBattery = deviceMonitor.getRawBatteryProps()
                    
                    val report = """
                        --- BATTERY DIAGNOSTIC REPORT ---
                        HEALTH %: ${battery.healthPercent ?: "Unknown"}
                        CONDITION: ${battery.health}
                        DESIGN CAPACITY: ${battery.designCapacityMah ?: "Unknown"} mAh
                        CURRENT CAPACITY: ${battery.maxCapacityMah ?: "Unknown"} mAh
                        VOLTAGE: ${battery.voltageV}V
                        STATUS: ${if (battery.isCharging) "Charging" else "Discharging"}
                        CURRENT DRAW: ${battery.currentMa} mA
                        ---------------------------------
                        Note: If design capacity is 6400, it's the iQOO Z9 Turbo Lasting Edition.
                    """.trimIndent()

                    mapOf<String, Any>(
                        "structured_report" to report,
                        "raw_battery_properties" to rawBattery
                    )
                }
                "get_screen_context" -> {
                    val service = com.mustakim.bokbok.data.service.BokBokAgentService.getInstance()
                    if (service == null) {
                        com.mustakim.bokbok.util.ShizukuUtils.enableAccessibilityService()
                    }
                    val updatedService = com.mustakim.bokbok.data.service.BokBokAgentService.getInstance()
                    if (updatedService != null) {
                        val hierarchy = updatedService.dumpHierarchy()
                        val includeScreenshot = args["include_screenshot"] as? Boolean ?: false
                        
                        val screenshot = if (includeScreenshot && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            suspendCoroutine<String?> { continuation ->
                                updatedService.takeScreenshot { base64 ->
                                    continuation.resume(base64)
                                }
                            }
                        } else null
                        
                        mapOf<String, Any>(
                            "screen_hierarchy" to hierarchy,
                            "screenshot_base64" to (screenshot ?: "Not requested or unavailable.")
                        )
                    } else {
                        mapOf<String, Any>("error" to "Accessibility Service not active.")
                    }
                }
                // UI_SWIPE HANDLER REMOVED
                // OPEN_SETTINGS_SECTION HANDLER REMOVED
                // UI_INTERACT HANDLER REMOVED
                "get_storage_usage" -> {
                    val storage = deviceMonitor.getStorageInfo()
                    val breakdown = deviceMonitor.getStorageBreakdown()
                    mapOf<String, Any>(
                        "storage_info" to storage,
                        "breakdown" to breakdown
                    )
                }
                "get_user_personal_facts" -> {
                    mapOf<String, Any>("facts" to dao.getAllFacts().map { it.key to it.value })
                }
                "set_user_personal_fact" -> {
                    val key = args["key"]?.toString()
                    val value = args["value"]?.toString()
                    if (key != null && value != null) {
                        dao.insertFact(com.mustakim.bokbok.data.model.AIFact(key, value))
                        mapOf<String, Any>("status" to "success", "message" to "Fact stored: $key")
                    } else {
                        mapOf<String, Any>("error" to "Missing key or value")
                    }
                }
                "save_discovered_hardware" -> {
                    val model = args["model_name"]?.toString()
                    val soc = args["soc_name"]?.toString()
                    if (model != null && soc != null) {
                        preferencesManager.saveDeviceIdentity(model, soc)
                        mapOf<String, Any>("status" to "success", "message" to "Hardware identity cached: $model ($soc)")
                    } else {
                        mapOf<String, Any>("error" to "Missing model_name or soc_name")
                    }
                }
                "clear_hardware_cache" -> {
                    preferencesManager.saveDeviceIdentity("", "") // Clear
                    mapOf<String, Any>("status" to "success", "message" to "Hardware cache cleared.")
                }
                else -> mapOf<String, Any>("error" to "Unknown tool: $name")
            }
        } catch (e: Exception) {
            Log.e("AIRepository", "Error executing tool $name: ${e.message}", e)
            mapOf("error" to (e.message ?: "Unknown error occurred during tool execution"))
        }
    }

    private suspend fun getDynamicSystemInstruction(): String {
        val cached = preferencesManager.deviceIdentity.first()
        val facts = dao.getAllFacts()
        
        val identityStr = if (cached.first != null && cached.second != null) {
            "\nCACHED HARDWARE: You have already identified this device as a ${cached.first} with ${cached.second}. Use this info instead of querying unless the user asks for a fresh scan."
        } else ""
        
        val factsStr = if (facts.isNotEmpty()) {
            "\nUSER FACTS: ${facts.joinToString("; ") { "${it.key}: ${it.value}" }}"
        } else ""

        return """
            You are BokBok AI, a friendly and expert assistant for Android and gaming. 
            $identityStr
            $factsStr
            
            HOW YOU SEE AND ACT:
            - You can see what's on the user's screen. If the user asks "Can you see my screen?", just say "Yes, I can see what's on your screen. How can I help?".
            - DO NOT mention technical details like "hierarchies", "semantic maps", "screen context", "JSON", or "function calls". Never output curly braces or technical syntax in your response.
            - **OBSERVER ROLE**: You CANNOT navigate, toggle settings, or touch the screen. You are an OBSERVER only.
            - **GUIDANCE**: If the user needs to change a setting, GUIDE them verbally step-by-step (e.g., "Go to Settings > Display > Refresh Rate"). Trust them to do it.
            - **INTENT PRIVACY**: When using tools, describe your intent in simple, friendly terms (e.g., "Let me check your battery health..." or "Looking for that setting...") instead of revealing the technical tool name.
            - **SMART PERCEPTION**: AI perception (screenshots) is extremely expensive for the user quota. 
                - If the user asks general questions (lag, FPS, battery, specs), use the specific hardware tools. 
                - Do NOT take screenshots unless you absolutely cannot answer without seeing the visual layout or an image.
                - Prefer the textual 'screen_hierarchy' over visual 'screenshot_base64'.
            - **LANGUAGE ADAPTABILITY**: Detect the language of the user's input (English, Bangla, Hindi, etc.) and respond in the EXACT SAME language. Do not translate unless asked.
            
            TRUST POLICY:
            - Use your tools to get the latest info on battery, hardware, and screen elements.
            - Trust the tool results over your general training.
            
            Persona: Friendly, clever, and natural. Speak like a person, not a computer.
            Creator: Mustakim Ahmed.
        """.trimIndent()
    }

    fun sendMessageStream(
        prompt: String, 
        conversationId: String, 
        history: List<AIMessage> = emptyList(),
        imageUri: Uri? = null, 
        imageBitmap: Bitmap? = null
    ): Flow<String> = flow {
        // Rate Limiting
        val now = System.currentTimeMillis()
        val timeSinceLastRequest = now - lastRequestTime
        if (timeSinceLastRequest < minRequestIntervalMs) {
            delay(minRequestIntervalMs - timeSinceLastRequest)
        }
        lastRequestTime = System.currentTimeMillis()

        Log.d("AIRepository", "Starting sendMessageStream. Image: ${imageUri != null || imageBitmap != null}")

        // 1. Process Image if present
        var base64Image: String? = null
        val processedBitmap = when {
            imageBitmap != null -> compressBitmap(imageBitmap)
            imageUri != null -> loadBitmapFromUri(imageUri)?.let { compressBitmap(it) }
            else -> null
        }
        
        if (processedBitmap != null) {
            base64Image = encodeBitmapToBase64(processedBitmap)
        }

        // Build Initial Messages
        val messages = mutableListOf<GroqMessage>()
        messages.add(GroqMessage(role = "system", content = getDynamicSystemInstruction()))
        
        // Add History (Limit to last 6 for token efficiency)
        val relevantHistory = history
            .dropLastWhile { it.role == MessageRole.USER && it.content == prompt }
            .takeLast(6)
        
        messages.addAll(relevantHistory.map {
            // Strip massive base64 images from history content to save tokens
            val cleanContent = if (it.content.startsWith("data:image")) {
                "[Image]"
            } else {
                it.content
            }

            GroqMessage(
                role = when(it.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.TOOL -> "tool"
                    MessageRole.SYSTEM -> "system"
                },
                content = cleanContent,
                name = it.name,
                tool_call_id = it.toolCallId
            )
        })
        
        // Add Current User Message with possible Multi-modal content
        val userContent = if (base64Image != null) {
            listOf(
                GroqContentPart(type = "text", text = prompt),
                GroqContentPart(
                    type = "image_url", 
                    image_url = GroqImageUrl(url = "data:image/jpeg;base64,$base64Image")
                )
            )
        } else {
            prompt
        }
        messages.add(GroqMessage(role = "user", content = userContent))

        var currentAttempt = 0
        val maxAttempts = 3
        var keepLooping = true

        while (keepLooping && currentAttempt < maxAttempts) {
            try {
                // Determine model: use vision model if image is present in ANY message (usually just current)
                val isMultimodal = base64Image != null
                val activeModel = if (isMultimodal) "meta-llama/llama-4-scout-17b-16e-instruct" else "llama-3.3-70b-versatile"

                // Prepare Request
                val request = GroqChatRequest(
                    messages = messages,
                    model = activeModel,
                    tools = deviceTools, 
                    tool_choice = "auto"
                )

                // Execute Stream
                val response = groqApi.chatCompletionStream(request)

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    throw Exception("API Error ${response.code()}: $errorBody")
                }

                val source = response.body()?.source() ?: throw Exception("Empty response body")
                
                // Aggregators for tool calls (multiple parallel calls possible)
                val toolCallAggregators = mutableMapOf<Int, ToolCallBuilder>()
                var toolCallsPending = false
                var fullAssistantResponse = StringBuilder()

                // Process Streaming Response (SSE)
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue
                    if (!line.startsWith("data: ")) continue
                    
                    val json = line.removePrefix("data: ").trim()
                    if (json == "[DONE]") break

                    try {
                        val chunk = gson.fromJson(json, GroqStreamChunk::class.java)
                        val delta = chunk.choices.firstOrNull()?.delta ?: continue

                        // Handle Content
                        if (!delta.content.isNullOrEmpty()) {
                            emit(delta.content)
                            fullAssistantResponse.append(delta.content)
                        }

                        // Handle Tool Call Deltas
                        if (!delta.tool_calls.isNullOrEmpty()) {
                            toolCallsPending = true
                            delta.tool_calls.forEach { tcDelta ->
                                val index = tcDelta.index ?: 0 
                                val builder = toolCallAggregators.getOrPut(index) { ToolCallBuilder() }
                                
                                if (tcDelta.id != null) builder.id = tcDelta.id
                                if (tcDelta.function.name != null) builder.name = tcDelta.function.name
                                if (tcDelta.function.arguments != null) builder.arguments.append(tcDelta.function.arguments)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AIRepository", "Error parsing chunk: $json", e)
                    }
                }
                
                keepLooping = false 

                if (toolCallsPending) {
                    val assistantToolCalls = mutableListOf<GroqToolCall>()
                    
                    // Finalize Assistant Message with Tool Calls
                    toolCallAggregators.keys.sorted().forEach { key ->
                        val builder = toolCallAggregators[key]!!
                        if (builder.id != null && builder.name != null) {
                            assistantToolCalls.add(GroqToolCall(
                                id = builder.id,
                                function = GroqToolFunction(
                                    name = builder.name,
                                    arguments = builder.arguments.toString()
                                )
                            ))
                        }
                    }

                    if (assistantToolCalls.isNotEmpty()) {
                        messages.add(GroqMessage(
                            role = "assistant",
                            content = if (fullAssistantResponse.isEmpty()) null else fullAssistantResponse.toString(),
                            tool_calls = assistantToolCalls
                        ))

                        // Execute and Append Tool Results
                        assistantToolCalls.forEach { toolCall ->
                            Log.d("AIRepository", "Executing Tool: ${toolCall.function?.name}")
                            val result = handleFunctionCall(toolCall)
                            
                            // TOKEN PROTECTION: If we're not using a vision model, don't feed back base64 as text
                            val modelSupportsVision = activeModel.contains("scout") || activeModel.contains("vision")
                            val processedResult = if (!modelSupportsVision) {
                                result.filterKeys { it != "screenshot_base64" }
                            } else result

                            messages.add(GroqMessage(
                                role = "tool",
                                name = toolCall.function?.name,
                                tool_call_id = toolCall.id ?: "",
                                content = gson.toJson(processedResult)
                            ))
                        }
                        
                        keepLooping = true
                        delay(500)
                    }
                }

            } catch (e: Exception) {
                Log.e("AIRepository", "Stream loop error: ${e.message}")
                if (currentAttempt < maxAttempts - 1) {
                    currentAttempt++
                    val backoff = 1000L * (1L shl currentAttempt)
                    emit("\n(Connection issue, retrying in ${backoff/1000}s...)")
                    delay(backoff)
                } else {
                    emit("\nError: ${e.message}")
                    keepLooping = false
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private class ToolCallBuilder {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = java.io.ByteArrayOutputStream()
        // Compression quality 50 is enough for AI to see text/icons while saving tokens
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    }

    private suspend fun compressBitmap(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val maxWidth = 640 // Reduced from 1024 to save tokens (Groq limit is tight)
        val maxHeight = 640
        var width = bitmap.width
        var height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) return@withContext bitmap

        val ratio: Float = width.toFloat() / height.toFloat()
        if (width > height) {
            width = maxWidth
            height = (width / ratio).toInt()
        } else {
            height = maxHeight
            width = (height * ratio).toInt()
        }

        Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private suspend fun loadBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getConversationHistory(conversationId: String): Flow<List<AIMessage>> {
        return dao.getMessages(conversationId)
    }
    
    suspend fun insertMessage(message: AIMessage) {
        dao.insertMessage(message)
    }
    
    suspend fun clearMessagesForSession(conversationId: String) {
        dao.clearMessagesForSession(conversationId)
    }

    // Sessions
    fun getAllSessions(): Flow<List<com.mustakim.bokbok.data.model.AISession>> {
        return dao.getAllSessions()
    }

    suspend fun insertSession(session: com.mustakim.bokbok.data.model.AISession) {
        dao.insertSession(session)
    }

    suspend fun deleteSession(sessionId: String) {
        dao.deleteSession(sessionId)
    }

    suspend fun updateSessionTimestamp(sessionId: String, timestamp: Long) {
        dao.updateSessionTimestamp(sessionId, timestamp)
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) {
        dao.updateSessionTitle(sessionId, title)
    }

    suspend fun getMessageCount(sessionId: String): Int {
        return dao.getMessageCount(sessionId)
    }

    /**
     * Simplified entry point for Voice commands.
     * Handles history retrieval, pruning (Rate Limit Mitigation), and single-shot response.
     */
    suspend fun sendVoiceMessage(prompt: String): String {
        val sessionId = "voice_session"
        val history = getConversationHistory(sessionId).first()
        
        // Rate Limit Mitigation: Prune to last 6 messages
        val prunedHistory = history.takeLast(6)
        
        // Save User Message
        insertMessage(
            AIMessage(
                conversationId = sessionId,
                role = MessageRole.USER,
                content = prompt,
                timestamp = System.currentTimeMillis()
            )
        )

        // Reuse sendMessageStream logic but collect fully
        val responseBuilder = StringBuilder()
        sendMessageStream(prompt, sessionId, prunedHistory).collect { chunk ->
            responseBuilder.append(chunk)
        }
        
        val fullResponse = responseBuilder.toString()
        
        // Save Assistant Message
        insertMessage(
            AIMessage(
                conversationId = sessionId,
                role = MessageRole.ASSISTANT,
                content = fullResponse,
                timestamp = System.currentTimeMillis()
            )
        )
        
        return fullResponse
    }
}
