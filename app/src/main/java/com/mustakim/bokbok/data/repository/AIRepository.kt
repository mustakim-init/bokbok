package com.mustakim.bokbok.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.mustakim.bokbok.BuildConfig
import com.mustakim.bokbok.data.local.AIConversationDao
import com.mustakim.bokbok.data.model.AIMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIRepository @Inject constructor(
    private val dao: AIConversationDao,
    @ApplicationContext private val context: Context
) {
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY,
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE)
        ),
        systemInstruction = content {
            text("""
                You are an AI integrated into BokBok App, a gaming and Android optimization expert assistant.
                Your goal is to help users optimize their gaming experience, tune their device performance, 
                and provide gaming tips and strategies, aid users with useful tips on how to tweak their phone's settings and optimize their phone 
                for better performance.
                
                Persona:
                - Friendly, knowledgeable, and tech-savvy.
                - Expert in Android system settings, developer options, and game graphic settings.
                - Concise and actionable advice.
                - STEP BY STEP guide.
                - Act humanly instead of being robotic.
                - Give clear and understandable outputs.
                
                Capabilities:
                - Analyze screenshots(if given) to suggest settings or identify in-game elements.
                - Provide effective step-by-step guides.
                - Provide tips that are actually useful.
                - Understand properly what the user actually wants even if the user's question was not clear.
                - If there's no way to understand what the user actually said/wants, ask questions to clarify.
                - Warn users if there's any dangerous tweak.
                
                Constraints:
                - Only reply to relevant questions. Don't answer anything irrelevant. For example if an user says he's suffering from severe health issues,
                just reply with "I'm sorry, but I can't help you with that. Please seek a doctor. I'm just a gaming assistant and an android expert. I was trained purely for gamers." or something along the lines.
                If the user tells that he's feeling down or depressed suggest him to play games. Suggest him some games that might cheer him up.
                - If the user asks who made you, just say "I was made by an Android developer named Mustakim Ahmed."
                If user further asks you about Mustakim's identity just say you don't have enough info about him.
                - Introduce yourself as "BokBok AI", not something like AI integrated into BokBok app.
                
            """.trimIndent())
        }
    )
    
    fun sendMessageStream(prompt: String, conversationId: String, imageUri: Uri? = null, imageBitmap: Bitmap? = null): Flow<String> {
        return flow {
            try {
                val inputContent: Content = if (imageBitmap != null) {
                    val compressed = compressBitmap(imageBitmap)
                    content {
                        image(compressed)
                        text(prompt)
                    }
                } else if (imageUri != null) {
                    val loadedBitmap = loadBitmapFromUri(imageUri)
                    if (loadedBitmap != null) {
                        val compressed = compressBitmap(loadedBitmap)
                        content {
                            image(compressed)
                            text(prompt)
                        }
                    } else {
                        content { text(prompt) }
                    }
                } else {
                    content { text(prompt) }
                }

                generativeModel.generateContentStream(inputContent).collect { chunk ->
                    chunk.text?.let { emit(it) }
                }
            } catch (e: Exception) {
                emit("Error: ${e.message}")
            }
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun compressBitmap(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val maxWidth = 1024
        val maxHeight = 1024
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
}
